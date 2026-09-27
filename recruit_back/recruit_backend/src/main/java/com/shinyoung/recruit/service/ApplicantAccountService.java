package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.dto.request.ApplicantPasswordChangeRequest;
import com.shinyoung.recruit.dto.request.ApplicantPhoneNumberChangeRequest;
import com.shinyoung.recruit.exception.InvalidApplicantAccountException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantAccountService {

    private final ApplicantRepository applicantRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionRevoker userSessionRevoker;
    private final AuditHmac auditHmac;

    public ApplicantAccountService(ApplicantRepository applicantRepository, PasswordEncoder passwordEncoder,
                                   UserSessionRevoker userSessionRevoker, AuditHmac auditHmac) {
        this.applicantRepository = applicantRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSessionRevoker = userSessionRevoker;
        this.auditHmac = auditHmac;
    }

    /** 바꾸면 이 계정의 다른 로그인 세션을 만료한다(지금 세션은 유지). 탈취된 세션이 살아 있지 않게 한다. */
    @Transactional
    public void changePassword(Long applicantId, ApplicantPasswordChangeRequest request, String currentSessionId) {
        Applicant applicant = findApplicant(applicantId);
        verifyCurrentPassword(request.currentPassword(), applicant);

        if (passwordEncoder.matches(request.newPassword(), applicant.getPassword())) {
            throw new InvalidApplicantAccountException("새 비밀번호가 현재 비밀번호와 달라야 합니다.");
        }

        applicant.changePassword(passwordEncoder.encode(request.newPassword()));
        userSessionRevoker.expireSessions(applicant.getLoginId(), currentSessionId);
    }

    /**
     * 새 번호는 NICE 본인확인 결과의 번호다(용도 {@code PHONE_CHANGE}, 호출자가 세션에서 꺼내 확인해 넘긴다).
     * 인증 명의(이름+생년월일+성별)가 이 계정의 가입자와 같아야 한다 — 남의 명의 번호로 바꾸지 못한다.
     */
    @Transactional
    public void changePhoneNumber(Long applicantId, ApplicantPhoneNumberChangeRequest request, NiceVerifiedIdentity identity) {
        Applicant applicant = findApplicant(applicantId);
        verifyCurrentPassword(request.currentPassword(), applicant);

        String identityKey = auditHmac.identityHash(identity.name(), identity.birthDate(), identity.gender());
        if (!identityKey.equals(applicant.getCiHash())) {
            throw new InvalidApplicantAccountException("본인인증 명의가 가입자 정보와 일치하지 않습니다.");
        }
        applicant.changePhoneNumber(identity.phoneNumber().trim());
    }

    private Applicant findApplicant(Long applicantId) {
        return applicantRepository.findById(applicantId)
                .orElseThrow(() -> new InvalidApplicantAccountException("지원자 정보를 찾을 수 없습니다."));
    }

    private void verifyCurrentPassword(String currentPassword, Applicant applicant) {
        if (!passwordEncoder.matches(currentPassword, applicant.getPassword())) {
            throw new InvalidApplicantAccountException("현재 비밀번호가 일치하지 않습니다.");
        }
    }
}
