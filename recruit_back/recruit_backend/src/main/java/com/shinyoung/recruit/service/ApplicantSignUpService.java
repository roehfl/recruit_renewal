package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.exception.InvalidApplicantSignUpException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantSignUpService {

    private final ApplicantRepository applicantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditHmac auditHmac;
    private final EmailVerificationService emailVerificationService;

    public ApplicantSignUpService(ApplicantRepository applicantRepository, UserRepository userRepository, PasswordEncoder passwordEncoder, AuditHmac auditHmac,
                                  EmailVerificationService emailVerificationService) {
        this.applicantRepository = applicantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditHmac = auditHmac;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public ApplicantSignUpResponse signUp(ApplicantSignUpRequest request, NiceVerifiedIdentity identity) {
        String loginId = request.loginId().trim();
        String name = identity.name().trim();
        String phoneNumber = identity.phoneNumber().trim();
        String email = normalizeEmail(request.email());

        // 이메일 인증은 email 만 확인한다. 아이디가 다르면 남의 이메일을 아이디로 가입할 수 있다.
        if (email == null || !loginId.equalsIgnoreCase(email)) {
            throw new InvalidApplicantSignUpException("아이디는 인증한 이메일과 같아야 합니다.");
        }

        // 로그인 해석(findUserByLoginId)이 users 테이블 전체에서 일어나므로 중복체크도 User 레벨로 수행한다.
        // (Applicant 레벨만 체크하면 임직원(LDAP JIT) loginId와 충돌해 양쪽 로그인 장애가 된다.)
        if (userRepository.existsByLoginId(loginId)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 아이디입니다.");
        }

        if (email != null && applicantRepository.existsByEmail(email)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 이메일입니다.");
        }

        // 중복 판정 키 = 이름+생년월일+성별의 HMAC(NICE 계약에 CI 가 없어 CI 대신 쓴다).
        // 휴대폰은 넣지 않는다 — 번호만 바꿔 중복 가입하는 것을 막는다.
        String identityKey = auditHmac.identityHash(identity.name(), identity.birthDate(), identity.gender());
        if (applicantRepository.existsByCiHash(identityKey)) {
            throw new InvalidApplicantSignUpException("이미 가입된 본인인증 정보입니다.");
        }

        Applicant applicant = new Applicant(identityKey);
        applicant.setLoginId(loginId);
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setPassword(passwordEncoder.encode(request.password()));
        applicant.setPhoneNumber(phoneNumber);
        applicant.setEmail(email);

        applicantRepository.save(applicant);

        return ApplicantSignUpResponse.from(applicant);
    }

    /**
     * 가입 이메일 인증번호를 보내고 세션에 둘 상태를 돌려준다. 이미 가입된 이메일이면 보내지 않는다.
     *
     * <p>트랜잭션을 걸지 않는다 — 발송 이력은 SystemMailService 가 먼저 커밋한 뒤 게이트웨이를 부른다.
     * 가입 인증 메일에는 이름이 없다(아직 본인확인 전일 수 있다).
     */
    public EmailVerificationState sendEmailVerification(EmailVerificationState previous, String email) {
        String normalized = normalizeEmail(email);
        if (applicantRepository.existsByEmail(normalized)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 이메일입니다.");
        }
        return emailVerificationService.send(previous, EmailVerificationPurpose.SIGNUP, normalized, "");
    }

    /**
     * 가입 화면용 advisory 이메일 가용성 판정. signUp과 동일한 정규화(trim)를 거쳐 판정하며,
     * 최종 권위는 signUp 시점 재검증 + Applicant.email DB unique 제약이다.
     */
    @Transactional(readOnly = true)
    public ApplicantEmailAvailabilityResponse checkEmailAvailability(String email) {
        String normalized = normalizeEmail(email);
        boolean available = normalized != null && !applicantRepository.existsByEmail(normalized);
        return new ApplicantEmailAvailabilityResponse(available);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
