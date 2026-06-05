package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.exception.InvalidApplicantSignUpException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantSignUpService {

    private final ApplicantRepository applicantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ApplicantSignUpService(ApplicantRepository applicantRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.applicantRepository = applicantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ApplicantSignUpResponse signUp(ApplicantSignUpRequest request) {
        String loginId = request.loginId().trim();
        String name = request.name().trim();
        String phoneNumber = request.phoneNumber().trim();
        String email = normalizeEmail(request.email());
        String ci = request.ci().trim();

        // 로그인 해석(findUserByLoginId)이 users 테이블 전체에서 일어나므로 중복체크도 User 레벨로 수행한다.
        // (Applicant 레벨만 체크하면 임직원(LDAP JIT) loginId와 충돌해 양쪽 로그인 장애가 된다.)
        if (userRepository.existsByLoginId(loginId)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 아이디입니다.");
        }

        if (email != null && applicantRepository.existsByEmail(email)) {
            throw new InvalidApplicantSignUpException("이미 사용 중인 이메일입니다.");
        }

        String ciHash = HashUtil.sha256(ci);
        if (applicantRepository.existsByCiHash(ciHash)) {
            throw new InvalidApplicantSignUpException("이미 가입된 본인인증 정보입니다.");
        }

        Applicant applicant = new Applicant(ci, ciHash);
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
