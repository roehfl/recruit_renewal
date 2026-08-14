package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.dto.request.ApplicantPasswordChangeRequest;
import com.shinyoung.recruit.dto.request.ApplicantPhoneNumberChangeRequest;
import com.shinyoung.recruit.exception.InvalidApplicantAccountException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantAccountService {

    private final ApplicantRepository applicantRepository;
    private final PasswordEncoder passwordEncoder;

    public ApplicantAccountService(ApplicantRepository applicantRepository, PasswordEncoder passwordEncoder) {
        this.applicantRepository = applicantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(Long applicantId, ApplicantPasswordChangeRequest request) {
        Applicant applicant = findApplicant(applicantId);
        verifyCurrentPassword(request.currentPassword(), applicant);

        if (passwordEncoder.matches(request.newPassword(), applicant.getPassword())) {
            throw new InvalidApplicantAccountException("새 비밀번호가 현재 비밀번호와 달라야 합니다.");
        }

        applicant.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void changePhoneNumber(Long applicantId, ApplicantPhoneNumberChangeRequest request) {
        Applicant applicant = findApplicant(applicantId);
        verifyCurrentPassword(request.currentPassword(), applicant);

        applicant.changePhoneNumber(request.phoneNumber().trim());
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
