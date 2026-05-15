package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.dto.request.MilitarySaveRequest;
import com.shinyoung.recruit.dto.response.MilitaryResponse;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationMilitaryService {

    private static final int EXEMPTION_REASON_MAX_LENGTH = 1000;

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationMilitaryRepository militaryRepository;

    @Transactional(readOnly = true)
    public MilitaryResponse getMilitary(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return militaryRepository.findByJobApplicationId(applicationId)
                .map(MilitaryResponse::from)
                .orElse(null);
    }

    @Transactional
    public MilitaryResponse saveMilitary(Long applicantId, Long applicationId, MilitarySaveRequest request) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        sectionAccessService.validateMilitaryEnabled(application);
        validateRequest(request);

        ApplicationMilitary military = militaryRepository.findByJobApplicationId(applicationId)
                .orElseGet(() -> militaryRepository.save(toMilitary(application, request)));

        military.update(
                request.militarySubjectType(),
                request.serviceType(),
                request.militaryBranch(),
                request.rank(),
                request.serviceStartDate(),
                request.serviceEndDate(),
                request.exemptionReason()
        );

        return MilitaryResponse.from(military);
    }

    private void validateRequest(MilitarySaveRequest request) {
        if (request == null || request.militarySubjectType() == null) {
            throw new InvalidJobApplicationException("Military subject type is required.");
        }
        if (request.exemptionReason() != null && request.exemptionReason().length() > EXEMPTION_REASON_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Exemption reason must be 1000 characters or less.");
        }
        if (request.serviceStartDate() != null
                && request.serviceEndDate() != null
                && request.serviceStartDate().isAfter(request.serviceEndDate())) {
            throw new InvalidJobApplicationException("Service start date cannot be after service end date.");
        }

        validateFieldsBySubjectType(request);
    }

    private void validateFieldsBySubjectType(MilitarySaveRequest request) {
        MilitarySubjectType subjectType = request.militarySubjectType();
        if (subjectType == MilitarySubjectType.SUBJECT
                || subjectType == MilitarySubjectType.NOT_SUBJECT
                || subjectType == MilitarySubjectType.NOT_APPLICABLE) {
            validateNoDetailFields(request);
            return;
        }

        if (subjectType == MilitarySubjectType.COMPLETED && request.exemptionReason() != null) {
            throw new InvalidJobApplicationException("Exemption reason is allowed only for exempted military status.");
        }

        if (subjectType == MilitarySubjectType.EXEMPTED && hasServiceDetailFields(request)) {
            throw new InvalidJobApplicationException("Service detail fields are not allowed for exempted military status.");
        }
    }

    private void validateNoDetailFields(MilitarySaveRequest request) {
        if (hasServiceDetailFields(request) || request.exemptionReason() != null) {
            throw new InvalidJobApplicationException("Military detail fields are not allowed for this military subject type.");
        }
    }

    private boolean hasServiceDetailFields(MilitarySaveRequest request) {
        return request.serviceType() != null
                || request.militaryBranch() != null
                || request.rank() != null
                || request.serviceStartDate() != null
                || request.serviceEndDate() != null;
    }

    private ApplicationMilitary toMilitary(JobApplication application, MilitarySaveRequest request) {
        return ApplicationMilitary.create(
                application,
                request.militarySubjectType(),
                request.serviceType(),
                request.militaryBranch(),
                request.rank(),
                request.serviceStartDate(),
                request.serviceEndDate(),
                request.exemptionReason()
        );
    }
}
