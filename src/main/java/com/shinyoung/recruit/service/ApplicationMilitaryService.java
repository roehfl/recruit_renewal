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

    private static final int NON_SERVICE_REASON_MAX_LENGTH = 1000;

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
                request.nonServiceReason()
        );

        return MilitaryResponse.from(military);
    }

    private void validateRequest(MilitarySaveRequest request) {
        if (request == null || request.militarySubjectType() == null) {
            throw new InvalidJobApplicationException("Military subject type is required.");
        }
        if (request.nonServiceReason() != null && request.nonServiceReason().length() > NON_SERVICE_REASON_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Non-service reason must be 1000 characters or less.");
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

        // 비대상: 복무 상세도 사유도 입력할 수 없다.
        if (subjectType == MilitarySubjectType.NOT_SUBJECT) {
            validateNoDetailFields(request);
            return;
        }

        // 미필(SUBJECT)·면제(EXEMPTED): 사유는 허용하되 복무 상세는 금지한다.
        if (subjectType == MilitarySubjectType.SUBJECT || subjectType == MilitarySubjectType.EXEMPTED) {
            if (hasServiceDetailFields(request)) {
                throw new InvalidJobApplicationException("Service detail fields are not allowed for this military subject type.");
            }
            return;
        }

        // 군필(COMPLETED): 복무 상세는 허용하되 사유는 금지한다.
        if (subjectType == MilitarySubjectType.COMPLETED && request.nonServiceReason() != null) {
            throw new InvalidJobApplicationException("Non-service reason is allowed only for not-yet-served or exempted military status.");
        }
    }

    private void validateNoDetailFields(MilitarySaveRequest request) {
        if (hasServiceDetailFields(request) || request.nonServiceReason() != null) {
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
                request.nonServiceReason()
        );
    }
}
