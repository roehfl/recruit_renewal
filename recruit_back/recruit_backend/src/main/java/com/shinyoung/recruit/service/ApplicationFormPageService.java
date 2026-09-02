package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.ApplicationFormPageItem;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicationFormPageRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.dto.response.ApplicationFormPageResponse;
import com.shinyoung.recruit.dto.response.ApplicationFormSectionResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationFormPageService {

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationFormPageRepository applicationFormPageRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final JobPostingAttachmentRequirementRepository attachmentRequirementRepository;
    private final ApplicationFormLayoutDefaultFactory defaultFactory;
    private final ApplicationFormLayoutValidator layoutValidator;
    private final Clock clock;

    public ApplicationFormPageResponse getFormPage(Long applicantId, Long applicationId) {
        JobApplication application = jobApplicationRepository.findFormPageByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));
        JobPosting jobPosting = application.getJobPosting();
        ApplicationFormConfig formConfig = requireFormConfig(jobPosting);
        validateSelectedJobPosition(application);

        Long jobPostingId = jobPosting.getId();
        Set<ApplicationSectionType> enabledSections = ApplicationFormLayoutSectionPolicy.enabledSections(
                formConfig,
                attachmentRequirementRepository.existsByJobPostingId(jobPostingId),
                jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrue(jobPostingId)
        );
        Set<ApplicationSectionType> requiredSections = ApplicationFormLayoutSectionPolicy.requiredSections(
                formConfig,
                attachmentRequirementRepository.existsByJobPostingIdAndRequiredTrue(jobPostingId),
                jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrueAndRequiredTrue(jobPostingId)
        );

        List<ApplicationFormPage> pages = loadStoredOrDefaultLayout(jobPosting, enabledSections);
        layoutValidator.validate(pages, enabledSections, requiredSections);

        boolean accepting = isAccepting(jobPosting);
        boolean editable = application.getStatus() != JobApplicationStatus.WITHDRAWN && accepting;
        return ApplicationFormPageResponse.from(
                application,
                accepting,
                editable,
                formConfig,
                toSectionResponses(pages, requiredSections)
        );
    }

    private ApplicationFormConfig requireFormConfig(JobPosting jobPosting) {
        ApplicationFormConfig formConfig = jobPosting.getApplicationFormConfig();
        if (formConfig == null) {
            throw new InvalidJobApplicationException("지원서 항목 설정이 없는 채용공고입니다.");
        }
        return formConfig;
    }

    private void validateSelectedJobPosition(JobApplication application) {
        JobPosting jobPosting = application.getJobPosting();
        JobPosition jobPosition = application.getJobPosition();
        if (jobPosition.getJobPosting() == null
                || jobPosition.getJobPosting().getId() == null
                || !jobPosition.getJobPosting().getId().equals(jobPosting.getId())) {
            throw new InvalidJobApplicationException("지원서의 모집분야가 채용공고에 속하지 않습니다.");
        }
    }

    private List<ApplicationFormPage> loadStoredOrDefaultLayout(
            JobPosting jobPosting,
            Set<ApplicationSectionType> enabledSections
    ) {
        List<ApplicationFormPage> pages = applicationFormPageRepository.findByJobPostingIdWithItems(jobPosting.getId());
        if (!pages.isEmpty()) {
            return pages;
        }
        return defaultFactory.createDefaultLayout(jobPosting, enabledSections);
    }

    private boolean isAccepting(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isBefore(jobPosting.getReceptionStartDateTime())
                && !now.isAfter(jobPosting.getReceptionEndDateTime());
    }

    private List<ApplicationFormSectionResponse> toSectionResponses(
            List<ApplicationFormPage> pages,
            Set<ApplicationSectionType> requiredSections
    ) {
        AtomicInteger sortOrder = new AtomicInteger();
        return pages.stream()
                .sorted(Comparator.comparing(ApplicationFormPage::getSortOrder)
                        .thenComparing(ApplicationFormPage::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .flatMap(page -> page.getItems().stream()
                        .sorted(Comparator.comparing(ApplicationFormPageItem::getSortOrder)
                                .thenComparing(ApplicationFormPageItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(item -> ApplicationFormSectionResponse.of(
                                item.getSectionType(),
                                requiredSections.contains(item.getSectionType()),
                                sortOrder.getAndIncrement(),
                                page.getPageNo(),
                                page.getTitle()
                        )))
                .toList();
    }
}
