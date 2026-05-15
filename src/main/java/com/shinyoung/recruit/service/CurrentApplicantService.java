package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentApplicantService {

    private final ApplicantRepository applicantRepository;

    public Long getCurrentApplicantId(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new InvalidJobApplicationException("Applicant authentication is required.");
        }
        if (!CustomUserDetails.USER_TYPE_APPLICANT.equals(userDetails.getUserType())) {
            throw new InvalidJobApplicationException("Only applicant users can access application APIs.");
        }

        return applicantRepository.findByLoginId(userDetails.getUsername())
                .map(Applicant::getId)
                .orElseThrow(() -> new InvalidJobApplicationException("Applicant user was not found."));
    }
}
