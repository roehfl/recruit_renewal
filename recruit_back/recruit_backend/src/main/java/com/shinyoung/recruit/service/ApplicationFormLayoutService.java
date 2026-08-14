package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.ApplicationFormPageItem;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicationFormPageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormLayoutSaveRequest;
import com.shinyoung.recruit.dto.response.AdminApplicationFormLayoutResponse;
import com.shinyoung.recruit.dto.response.ApplicationFormLayoutPreviewResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidApplicationFormLayoutException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationFormLayoutService {

    private final JobPostingRepository jobPostingRepository;
    private final ApplicationFormPageRepository applicationFormPageRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final JobPostingAttachmentRequirementRepository attachmentRequirementRepository;
    private final ApplicationFormLayoutDefaultFactory defaultFactory;
    private final ApplicationFormLayoutValidator layoutValidator;
    private final Clock clock;

    public AdminApplicationFormLayoutResponse getLayout(Long jobPostingId) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        ApplicationFormConfig formConfig = requireFormConfig(jobPosting);

        Set<ApplicationSectionType> enabledSections = calculateEnabledSections(jobPostingId, formConfig);
        Set<ApplicationSectionType> requiredSections = calculateRequiredSections(jobPostingId, formConfig);

        List<ApplicationFormPage> storedPages = applicationFormPageRepository.findByJobPostingIdWithItems(jobPostingId);
        boolean layoutStored = !storedPages.isEmpty();
        List<ApplicationFormPage> pages = layoutStored
                ? storedPages
                : defaultFactory.createDefaultLayout(jobPosting, enabledSections);

        boolean editable = isEditable(jobPosting);
        Set<ApplicationSectionType> placedSections = collectPlacedSections(pages);

        return buildAdminResponse(jobPostingId, layoutStored, editable, pages, enabledSections, requiredSections, placedSections);
    }

    @Transactional
    public AdminApplicationFormLayoutResponse saveLayout(Long jobPostingId, ApplicationFormLayoutSaveRequest request) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        ApplicationFormConfig formConfig = requireFormConfig(jobPosting);

        validateEditable(jobPosting);

        Set<ApplicationSectionType> enabledSections = calculateEnabledSections(jobPostingId, formConfig);
        Set<ApplicationSectionType> requiredSections = calculateRequiredSections(jobPostingId, formConfig);

        List<ApplicationFormPage> newPages = toEntities(jobPosting, request);
        layoutValidator.validate(newPages, enabledSections, requiredSections);

        applicationFormPageRepository.deleteByJobPostingId(jobPostingId);
        applicationFormPageRepository.flush();
        applicationFormPageRepository.saveAll(newPages);

        Set<ApplicationSectionType> placedSections = collectPlacedSections(newPages);
        return buildAdminResponse(jobPostingId, true, true, newPages, enabledSections, requiredSections, placedSections);
    }

    public void validateLayoutForPublish(JobPosting jobPosting) {
        ApplicationFormConfig formConfig = jobPosting.getApplicationFormConfig();
        if (formConfig == null) {
            throw new InvalidApplicationFormLayoutException("지원서 항목 설정이 없는 채용공고는 게시할 수 없습니다.");
        }

        Long jobPostingId = jobPosting.getId();
        Set<ApplicationSectionType> enabledSections = calculateEnabledSections(jobPostingId, formConfig);
        Set<ApplicationSectionType> requiredSections = calculateRequiredSections(jobPostingId, formConfig);

        List<ApplicationFormPage> storedPages = applicationFormPageRepository.findByJobPostingIdWithItems(jobPostingId);
        List<ApplicationFormPage> pages = !storedPages.isEmpty()
                ? storedPages
                : defaultFactory.createDefaultLayout(jobPosting, enabledSections);

        layoutValidator.validate(pages, enabledSections, requiredSections);
    }

    public ApplicationFormLayoutPreviewResponse getPreview(Long jobPostingId) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        ApplicationFormConfig formConfig = requireFormConfig(jobPosting);

        Set<ApplicationSectionType> enabledSections = calculateEnabledSections(jobPostingId, formConfig);
        Set<ApplicationSectionType> requiredSections = calculateRequiredSections(jobPostingId, formConfig);

        List<ApplicationFormPage> storedPages = applicationFormPageRepository.findByJobPostingIdWithItems(jobPostingId);
        List<ApplicationFormPage> pages = !storedPages.isEmpty()
                ? storedPages
                : defaultFactory.createDefaultLayout(jobPosting, enabledSections);

        return buildPreviewResponse(jobPostingId, jobPosting.getTitle(), pages, enabledSections, requiredSections);
    }

    private JobPosting findJobPosting(Long jobPostingId) {
        return jobPostingRepository.findDetailById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));
    }

    private ApplicationFormConfig requireFormConfig(JobPosting jobPosting) {
        ApplicationFormConfig formConfig = jobPosting.getApplicationFormConfig();
        if (formConfig == null) {
            throw new InvalidApplicationFormLayoutException("지원서 항목 설정이 없는 채용공고입니다.");
        }
        return formConfig;
    }

    private Set<ApplicationSectionType> calculateEnabledSections(Long jobPostingId, ApplicationFormConfig formConfig) {
        return ApplicationFormLayoutSectionPolicy.enabledSections(
                formConfig,
                attachmentRequirementRepository.existsByJobPostingId(jobPostingId),
                jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrue(jobPostingId)
        );
    }

    private Set<ApplicationSectionType> calculateRequiredSections(Long jobPostingId, ApplicationFormConfig formConfig) {
        return ApplicationFormLayoutSectionPolicy.requiredSections(
                formConfig,
                attachmentRequirementRepository.existsByJobPostingIdAndRequiredTrue(jobPostingId),
                jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrueAndRequiredTrue(jobPostingId)
        );
    }

    private boolean isEditable(JobPosting jobPosting) {
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return now.isBefore(jobPosting.getReceptionStartDateTime());
    }

    private void validateEditable(JobPosting jobPosting) {
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidApplicationFormLayoutException("마감된 채용공고의 레이아웃은 수정할 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(jobPosting.getReceptionStartDateTime())) {
            throw new InvalidApplicationFormLayoutException("접수가 시작된 채용공고의 레이아웃은 수정할 수 없습니다.");
        }
    }

    private List<ApplicationFormPage> toEntities(JobPosting jobPosting, ApplicationFormLayoutSaveRequest request) {
        return request.pages().stream()
                .map(pageReq -> {
                    ApplicationFormPage page = ApplicationFormPage.create(
                            jobPosting,
                            pageReq.pageNo(),
                            pageReq.title(),
                            pageReq.description(),
                            pageReq.sortOrder()
                    );
                    pageReq.items().forEach(itemReq ->
                            page.addItem(itemReq.sectionType(), itemReq.sortOrder())
                    );
                    return page;
                })
                .toList();
    }

    private Set<ApplicationSectionType> collectPlacedSections(List<ApplicationFormPage> pages) {
        EnumSet<ApplicationSectionType> placed = EnumSet.noneOf(ApplicationSectionType.class);
        for (ApplicationFormPage page : pages) {
            for (ApplicationFormPageItem item : page.getItems()) {
                placed.add(item.getSectionType());
            }
        }
        return placed;
    }

    private AdminApplicationFormLayoutResponse buildAdminResponse(
            Long jobPostingId,
            boolean layoutStored,
            boolean editable,
            List<ApplicationFormPage> pages,
            Set<ApplicationSectionType> enabledSections,
            Set<ApplicationSectionType> requiredSections,
            Set<ApplicationSectionType> placedSections
    ) {
        List<AdminApplicationFormLayoutResponse.PageResponse> pageResponses = pages.stream()
                .sorted(pageComparator())
                .map(page -> new AdminApplicationFormLayoutResponse.PageResponse(
                        page.getPageNo(),
                        page.getTitle(),
                        page.getDescription(),
                        page.getSortOrder(),
                        page.getItems().stream()
                                .sorted(itemComparator())
                                .map(item -> new AdminApplicationFormLayoutResponse.ItemResponse(
                                        item.getSectionType(),
                                        labelOf(item.getSectionType()),
                                        item.getSortOrder(),
                                        enabledSections.contains(item.getSectionType()),
                                        requiredSections.contains(item.getSectionType()),
                                        true
                                ))
                                .toList()
                ))
                .toList();

        List<AdminApplicationFormLayoutResponse.SectionAvailability> availableSections =
                ApplicationSectionType.layoutSectionTypes().stream()
                        .sorted(Comparator.comparingInt(Enum::ordinal))
                        .map(sectionType -> new AdminApplicationFormLayoutResponse.SectionAvailability(
                                sectionType,
                                labelOf(sectionType),
                                enabledSections.contains(sectionType),
                                requiredSections.contains(sectionType),
                                placedSections.contains(sectionType),
                                sourceOf(sectionType)
                        ))
                        .toList();

        return new AdminApplicationFormLayoutResponse(jobPostingId, layoutStored, editable, pageResponses, availableSections);
    }

    private ApplicationFormLayoutPreviewResponse buildPreviewResponse(
            Long jobPostingId,
            String jobPostingTitle,
            List<ApplicationFormPage> pages,
            Set<ApplicationSectionType> enabledSections,
            Set<ApplicationSectionType> requiredSections
    ) {
        List<ApplicationFormLayoutPreviewResponse.PageResponse> pageResponses = pages.stream()
                .sorted(pageComparator())
                .map(page -> new ApplicationFormLayoutPreviewResponse.PageResponse(
                        page.getPageNo(),
                        page.getTitle(),
                        page.getDescription(),
                        page.getSortOrder(),
                        page.getItems().stream()
                                .sorted(itemComparator())
                                .filter(item -> enabledSections.contains(item.getSectionType()))
                                .map(item -> new ApplicationFormLayoutPreviewResponse.ItemResponse(
                                        item.getSectionType(),
                                        labelOf(item.getSectionType()),
                                        requiredSections.contains(item.getSectionType()),
                                        item.getSortOrder()
                                ))
                                .toList()
                ))
                .filter(page -> !page.items().isEmpty())
                .toList();

        return new ApplicationFormLayoutPreviewResponse(jobPostingId, jobPostingTitle, pageResponses);
    }

    private static Comparator<ApplicationFormPage> pageComparator() {
        return Comparator.comparing(ApplicationFormPage::getSortOrder)
                .thenComparing(ApplicationFormPage::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<ApplicationFormPageItem> itemComparator() {
        return Comparator.comparing(ApplicationFormPageItem::getSortOrder)
                .thenComparing(ApplicationFormPageItem::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    static String labelOf(ApplicationSectionType sectionType) {
        return switch (sectionType) {
            case BASIC_INFO -> "Basic Info";
            case EDUCATION -> "Education";
            case CAREER -> "Career";
            case MILITARY -> "Military";
            case CERTIFICATE -> "Certificate";
            case LANGUAGE -> "Language";
            case AWARD -> "Award";
            case GAP_PERIOD -> "Gap Period";
            case QUESTION_ANSWER -> "Questions";
            case ATTACHMENT -> "Attachment";
            case APPLICATION, ETC -> sectionType.name();
        };
    }

    static String sourceOf(ApplicationSectionType sectionType) {
        return switch (sectionType) {
            case BASIC_INFO -> "ALWAYS";
            case EDUCATION, CAREER, CERTIFICATE, LANGUAGE, MILITARY, AWARD, GAP_PERIOD -> "APPLICATION_FORM_CONFIG";
            case QUESTION_ANSWER -> "QUESTION";
            case ATTACHMENT -> "ATTACHMENT_REQUIREMENT";
            case APPLICATION, ETC -> "UNKNOWN";
        };
    }
}
