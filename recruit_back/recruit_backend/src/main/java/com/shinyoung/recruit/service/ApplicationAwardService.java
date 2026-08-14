package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.dto.request.AwardReplaceRequest;
import com.shinyoung.recruit.dto.request.AwardRequest;
import com.shinyoung.recruit.dto.response.AwardResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationAwardService {

    private static final int DESCRIPTION_MAX_LENGTH = 2000;

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationAwardRepository awardRepository;

    @Transactional(readOnly = true)
    public List<AwardResponse> getAwards(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return getAwardResponses(applicationId);
    }

    @Transactional
    public List<AwardResponse> replaceAwards(
            Long applicantId,
            Long applicationId,
            AwardReplaceRequest request
    ) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        sectionAccessService.validateAwardEnabled(application);
        validateRequest(request);

        awardRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationAward> awards = request.awards().stream()
                .map(award -> toAward(application, award))
                .toList();
        awardRepository.saveAll(awards);

        return getAwardResponses(applicationId);
    }

    private void validateRequest(AwardReplaceRequest request) {
        if (request == null || request.awards() == null) {
            throw new InvalidJobApplicationException("Award list is required.");
        }

        Set<Integer> sortOrders = new HashSet<>();
        for (AwardRequest award : request.awards()) {
            validateAwardRequiredFields(award);
            if (!sortOrders.add(award.sortOrder())) {
                throw new InvalidJobApplicationException("Award sort order must be unique.");
            }
        }
    }

    private void validateAwardRequiredFields(AwardRequest award) {
        if (award == null) {
            throw new InvalidJobApplicationException("Award item is required.");
        }
        if (award.awardName() == null || award.awardName().isBlank()) {
            throw new InvalidJobApplicationException("Award name is required.");
        }
        if (award.awardingOrganization() == null || award.awardingOrganization().isBlank()) {
            throw new InvalidJobApplicationException("Awarding organization is required.");
        }
        if (award.awardDate() == null) {
            throw new InvalidJobApplicationException("Award date is required.");
        }
        if (award.sortOrder() == null || award.sortOrder() < 0) {
            throw new InvalidJobApplicationException("Sort order must be greater than or equal to 0.");
        }
        if (award.description() != null && award.description().length() > DESCRIPTION_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Description must be 2000 characters or less.");
        }
    }

    private ApplicationAward toAward(JobApplication application, AwardRequest request) {
        return ApplicationAward.create(
                application,
                request.awardName(),
                request.awardingOrganization(),
                request.awardDate(),
                request.description(),
                request.sortOrder()
        );
    }

    private List<AwardResponse> getAwardResponses(Long applicationId) {
        return awardRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AwardResponse::from)
                .toList();
    }
}
