package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.dto.request.GapPeriodReplaceRequest;
import com.shinyoung.recruit.dto.request.GapPeriodRequest;
import com.shinyoung.recruit.dto.response.GapPeriodResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationGapPeriodService {

    private static final int DESCRIPTION_MAX_LENGTH = 2000;

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationGapPeriodRepository gapPeriodRepository;

    @Transactional(readOnly = true)
    public List<GapPeriodResponse> getGapPeriods(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return getGapPeriodResponses(applicationId);
    }

    @Transactional
    public List<GapPeriodResponse> replaceGapPeriods(
            Long applicantId,
            Long applicationId,
            GapPeriodReplaceRequest request
    ) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        sectionAccessService.validateGapPeriodEnabled(application);
        validateRequest(request);

        gapPeriodRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationGapPeriod> gapPeriods = request.gapPeriods().stream()
                .map(gapPeriod -> toGapPeriod(application, gapPeriod))
                .toList();
        gapPeriodRepository.saveAll(gapPeriods);

        return getGapPeriodResponses(applicationId);
    }

    private void validateRequest(GapPeriodReplaceRequest request) {
        if (request == null || request.gapPeriods() == null) {
            throw new InvalidJobApplicationException("Gap period list is required.");
        }

        Set<Integer> sortOrders = new HashSet<>();
        for (GapPeriodRequest gapPeriod : request.gapPeriods()) {
            validateGapPeriodRequiredFields(gapPeriod);
            if (!sortOrders.add(gapPeriod.sortOrder())) {
                throw new InvalidJobApplicationException("Gap period sort order must be unique.");
            }
        }
    }

    private void validateGapPeriodRequiredFields(GapPeriodRequest gapPeriod) {
        if (gapPeriod == null) {
            throw new InvalidJobApplicationException("Gap period item is required.");
        }
        if (gapPeriod.startDate() == null) {
            throw new InvalidJobApplicationException("Start date is required.");
        }
        if (gapPeriod.endDate() == null) {
            throw new InvalidJobApplicationException("End date is required.");
        }
        if (gapPeriod.gapType() == null) {
            throw new InvalidJobApplicationException("Gap type is required.");
        }
        if (gapPeriod.reason() == null || gapPeriod.reason().isBlank()) {
            throw new InvalidJobApplicationException("Reason is required.");
        }
        if (gapPeriod.sortOrder() == null || gapPeriod.sortOrder() < 0) {
            throw new InvalidJobApplicationException("Sort order must be greater than or equal to 0.");
        }
        if (gapPeriod.startDate().isAfter(gapPeriod.endDate())) {
            throw new InvalidJobApplicationException("Start date cannot be after end date.");
        }
        if (gapPeriod.description() != null && gapPeriod.description().length() > DESCRIPTION_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Description must be 2000 characters or less.");
        }
    }

    private ApplicationGapPeriod toGapPeriod(JobApplication application, GapPeriodRequest request) {
        return ApplicationGapPeriod.create(
                application,
                request.startDate(),
                request.endDate(),
                request.gapType(),
                request.reason(),
                request.description(),
                request.sortOrder()
        );
    }

    private List<GapPeriodResponse> getGapPeriodResponses(Long applicationId) {
        return gapPeriodRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(GapPeriodResponse::from)
                .toList();
    }
}
