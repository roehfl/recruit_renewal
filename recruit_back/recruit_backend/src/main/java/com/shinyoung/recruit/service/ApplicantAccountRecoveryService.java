package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.dto.response.ApplicantFindEmailResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 전 계정 복구: 아이디(이메일) 찾기, 비밀번호 재설정.
 *
 * <p>세션을 직접 만지지 않는다. 컨트롤러가 세션의 NICE 인증 결과·이메일 인증 상태를 검사·소비한 뒤 값으로 넘긴다
 * — 서비스가 {@code HttpSession} 을 알면 단위 테스트가 서블릿 컨테이너에 묶인다.
 */
@Service
public class ApplicantAccountRecoveryService {

    private static final String NOT_FOUND_MESSAGE = "본인인증 정보와 일치하는 계정이 없습니다.";
    private static final String EMAIL_NOT_FOUND_MESSAGE = "가입된 이메일이 아닙니다.";

    private final ApplicantRepository applicantRepository;
    private final AuditHmac auditHmac;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    public ApplicantAccountRecoveryService(ApplicantRepository applicantRepository, AuditHmac auditHmac,
                                           EmailVerificationService emailVerificationService,
                                           PasswordEncoder passwordEncoder) {
        this.applicantRepository = applicantRepository;
        this.auditHmac = auditHmac;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 본인확인한 사람의 아이디를 부분 마스킹해 돌려준다.
     *
     * <p>가입과 같은 식별 키(이름+생년월일+성별 HMAC)로 찾는다. {@code ciHash} 가 unique 라 계정은 많아야 하나다.
     * 파기된 계정은 {@code ciHash} 가 {@code PURGED:} sentinel 로 덮여 있어 찾아지지 않는다.
     */
    @Transactional(readOnly = true)
    public ApplicantFindEmailResponse findEmail(NiceVerifiedIdentity identity) {
        String identityKey = auditHmac.identityHash(identity.name(), identity.birthDate(), identity.gender());
        String loginId = applicantRepository.findByCiHash(identityKey)
                .map(Applicant::getLoginId)
                .orElseThrow(() -> new ApplicantNotFoundException(NOT_FOUND_MESSAGE));
        return new ApplicantFindEmailResponse(maskLoginId(loginId));
    }

    /**
     * 가입 이메일로 비밀번호 재설정 인증번호를 보내고 세션에 둘 상태를 돌려준다. 가입된 지원자가 없으면 404
     * (계정 열거 감수 — 기존 결정). 트랜잭션을 걸지 않는다 — 발송 이력은 SystemMailService 가 먼저 커밋한다.
     */
    public EmailVerificationState sendPasswordResetCode(EmailVerificationState previous, String email) {
        Applicant applicant = findByEmail(email);
        return emailVerificationService.send(previous, EmailVerificationPurpose.PASSWORD_RESET,
                applicant.getEmail(), applicant.getName());
    }

    /** 인증을 마친 이메일의 비밀번호를 바꾼다(BCrypt). 다른 로그인 세션은 건드리지 않는다. */
    @Transactional
    public void resetPassword(String email, String newPassword) {
        findByEmail(email).changePassword(passwordEncoder.encode(newPassword));
    }

    private Applicant findByEmail(String email) {
        return applicantRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ApplicantNotFoundException(EMAIL_NOT_FOUND_MESSAGE));
    }

    /**
     * 아이디 부분 마스킹. 로컬부({@code @} 앞)는 3자 이상이면 앞 2자, 2자 이하면 앞 1자만 남기고 나머지를
     * 같은 길이의 {@code *} 로 가린다(가린 글자가 0개면 {@code *} 1개). 도메인은 그대로 둔다.
     * {@code @} 가 없으면(형식 검증 없이 만든 계정) 전체를 로컬부로 본다.
     *
     * <p>{@code MessageContacts.maskEmail} 을 쓰지 않는다 — 그건 로그용이라 형식이 다르다(첫 글자+{@code ***}).
     */
    static String maskLoginId(String loginId) {
        int at = loginId.indexOf('@');
        String local = at < 0 ? loginId : loginId.substring(0, at);
        String domain = at < 0 ? "" : loginId.substring(at);
        int keep = Math.min(local.length() >= 3 ? 2 : 1, local.length());
        int hidden = Math.max(local.length() - keep, 1);
        return local.substring(0, keep) + "*".repeat(hidden) + domain;
    }
}
