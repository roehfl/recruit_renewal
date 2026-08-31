package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.CommonCode;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequirementReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequirementRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.response.ApplicationFormRequiredPolicyResponse;
import com.shinyoung.recruit.dto.response.ApplicationFormSectionPolicyResponse;
import com.shinyoung.recruit.dto.response.JobPostingPublicDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingPublicListResponse;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ApplicationFormRequirementType;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.enumeration.ReceptionStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@Transactional
class JobPostingPublicServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T10:00:00Z"),
            ZoneId.of("UTC")
    );

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingPublicService jobPostingPublicService;

    @Autowired
    private JobPostingImageService jobPostingImageService;

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    @Autowired
    private JobPostingAttachmentRequirementService attachmentRequirementService;

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    /** 공통 request 헬퍼가 쓰는 근무지 코드(CommonCode 그룹 WORK_LOCATION)를 미리 등록한다. */
    @BeforeEach
    void 근무지코드_등록() {
        commonCodeRepository.save(CommonCode.create("WORK_LOCATION", "SEOUL", "Seoul", 0, true, null));
        commonCodeRepository.save(CommonCode.create("WORK_LOCATION", "BUSAN", "Busan", 1, true, null));
    }

    @Test
    void public_list_returns_only_published_visible_displayable_postings() {
        Long visibleId = createPublishedPosting(request(
                "visible",
                JobPostingType.PUBLIC_RECRUITMENT,
                "visible summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                false,
                1
        ));
        Long hiddenId = createPublishedPosting(request("hidden", true, receptionStart(), receptionEnd(), displayStart(), displayEnd(), false));
        Long futureDisplayId = createPublishedPosting(request(
                "future display",
                true,
                receptionStart(),
                receptionEnd(),
                LocalDateTime.of(2026, 6, 2, 0, 0),
                displayEnd(),
                true
        ));
        Long pastDisplayId = createPublishedPosting(request(
                "past display",
                true,
                receptionStart(),
                receptionEnd(),
                displayStart(),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                true
        ));
        Long draftId = jobPostingService.create(request("draft", true, receptionStart(), receptionEnd(), displayStart(), displayEnd(), true));
        Long closedStatusId = createPublishedPosting(request("closed status", true, receptionStart(), receptionEnd(), displayStart(), displayEnd(), true));
        jobPostingService.close(closedStatusId);

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content())
                .extracting(JobPostingPublicListResponse::id)
                .contains(visibleId)
                .doesNotContain(hiddenId, futureDisplayId, pastDisplayId, draftId, closedStatusId);
    }

    @Test
    void public_list_computes_reception_status_and_keeps_closed_reception_visible() {
        Long acceptingId = createPublishedPosting(request(
                "accepting",
                false,
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true
        ));
        Long upcomingId = createPublishedPosting(request(
                "upcoming",
                false,
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 3, 18, 0),
                displayStart(),
                displayEnd(),
                true
        ));
        Long closedReceptionId = createPublishedPosting(request(
                "closed reception",
                false,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 2, 18, 0),
                displayStart(),
                displayEnd(),
                true
        ));

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        JobPostingPublicListResponse accepting = findById(response, acceptingId);
        JobPostingPublicListResponse upcoming = findById(response, upcomingId);
        JobPostingPublicListResponse closedReception = findById(response, closedReceptionId);
        assertThat(accepting.receptionStatus()).isEqualTo(ReceptionStatus.ACCEPTING);
        assertThat(accepting.accepting()).isTrue();
        assertThat(upcoming.receptionStatus()).isEqualTo(ReceptionStatus.UPCOMING);
        assertThat(upcoming.accepting()).isFalse();
        assertThat(closedReception.receptionStatus()).isEqualTo(ReceptionStatus.CLOSED);
        assertThat(closedReception.accepting()).isFalse();
    }

    @Test
    void public_list_sorts_by_pinned_reception_status_and_display_order() {
        Long acceptingLater = createPublishedPosting(request(
                "accepting later",
                false,
                receptionStart(),
                LocalDateTime.of(2026, 6, 5, 18, 0),
                displayStart(),
                displayEnd(),
                true,
                2
        ));
        Long acceptingEarlier = createPublishedPosting(request(
                "accepting earlier",
                false,
                receptionStart(),
                LocalDateTime.of(2026, 6, 4, 18, 0),
                displayStart(),
                displayEnd(),
                true,
                1
        ));
        Long upcoming = createPublishedPosting(request(
                "upcoming",
                false,
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 3, 18, 0),
                displayStart(),
                displayEnd(),
                true,
                0
        ));
        Long closedReception = createPublishedPosting(request(
                "closed reception",
                false,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 2, 18, 0),
                displayStart(),
                displayEnd(),
                true,
                0
        ));
        Long pinnedClosedReception = createPublishedPosting(request(
                "pinned closed reception",
                true,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 2, 18, 0),
                displayStart(),
                displayEnd(),
                true,
                0
        ));

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content())
                .extracting(JobPostingPublicListResponse::id)
                .containsExactly(
                        pinnedClosedReception,
                        acceptingEarlier,
                        acceptingLater,
                        upcoming,
                        closedReception
                );
    }

    @Test
    void public_list_sorts_by_reception_end_when_priority_and_display_order_match() {
        Long laterEnd = createPublishedPosting(request(
                "later end",
                false,
                receptionStart(),
                LocalDateTime.of(2026, 6, 5, 18, 0),
                displayStart(),
                displayEnd(),
                true,
                0
        ));
        Long earlierEnd = createPublishedPosting(request(
                "earlier end",
                false,
                receptionStart(),
                LocalDateTime.of(2026, 6, 4, 18, 0),
                displayStart(),
                displayEnd(),
                true,
                0
        ));

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content())
                .extracting(JobPostingPublicListResponse::id)
                .containsExactly(earlierEnd, laterEnd);
    }

    @Test
    void public_list_sorts_by_id_desc_when_all_other_sort_keys_match() {
        Long first = createPublishedPosting(request(
                "same first",
                false,
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                0
        ));
        Long second = createPublishedPosting(request(
                "same second",
                false,
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                0
        ));
        Long third = createPublishedPosting(request(
                "same third",
                false,
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                0
        ));

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content())
                .extracting(JobPostingPublicListResponse::id)
                .containsExactly(third, second, first);
    }

    @Test
    void public_list_includes_applicant_display_fields_and_positions() {
        Long id = createPublishedPosting(request(
                "expanded",
                JobPostingType.EXPERIENCED_RECRUITMENT,
                "expanded summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                true,
                1
        ));

        JobPostingPublicListResponse response = findById(jobPostingPublicService.getJobPostings(0, 10), id);

        assertThat(response.postingType()).isEqualTo(JobPostingType.EXPERIENCED_RECRUITMENT);
        assertThat(response.summary()).isEqualTo("expanded summary");
        assertThat(response.pinned()).isTrue();
        assertThat(response.positions()).hasSize(2);
        assertThat(response.positions().get(0).positionName()).isEqualTo("Analyst");
        assertThat(response.positions().get(0).applicationType()).isEqualTo(JobPositionApplicationType.NEW_GRADUATE);
        assertThat(response.positions().get(0).jobGroup()).isEqualTo("Research");
        assertThat(response.positions().get(0).jobTitle()).isEqualTo("Equity Analyst");
        assertThat(response.positions().get(0).workLocations())
                .extracting("code", "name")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SEOUL", "Seoul"));
        assertThat(response.positions().get(0).employmentType()).isEqualTo(EmploymentType.FULL_TIME);
    }

    @Test
    void public_list_includes_application_form_required_policy() {
        Long id = createPublishedPosting(request(
                "policy",
                JobPostingType.PUBLIC_RECRUITMENT,
                "policy summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                false,
                1
        ));

        ApplicationFormRequiredPolicyResponse policy = findById(
                jobPostingPublicService.getJobPostings(0, 10),
                id
        ).applicationFormRequiredPolicy();

        assertThat(policy.requiredSectionCount()).isEqualTo(2);
        assertThat(policy.optionalSectionCount()).isEqualTo(2);
        assertThat(policy.requiredQuestionCount()).isZero();
        assertThat(policy.optionalQuestionCount()).isZero();
        assertThat(policy.hasRequiredQuestion()).isFalse();
        assertThat(policy.attachmentRequired()).isFalse();
        assertThat(policy.sections())
                .extracting(ApplicationFormSectionPolicyResponse::sectionCode)
                .containsExactly(
                        "EDUCATION",
                        "CAREER",
                        "CERTIFICATE",
                        "LANGUAGE",
                        "MILITARY",
                        "AWARD",
                        "GAP_PERIOD",
                        "QUESTION",
                        "ATTACHMENT"
                );
        assertSection(policy, "EDUCATION", true, true, ApplicationFormRequirementType.REQUIRED);
        assertSection(policy, "CAREER", false, false, ApplicationFormRequirementType.DISABLED);
        assertSection(policy, "CERTIFICATE", true, false, ApplicationFormRequirementType.OPTIONAL);
        assertSection(policy, "LANGUAGE", false, false, ApplicationFormRequirementType.DISABLED);
        assertSection(policy, "MILITARY", true, true, ApplicationFormRequirementType.REQUIRED);
        assertSection(policy, "AWARD", true, false, ApplicationFormRequirementType.OPTIONAL);
        assertSection(policy, "GAP_PERIOD", false, false, ApplicationFormRequirementType.DISABLED);
        assertSection(policy, "QUESTION", false, false, ApplicationFormRequirementType.DISABLED);
        assertSection(policy, "ATTACHMENT", false, false, ApplicationFormRequirementType.DISABLED);
    }

    @Test
    void public_policy_uses_explicit_required_flags_from_detail_and_list() {
        Long id = jobPostingService.create(request(
                "explicit required policy",
                JobPostingType.PUBLIC_RECRUITMENT,
                "explicit required policy summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                false,
                1,
                new ApplicationFormConfigRequest(
                        true,
                        false,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false,
                        true,
                        false,
                        true,
                        true,
                        true,
                        false,
                        false
                )
        ));
        jobPostingService.publish(id);

        ApplicationFormRequiredPolicyResponse detailPolicy =
                jobPostingPublicService.getJobPosting(id).applicationFormRequiredPolicy();
        ApplicationFormRequiredPolicyResponse listPolicy = findById(
                jobPostingPublicService.getJobPostings(0, 10),
                id
        ).applicationFormRequiredPolicy();

        assertThat(detailPolicy.requiredSectionCount()).isEqualTo(3);
        assertThat(detailPolicy.optionalSectionCount()).isEqualTo(3);
        assertSection(detailPolicy, "EDUCATION", true, false, ApplicationFormRequirementType.OPTIONAL);
        assertSection(detailPolicy, "CAREER", true, true, ApplicationFormRequirementType.REQUIRED);
        assertSection(detailPolicy, "CERTIFICATE", true, true, ApplicationFormRequirementType.REQUIRED);
        assertSection(detailPolicy, "MILITARY", true, false, ApplicationFormRequirementType.OPTIONAL);
        assertSection(detailPolicy, "AWARD", true, true, ApplicationFormRequirementType.REQUIRED);
        assertSection(detailPolicy, "GAP_PERIOD", true, false, ApplicationFormRequirementType.OPTIONAL);
        assertThat(listPolicy).isEqualTo(detailPolicy);
    }

    @Test
    void public_list_uses_attachment_requirement_aggregate_without_exposing_full_list() {
        Long id = jobPostingService.create(request(
                "attachment aggregate",
                JobPostingType.PUBLIC_RECRUITMENT,
                "attachment aggregate summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                false,
                1
        ));
        replaceAttachmentRequirements(id, List.of(
                attachmentRequirement(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, true, 1)
        ));
        jobPostingService.publish(id);

        JobPostingPublicListResponse response = findById(jobPostingPublicService.getJobPostings(0, 10), id);

        assertThat(response.applicationFormRequiredPolicy().attachmentRequired()).isTrue();
        assertSection(response.applicationFormRequiredPolicy(), "ATTACHMENT", true, true, ApplicationFormRequirementType.REQUIRED);
    }

    @Test
    void public_detail_exposes_safe_attachment_requirements_and_policy_states() {
        Long requiredId = jobPostingService.create(request(
                "required attachment detail",
                JobPostingType.PUBLIC_RECRUITMENT,
                "required attachment detail summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                false,
                1
        ));
        replaceAttachmentRequirements(requiredId, List.of(
                attachmentRequirement(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, true, 1)
        ));
        jobPostingService.publish(requiredId);

        JobPostingPublicDetailResponse required = jobPostingPublicService.getJobPosting(requiredId);

        assertThat(required.attachmentRequirements()).hasSize(1);
        assertThat(required.attachmentRequirements().get(0).attachmentType()).isEqualTo(AttachmentType.RESUME);
        assertThat(required.attachmentRequirements().get(0).required()).isTrue();
        assertSection(required.applicationFormRequiredPolicy(), "ATTACHMENT", true, true, ApplicationFormRequirementType.REQUIRED);

        Long optionalId = jobPostingService.create(request(
                "optional attachment detail",
                JobPostingType.PUBLIC_RECRUITMENT,
                "optional attachment detail summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                false,
                1
        ));
        replaceAttachmentRequirements(optionalId, List.of(
                attachmentRequirement(AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, false, 0)
        ));
        jobPostingService.publish(optionalId);

        JobPostingPublicDetailResponse optional = jobPostingPublicService.getJobPosting(optionalId);
        assertThat(optional.applicationFormRequiredPolicy().attachmentRequired()).isFalse();
        assertSection(optional.applicationFormRequiredPolicy(), "ATTACHMENT", true, false, ApplicationFormRequirementType.OPTIONAL);
    }

    @Test
    void public_list_empty_page_responds_safely() {
        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void public_detail_returns_displayable_posting_with_expanded_fields() {
        Long id = createPublishedPosting(request(
                "detail",
                JobPostingType.INTERN_RECRUITMENT,
                "detail summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                true,
                1
        ));

        JobPostingPublicDetailResponse response = jobPostingPublicService.getJobPosting(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.title()).isEqualTo("detail");
        assertThat(response.postingType()).isEqualTo(JobPostingType.INTERN_RECRUITMENT);
        assertThat(response.summary()).isEqualTo("detail summary");
        assertThat(response.contentHtml()).isEqualTo("<p>detail</p>");
        assertThat(response.receptionStatus()).isEqualTo(ReceptionStatus.ACCEPTING);
        assertThat(response.accepting()).isTrue();
        assertThat(response.pinned()).isTrue();
        assertThat(response.jobPositions())
                .extracting(position -> position.positionName())
                .containsExactly("Analyst", "Developer");
        assertThat(response.applicationFormConfig().useEducation()).isTrue();
        assertThat(response.applicationFormConfig().requireEducation()).isTrue();
        assertThat(response.applicationFormConfig().useAward()).isTrue();
        assertThat(response.applicationFormConfig().requireAward()).isFalse();
    }

    @Test
    void 공개_상세조회에_이미지_목록이_포함된다() {
        Long id = createPublishedPosting(request(
                "이미지 공개 상세",
                false,
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true
        ));
        jobPostingImageService.addImage(id,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_HEAD), "채용 포스터", 0);

        JobPostingPublicDetailResponse detail = jobPostingPublicService.getJobPosting(id);

        assertThat(detail.images()).hasSize(1);
        assertThat(detail.images().get(0).altText()).isEqualTo("채용 포스터");
    }

    @Test
    void public_detail_and_list_include_active_question_policy_counts() {
        Long id = jobPostingService.create(request(
                "question policy",
                JobPostingType.PUBLIC_RECRUITMENT,
                "question policy summary",
                receptionStart(),
                receptionEnd(),
                displayStart(),
                displayEnd(),
                true,
                false,
                1
        ));
        createQuestion(id, 0, true);
        createQuestion(id, 1, false);
        JobPostingQuestionResponse inactiveQuestion = createQuestion(id, 2, true);
        jobPostingQuestionService.deactivateQuestion(id, inactiveQuestion.questionId());
        jobPostingService.publish(id);

        ApplicationFormRequiredPolicyResponse detailPolicy =
                jobPostingPublicService.getJobPosting(id).applicationFormRequiredPolicy();
        ApplicationFormRequiredPolicyResponse listPolicy = findById(
                jobPostingPublicService.getJobPostings(0, 10),
                id
        ).applicationFormRequiredPolicy();

        assertThat(detailPolicy.requiredQuestionCount()).isEqualTo(1);
        assertThat(detailPolicy.optionalQuestionCount()).isEqualTo(1);
        assertThat(detailPolicy.hasRequiredQuestion()).isTrue();
        assertThat(detailPolicy.requiredSectionCount()).isEqualTo(3);
        assertSection(detailPolicy, "QUESTION", true, true, ApplicationFormRequirementType.REQUIRED);
        assertThat(listPolicy).isEqualTo(detailPolicy);
    }

    @Test
    void public_detail_keeps_closed_reception_accessible_when_displayable() {
        Long id = createPublishedPosting(request(
                "closed reception detail",
                false,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 2, 18, 0),
                displayStart(),
                displayEnd(),
                true
        ));

        JobPostingPublicDetailResponse response = jobPostingPublicService.getJobPosting(id);

        assertThat(response.receptionStatus()).isEqualTo(ReceptionStatus.CLOSED);
        assertThat(response.accepting()).isFalse();
    }

    @Test
    void public_detail_returns_not_found_for_hidden_out_of_display_or_non_published_postings() {
        Long hiddenId = createPublishedPosting(request("hidden", false, receptionStart(), receptionEnd(), displayStart(), displayEnd(), false));
        Long futureDisplayId = createPublishedPosting(request(
                "future display",
                false,
                receptionStart(),
                receptionEnd(),
                LocalDateTime.of(2026, 6, 2, 0, 0),
                displayEnd(),
                true
        ));
        Long pastDisplayId = createPublishedPosting(request(
                "past display",
                false,
                receptionStart(),
                receptionEnd(),
                displayStart(),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                true
        ));
        Long draftId = jobPostingService.create(request("draft", false, receptionStart(), receptionEnd(), displayStart(), displayEnd(), true));
        Long closedStatusId = createPublishedPosting(request("closed status", false, receptionStart(), receptionEnd(), displayStart(), displayEnd(), true));
        jobPostingService.close(closedStatusId);

        assertThatThrownBy(() -> jobPostingPublicService.getJobPosting(hiddenId))
                .isInstanceOf(JobPostingNotFoundException.class);
        assertThatThrownBy(() -> jobPostingPublicService.getJobPosting(futureDisplayId))
                .isInstanceOf(JobPostingNotFoundException.class);
        assertThatThrownBy(() -> jobPostingPublicService.getJobPosting(pastDisplayId))
                .isInstanceOf(JobPostingNotFoundException.class);
        assertThatThrownBy(() -> jobPostingPublicService.getJobPosting(draftId))
                .isInstanceOf(JobPostingNotFoundException.class);
        assertThatThrownBy(() -> jobPostingPublicService.getJobPosting(closedStatusId))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void invalid_public_list_page_request_throws_exception() {
        assertThatThrownBy(() -> jobPostingPublicService.getJobPostings(-1, 10))
                .isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingPublicService.getJobPostings(0, 101))
                .isInstanceOf(InvalidJobPostingException.class);
    }

    private Long createPublishedPosting(JobPostingCreateRequest request) {
        Long id = jobPostingService.create(request);
        jobPostingService.publish(id);
        return id;
    }

    private JobPostingPublicListResponse findById(PageResponse<JobPostingPublicListResponse> response, Long id) {
        return response.content().stream()
                .filter(jobPosting -> jobPosting.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private JobPostingQuestionResponse createQuestion(Long jobPostingId, int sortOrder, boolean required) {
        return jobPostingQuestionService.createQuestion(
                jobPostingId,
                new JobPostingQuestionCreateRequest(
                        null,
                        "Question " + sortOrder,
                        "Helper " + sortOrder,
                        QuestionCategory.GENERAL,
                        QuestionAnswerType.SHORT_TEXT,
                        required,
                        0,
                        500,
                        sortOrder
                )
        );
    }

    private void replaceAttachmentRequirements(Long jobPostingId, List<AttachmentRequirementRequest> requirements) {
        attachmentRequirementService.replaceRequirements(
                jobPostingId,
                new AttachmentRequirementReplaceRequest(requirements)
        );
    }

    private AttachmentRequirementRequest attachmentRequirement(
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            boolean required,
            int minCount
    ) {
        return new AttachmentRequirementRequest(
                attachmentType,
                sectionType,
                required,
                minCount,
                0,
                attachmentType.name(),
                "safe public guidance"
        );
    }

    private void assertSection(
            ApplicationFormRequiredPolicyResponse policy,
            String sectionCode,
            boolean enabled,
            boolean required,
            ApplicationFormRequirementType requirementType
    ) {
        ApplicationFormSectionPolicyResponse section = policy.sections().stream()
                .filter(item -> item.sectionCode().equals(sectionCode))
                .findFirst()
                .orElseThrow();

        assertThat(section.enabled()).isEqualTo(enabled);
        assertThat(section.required()).isEqualTo(required);
        assertThat(section.requirementType()).isEqualTo(requirementType);
    }

    private JobPostingCreateRequest request(
            String title,
            boolean pinned,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            boolean visible
    ) {
        return request(
                title,
                JobPostingType.PUBLIC_RECRUITMENT,
                title + " summary",
                receptionStartDateTime,
                receptionEndDateTime,
                displayStartDateTime,
                displayEndDateTime,
                visible,
                pinned,
                0
        );
    }

    private JobPostingCreateRequest request(
            String title,
            boolean pinned,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            boolean visible,
            int displayOrder
    ) {
        return request(
                title,
                JobPostingType.PUBLIC_RECRUITMENT,
                title + " summary",
                receptionStartDateTime,
                receptionEndDateTime,
                displayStartDateTime,
                displayEndDateTime,
                visible,
                pinned,
                displayOrder
        );
    }

    private JobPostingCreateRequest request(
            String title,
            JobPostingType postingType,
            String summary,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            boolean visible,
            boolean pinned,
            int displayOrder
    ) {
        return request(
                title,
                postingType,
                summary,
                receptionStartDateTime,
                receptionEndDateTime,
                displayStartDateTime,
                displayEndDateTime,
                visible,
                pinned,
                displayOrder,
                new ApplicationFormConfigRequest(true, false, true, false, true, true, false)
        );
    }

    private JobPostingCreateRequest request(
            String title,
            JobPostingType postingType,
            String summary,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            boolean visible,
            boolean pinned,
            int displayOrder,
            ApplicationFormConfigRequest applicationFormConfig
    ) {
        return new JobPostingCreateRequest(
                title,
                postingType,
                summary,
                "<p>detail</p>",
                receptionStartDateTime,
                receptionEndDateTime,
                displayStartDateTime,
                displayEndDateTime,
                visible,
                pinned,
                displayOrder,
                List.of(
                        new JobPositionRequest(
                                "Developer",
                                JobPositionApplicationType.EXPERIENCED,
                                "IT",
                                "Backend Developer",
                                List.of("BUSAN"),
                                EmploymentType.CONTRACT,
                                2
                        ),
                        new JobPositionRequest(
                                "Analyst",
                                JobPositionApplicationType.NEW_GRADUATE,
                                "Research",
                                "Equity Analyst",
                                List.of("SEOUL"),
                                EmploymentType.FULL_TIME,
                                1
                        )
                ),
                applicationFormConfig
        );
    }

    private LocalDateTime receptionStart() {
        return LocalDateTime.of(2026, 6, 1, 9, 0);
    }

    private LocalDateTime receptionEnd() {
        return LocalDateTime.of(2026, 6, 2, 18, 0);
    }

    private LocalDateTime displayStart() {
        return LocalDateTime.of(2026, 5, 25, 9, 0);
    }

    private LocalDateTime displayEnd() {
        return LocalDateTime.of(2026, 7, 1, 18, 0);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
