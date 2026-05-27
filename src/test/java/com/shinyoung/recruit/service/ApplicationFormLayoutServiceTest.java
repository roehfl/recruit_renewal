package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationFormLayoutServiceTest {

    private static final Long JOB_POSTING_ID = 100L;
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private ApplicationFormPageRepository applicationFormPageRepository;

    @Mock
    private JobPostingQuestionRepository jobPostingQuestionRepository;

    @Mock
    private JobPostingAttachmentRequirementRepository attachmentRequirementRepository;

    private ApplicationFormLayoutService layoutService;

    @BeforeEach
    void setUp() {
        layoutService = new ApplicationFormLayoutService(
                jobPostingRepository,
                applicationFormPageRepository,
                jobPostingQuestionRepository,
                attachmentRequirementRepository,
                new ApplicationFormLayoutDefaultFactory(),
                new ApplicationFormLayoutValidator(),
                FIXED_CLOCK
        );

        lenient().when(applicationFormPageRepository.findByJobPostingIdWithItems(JOB_POSTING_ID))
                .thenReturn(List.of());
        lenient().when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrue(JOB_POSTING_ID))
                .thenReturn(false);
        lenient().when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrueAndRequiredTrue(JOB_POSTING_ID))
                .thenReturn(false);
        lenient().when(attachmentRequirementRepository.existsByJobPostingId(JOB_POSTING_ID))
                .thenReturn(false);
        lenient().when(attachmentRequirementRepository.existsByJobPostingIdAndRequiredTrue(JOB_POSTING_ID))
                .thenReturn(false);
    }

    @Test
    void getLayout_기본_설정_공고의_기본_레이아웃_반환() {
        ApplicationFormConfig config = config(true, true, false, false, true, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        AdminApplicationFormLayoutResponse response = layoutService.getLayout(JOB_POSTING_ID);

        assertThat(response.jobPostingId()).isEqualTo(JOB_POSTING_ID);
        assertThat(response.layoutStored()).isFalse();
        assertThat(response.editable()).isTrue();
        assertThat(response.pages()).isNotEmpty();
        assertThat(response.pages().stream()
                .flatMap(p -> p.items().stream())
                .map(AdminApplicationFormLayoutResponse.ItemResponse::sectionType))
                .containsExactlyInAnyOrder(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.MILITARY,
                        ApplicationSectionType.EDUCATION,
                        ApplicationSectionType.CAREER
                );
    }

    @Test
    void getLayout_저장된_레이아웃이_있으면_layoutStored_true() {
        ApplicationFormConfig config = config(true, false, false, false, true, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormPage page = ApplicationFormPage.create(posting, 1, "Page 1", null, 0);
        page.addItem(ApplicationSectionType.BASIC_INFO, 0);
        page.addItem(ApplicationSectionType.MILITARY, 1);
        ApplicationFormPage page2 = ApplicationFormPage.create(posting, 2, "Page 2", null, 1);
        page2.addItem(ApplicationSectionType.EDUCATION, 0);
        when(applicationFormPageRepository.findByJobPostingIdWithItems(JOB_POSTING_ID))
                .thenReturn(List.of(page, page2));

        AdminApplicationFormLayoutResponse response = layoutService.getLayout(JOB_POSTING_ID);

        assertThat(response.layoutStored()).isTrue();
        assertThat(response.pages()).hasSize(2);
        assertThat(response.pages().get(0).items()).hasSize(2);
        assertThat(response.pages().get(1).items()).hasSize(1);
    }

    @Test
    void getLayout_availableSections에_전체_레이아웃_섹션_표시() {
        ApplicationFormConfig config = config(true, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        AdminApplicationFormLayoutResponse response = layoutService.getLayout(JOB_POSTING_ID);

        assertThat(response.availableSections()).hasSize(ApplicationSectionType.layoutSectionTypes().size());
        AdminApplicationFormLayoutResponse.SectionAvailability basicInfo = response.availableSections().stream()
                .filter(s -> s.sectionType() == ApplicationSectionType.BASIC_INFO)
                .findFirst().orElseThrow();
        assertThat(basicInfo.enabled()).isTrue();
        assertThat(basicInfo.required()).isTrue();
        assertThat(basicInfo.placed()).isTrue();
        assertThat(basicInfo.source()).isEqualTo("ALWAYS");

        AdminApplicationFormLayoutResponse.SectionAvailability education = response.availableSections().stream()
                .filter(s -> s.sectionType() == ApplicationSectionType.EDUCATION)
                .findFirst().orElseThrow();
        assertThat(education.enabled()).isTrue();
        assertThat(education.placed()).isTrue();
        assertThat(education.source()).isEqualTo("APPLICATION_FORM_CONFIG");

        AdminApplicationFormLayoutResponse.SectionAvailability career = response.availableSections().stream()
                .filter(s -> s.sectionType() == ApplicationSectionType.CAREER)
                .findFirst().orElseThrow();
        assertThat(career.enabled()).isFalse();
        assertThat(career.placed()).isFalse();
    }

    @Test
    void getLayout_질문_첨부_활성_시_섹션_포함() {
        ApplicationFormConfig config = config(false, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);
        when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrue(JOB_POSTING_ID)).thenReturn(true);
        when(jobPostingQuestionRepository.existsByJobPostingIdAndActiveTrueAndRequiredTrue(JOB_POSTING_ID)).thenReturn(true);
        when(attachmentRequirementRepository.existsByJobPostingId(JOB_POSTING_ID)).thenReturn(true);
        when(attachmentRequirementRepository.existsByJobPostingIdAndRequiredTrue(JOB_POSTING_ID)).thenReturn(true);

        AdminApplicationFormLayoutResponse response = layoutService.getLayout(JOB_POSTING_ID);

        AdminApplicationFormLayoutResponse.SectionAvailability qa = response.availableSections().stream()
                .filter(s -> s.sectionType() == ApplicationSectionType.QUESTION_ANSWER)
                .findFirst().orElseThrow();
        assertThat(qa.enabled()).isTrue();
        assertThat(qa.required()).isTrue();
        assertThat(qa.source()).isEqualTo("QUESTION");

        AdminApplicationFormLayoutResponse.SectionAvailability att = response.availableSections().stream()
                .filter(s -> s.sectionType() == ApplicationSectionType.ATTACHMENT)
                .findFirst().orElseThrow();
        assertThat(att.enabled()).isTrue();
        assertThat(att.required()).isTrue();
        assertThat(att.source()).isEqualTo("ATTACHMENT_REQUIREMENT");
    }

    @Test
    void getLayout_마감된_공고는_editable_false() {
        ApplicationFormConfig config = config(false, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.CLOSED, config,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0));
        stubPosting(posting);

        AdminApplicationFormLayoutResponse response = layoutService.getLayout(JOB_POSTING_ID);

        assertThat(response.editable()).isFalse();
    }

    @Test
    void getLayout_접수_시작_후에는_editable_false() {
        ApplicationFormConfig config = config(false, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.PUBLISHED, config,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0));
        stubPosting(posting);

        AdminApplicationFormLayoutResponse response = layoutService.getLayout(JOB_POSTING_ID);

        assertThat(response.editable()).isFalse();
    }

    @Test
    void getLayout_접수_시작_전이면_editable_true() {
        ApplicationFormConfig config = config(false, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.PUBLISHED, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        AdminApplicationFormLayoutResponse response = layoutService.getLayout(JOB_POSTING_ID);

        assertThat(response.editable()).isTrue();
    }

    @Test
    void getLayout_존재하지_않는_공고는_404() {
        when(jobPostingRepository.findDetailById(JOB_POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> layoutService.getLayout(JOB_POSTING_ID))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void saveLayout_유효한_레이아웃_저장_성공() {
        ApplicationFormConfig config = config(true, false, false, false, true, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormLayoutSaveRequest request = new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Page 1", null, 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0),
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.MILITARY, 1)
                )),
                new ApplicationFormLayoutSaveRequest.PageRequest(2, "Page 2", "Education page", 1, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.EDUCATION, 0)
                ))
        ));

        AdminApplicationFormLayoutResponse response = layoutService.saveLayout(JOB_POSTING_ID, request);

        assertThat(response.layoutStored()).isTrue();
        assertThat(response.pages()).hasSize(2);
        verify(applicationFormPageRepository).deleteByJobPostingId(JOB_POSTING_ID);
        verify(applicationFormPageRepository).saveAll(anyList());
    }

    @Test
    void saveLayout_마감된_공고에_저장_시도시_실패() {
        ApplicationFormConfig config = config(false, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.CLOSED, config,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormLayoutSaveRequest request = validBasicInfoOnlyRequest();

        assertThatThrownBy(() -> layoutService.saveLayout(JOB_POSTING_ID, request))
                .isInstanceOf(InvalidApplicationFormLayoutException.class)
                .hasMessageContaining("마감");
        verify(applicationFormPageRepository, never()).deleteByJobPostingId(any());
    }

    @Test
    void saveLayout_접수_시작_후_저장_시도시_실패() {
        ApplicationFormConfig config = config(false, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.PUBLISHED, config,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormLayoutSaveRequest request = validBasicInfoOnlyRequest();

        assertThatThrownBy(() -> layoutService.saveLayout(JOB_POSTING_ID, request))
                .isInstanceOf(InvalidApplicationFormLayoutException.class)
                .hasMessageContaining("접수");
        verify(applicationFormPageRepository, never()).deleteByJobPostingId(any());
    }

    @Test
    void saveLayout_formConfig_없으면_실패() {
        JobPosting posting = posting(JobPostingStatus.DRAFT, null,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormLayoutSaveRequest request = validBasicInfoOnlyRequest();

        assertThatThrownBy(() -> layoutService.saveLayout(JOB_POSTING_ID, request))
                .isInstanceOf(InvalidApplicationFormLayoutException.class)
                .hasMessageContaining("항목 설정");
    }

    @Test
    void saveLayout_비활성_섹션_배치시_검증_실패() {
        ApplicationFormConfig config = config(false, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormLayoutSaveRequest request = new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Page 1", null, 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0),
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.EDUCATION, 1)
                ))
        ));

        assertThatThrownBy(() -> layoutService.saveLayout(JOB_POSTING_ID, request))
                .isInstanceOf(InvalidApplicationFormLayoutException.class);
    }

    @Test
    void getPreview_활성_섹션만_페이지_구조로_반환() {
        ApplicationFormConfig config = config(true, true, false, false, true, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormLayoutPreviewResponse response = layoutService.getPreview(JOB_POSTING_ID);

        assertThat(response.jobPostingId()).isEqualTo(JOB_POSTING_ID);
        assertThat(response.jobPostingTitle()).isEqualTo("Layout Posting");
        assertThat(response.pages()).isNotEmpty();
        assertThat(response.pages().stream()
                .flatMap(p -> p.items().stream())
                .map(ApplicationFormLayoutPreviewResponse.ItemResponse::sectionType))
                .containsExactlyInAnyOrder(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.MILITARY,
                        ApplicationSectionType.EDUCATION,
                        ApplicationSectionType.CAREER
                );
    }

    @Test
    void getPreview_각_아이템에_required_표시() {
        ApplicationFormConfig config = config(
                true, true,
                true, false,
                false, false,
                false, false,
                true, true,
                false, false,
                false, false
        );
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormLayoutPreviewResponse response = layoutService.getPreview(JOB_POSTING_ID);

        List<ApplicationFormLayoutPreviewResponse.ItemResponse> allItems = response.pages().stream()
                .flatMap(p -> p.items().stream())
                .toList();

        ApplicationFormLayoutPreviewResponse.ItemResponse basicInfo = allItems.stream()
                .filter(i -> i.sectionType() == ApplicationSectionType.BASIC_INFO)
                .findFirst().orElseThrow();
        assertThat(basicInfo.required()).isTrue();

        ApplicationFormLayoutPreviewResponse.ItemResponse education = allItems.stream()
                .filter(i -> i.sectionType() == ApplicationSectionType.EDUCATION)
                .findFirst().orElseThrow();
        assertThat(education.required()).isTrue();

        ApplicationFormLayoutPreviewResponse.ItemResponse career = allItems.stream()
                .filter(i -> i.sectionType() == ApplicationSectionType.CAREER)
                .findFirst().orElseThrow();
        assertThat(career.required()).isFalse();

        ApplicationFormLayoutPreviewResponse.ItemResponse military = allItems.stream()
                .filter(i -> i.sectionType() == ApplicationSectionType.MILITARY)
                .findFirst().orElseThrow();
        assertThat(military.required()).isTrue();
    }

    @Test
    void getPreview_비활성_섹션은_숨김() {
        ApplicationFormConfig config = config(true, false, false, false, false, false, false);
        JobPosting posting = posting(JobPostingStatus.DRAFT, config,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        stubPosting(posting);

        ApplicationFormPage page = ApplicationFormPage.create(posting, 1, "Page 1", null, 0);
        page.addItem(ApplicationSectionType.BASIC_INFO, 0);
        page.addItem(ApplicationSectionType.EDUCATION, 1);
        when(applicationFormPageRepository.findByJobPostingIdWithItems(JOB_POSTING_ID))
                .thenReturn(List.of(page));

        ApplicationFormLayoutPreviewResponse response = layoutService.getPreview(JOB_POSTING_ID);

        List<ApplicationSectionType> previewSections = response.pages().stream()
                .flatMap(p -> p.items().stream())
                .map(ApplicationFormLayoutPreviewResponse.ItemResponse::sectionType)
                .toList();
        assertThat(previewSections).contains(ApplicationSectionType.BASIC_INFO);
        assertThat(previewSections).contains(ApplicationSectionType.EDUCATION);

        ApplicationFormConfig configNoEdu = config(false, false, false, false, false, false, false);
        JobPosting posting2 = posting(JobPostingStatus.DRAFT, configNoEdu,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0));
        when(jobPostingRepository.findDetailById(JOB_POSTING_ID)).thenReturn(Optional.of(posting2));

        ApplicationFormPage page2 = ApplicationFormPage.create(posting2, 1, "Page 1", null, 0);
        page2.addItem(ApplicationSectionType.BASIC_INFO, 0);
        page2.addItem(ApplicationSectionType.EDUCATION, 1);
        when(applicationFormPageRepository.findByJobPostingIdWithItems(JOB_POSTING_ID))
                .thenReturn(List.of(page2));

        ApplicationFormLayoutPreviewResponse response2 = layoutService.getPreview(JOB_POSTING_ID);

        List<ApplicationSectionType> previewSections2 = response2.pages().stream()
                .flatMap(p -> p.items().stream())
                .map(ApplicationFormLayoutPreviewResponse.ItemResponse::sectionType)
                .toList();
        assertThat(previewSections2).contains(ApplicationSectionType.BASIC_INFO);
        assertThat(previewSections2).doesNotContain(ApplicationSectionType.EDUCATION);
    }

    private void stubPosting(JobPosting posting) {
        when(jobPostingRepository.findDetailById(JOB_POSTING_ID)).thenReturn(Optional.of(posting));
    }

    private ApplicationFormLayoutSaveRequest validBasicInfoOnlyRequest() {
        return new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Basic", null, 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0)
                ))
        ));
    }

    private JobPosting posting(
            JobPostingStatus status,
            ApplicationFormConfig config,
            LocalDateTime receptionStart,
            LocalDateTime receptionEnd
    ) {
        JobPosting posting = mock(JobPosting.class);
        lenient().when(posting.getId()).thenReturn(JOB_POSTING_ID);
        lenient().when(posting.getTitle()).thenReturn("Layout Posting");
        lenient().when(posting.getStatus()).thenReturn(status);
        lenient().when(posting.getReceptionStartDateTime()).thenReturn(receptionStart);
        lenient().when(posting.getReceptionEndDateTime()).thenReturn(receptionEnd);
        lenient().when(posting.getApplicationFormConfig()).thenReturn(config);
        return posting;
    }

    private ApplicationFormConfig config(
            boolean useEducation,
            boolean useCareer,
            boolean useCertificate,
            boolean useLanguage,
            boolean useMilitary,
            boolean useAward,
            boolean useGapPeriod
    ) {
        ApplicationFormConfig config = mock(ApplicationFormConfig.class);
        lenient().when(config.isUseEducation()).thenReturn(useEducation);
        lenient().when(config.isRequireEducation()).thenReturn(useEducation);
        lenient().when(config.isUseCareer()).thenReturn(useCareer);
        lenient().when(config.isRequireCareer()).thenReturn(useCareer);
        lenient().when(config.isUseCertificate()).thenReturn(useCertificate);
        lenient().when(config.isRequireCertificate()).thenReturn(false);
        lenient().when(config.isUseLanguage()).thenReturn(useLanguage);
        lenient().when(config.isRequireLanguage()).thenReturn(false);
        lenient().when(config.isUseMilitary()).thenReturn(useMilitary);
        lenient().when(config.isRequireMilitary()).thenReturn(useMilitary);
        lenient().when(config.isUseAward()).thenReturn(useAward);
        lenient().when(config.isRequireAward()).thenReturn(false);
        lenient().when(config.isUseGapPeriod()).thenReturn(useGapPeriod);
        lenient().when(config.isRequireGapPeriod()).thenReturn(false);
        return config;
    }

    private ApplicationFormConfig config(
            boolean useEducation,
            boolean requireEducation,
            boolean useCareer,
            boolean requireCareer,
            boolean useCertificate,
            boolean requireCertificate,
            boolean useLanguage,
            boolean requireLanguage,
            boolean useMilitary,
            boolean requireMilitary,
            boolean useAward,
            boolean requireAward,
            boolean useGapPeriod,
            boolean requireGapPeriod
    ) {
        ApplicationFormConfig config = mock(ApplicationFormConfig.class);
        lenient().when(config.isUseEducation()).thenReturn(useEducation);
        lenient().when(config.isRequireEducation()).thenReturn(requireEducation);
        lenient().when(config.isUseCareer()).thenReturn(useCareer);
        lenient().when(config.isRequireCareer()).thenReturn(requireCareer);
        lenient().when(config.isUseCertificate()).thenReturn(useCertificate);
        lenient().when(config.isRequireCertificate()).thenReturn(requireCertificate);
        lenient().when(config.isUseLanguage()).thenReturn(useLanguage);
        lenient().when(config.isRequireLanguage()).thenReturn(requireLanguage);
        lenient().when(config.isUseMilitary()).thenReturn(useMilitary);
        lenient().when(config.isRequireMilitary()).thenReturn(requireMilitary);
        lenient().when(config.isUseAward()).thenReturn(useAward);
        lenient().when(config.isRequireAward()).thenReturn(requireAward);
        lenient().when(config.isUseGapPeriod()).thenReturn(useGapPeriod);
        lenient().when(config.isRequireGapPeriod()).thenReturn(requireGapPeriod);
        return config;
    }
}
