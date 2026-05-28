package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.exception.InvalidApplicantSignUpException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantSignUpService {

    private final ApplicantRepository applicantRepository;
    private final PasswordEncoder passwordEncoder;

    public ApplicantSignUpService(ApplicantRepository applicantRepository, PasswordEncoder passwordEncoder) {
        this.applicantRepository = applicantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ApplicantSignUpResponse signUp(ApplicantSignUpRequest request) {
        String loginId = request.loginId().trim();
        String name = request.name().trim();
        String phoneNumber = request.phoneNumber().trim();
        String email = normalizeEmail(request.email());
        String ci = request.ci().trim();

        if (applicantRepository.existsByLoginId(loginId)) {
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
