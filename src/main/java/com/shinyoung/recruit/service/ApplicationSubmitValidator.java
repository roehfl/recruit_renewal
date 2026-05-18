package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationSubmitValidator {

    private final ApplicationEducationRepository educationRepository;
    private final ApplicationCareerProfileRepository careerProfileRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationMilitaryRepository militaryRepository;

    public void validate(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null) {
            throw new InvalidJobApplicationException("Application form config is required before submit.");
        }

        Long applicationId = application.getId();
        validateEducation(config, applicationId);
        validateCareer(config, applicationId);
        validateMilitary(config, applicationId);
    }

    private void validateEducation(ApplicationFormConfig config, Long applicationId) {
        if (!config.isUseEducation()) {
            return;
        }
        if (!educationRepository.existsByJobApplicationId(applicationId)) {
            throw new InvalidJobApplicationException("Education section is required before submit.");
        }
    }

    private void validateCareer(ApplicationFormConfig config, Long applicationId) {
        if (!config.isUseCareer()) {
            return;
        }

        ApplicationCareerProfile profile = careerProfileRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("Career profile is required before submit."));
        CareerType careerType = profile.getCareerType();
        if (careerType == null || careerType == CareerType.NOT_SELECTED) {
            throw new InvalidJobApplicationException("Career type must be selected before submit.");
        }

        boolean hasCareerRows = careerRepository.existsByJobApplicationId(applicationId);
        if (careerType == CareerType.EXPERIENCED && !hasCareerRows) {
            throw new InvalidJobApplicationException("Career rows are required for experienced applicants before submit.");
        }
        if ((careerType == CareerType.NEWCOMER || careerType == CareerType.NOT_APPLICABLE) && hasCareerRows) {
            throw new InvalidJobApplicationException("Career rows are not allowed for the selected career type before submit.");
        }
    }

    private void validateMilitary(ApplicationFormConfig config, Long applicationId) {
        if (!config.isUseMilitary()) {
            return;
        }

        ApplicationMilitary military = militaryRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("Military section is required before submit."));
        MilitarySubjectType subjectType = military.getMilitarySubjectType();
        if (subjectType == null) {
            throw new InvalidJobApplicationException("Military subject type is required before submit.");
        }

        if (subjectType == MilitarySubjectType.COMPLETED
                && (military.getServiceStartDate() == null || military.getServiceEndDate() == null)) {
            throw new InvalidJobApplicationException("Military service period is required for completed applicants before submit.");
        }
        if (subjectType == MilitarySubjectType.EXEMPTED
                && (military.getExemptionReason() == null || military.getExemptionReason().isBlank())) {
            throw new InvalidJobApplicationException("Military exemption reason is required before submit.");
        }
    }
}
