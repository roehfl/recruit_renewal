package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.AdminApplicationFormSummarySearchRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormLayoutSaveRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.response.AdminApplicationFormSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ApplicationFormConfigState;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.enumeration.ReceptionStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@Transactional
class AdminApplicationFormSummaryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T10:00:00Z"),
            ZoneId.of("UTC")
    );

    /** 고정 시계 기준 접수 시작 전 — 편집 가능. */
    private static final LocalDateTime FUTURE_START = LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime FUTURE_END = LocalDateTime.of(2026, 7, 30, 18, 0);

    /** 고정 시계 기준 접수 중 — 편집 불가. */
    private static final LocalDateTime STARTED_START = LocalDateTime.of(2026, 6, 1, 9, 0);
    private static final LocalDateTime STARTED_END = LocalDateTime.of(2026, 6, 30, 18, 0);

    private static final AdminApplicationFormSummarySearchRequest NO_FILTER =
            new AdminApplicationFormSummarySearchRequest(null, null, null, null, null);

    @Autowired
    private AdminApplicationFormSummaryService summaryService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicationFormLayoutService applicationFormLayoutService;

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    @Test
    void 레이아웃을_저장하지_않으면_DEFAULT() {
        Long id = createPosting("기본 레이아웃 공고", FUTURE_START, FUTURE_END);

        AdminApplicationFormSummaryResponse summary = findById(summaryService.getSummaries(NO_FILTER, 0, 20), id);

        assertThat(summary.configState()).isEqualTo(ApplicationFormConfigState.DEFAULT);
        assertThat(summary.layoutStored()).isFalse();
        assertThat(summary.pageCount()).isZero();
        assertThat(summary.editable()).isTrue();
        assertThat(summary.receptionStatus()).isEqualTo(ReceptionStatus.UPCOMING);
    }

    @Test
    void 레이아웃이_활성_섹션과_일치하면_OK() {
        Long id = createPosting("정상 공고", FUTURE_START, FUTURE_END);
        applicationFormLayoutService.saveLayout(id, buildLayoutRequest(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER,
                ApplicationSectionType.MILITARY
        ));

        AdminApplicationFormSummaryResponse summary = findById(summaryService.getSummaries(NO_FILTER, 0, 20), id);

        assertThat(summary.configState()).isEqualTo(ApplicationFormConfigState.OK);
        assertThat(summary.layoutStored()).isTrue();
        assertThat(summary.pageCount()).isEqualTo(1);
        assertThat(summary.sectionSummary().enabledCount()).isEqualTo(4);
    }

    @Test
    void 레이아웃_저장_후_질문을_추가하면_RELAYOUT_REQUIRED() {
        Long id = createPosting("질문 추가 공고", FUTURE_START, FUTURE_END);
        applicationFormLayoutService.saveLayout(id, buildLayoutRequest(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER,
                ApplicationSectionType.MILITARY
        ));
        addQuestion(id);

        AdminApplicationFormSummaryResponse summary = findById(summaryService.getSummaries(NO_FILTER, 0, 20), id);

        assertThat(summary.configState()).isEqualTo(ApplicationFormConfigState.RELAYOUT_REQUIRED);
        assertThat(summary.activeQuestionCount()).isEqualTo(1);
        assertThat(summary.requiredQuestionCount()).isEqualTo(1);
    }

    @Test
    void 설정_상태로_필터링한다() {
        Long okId = createPosting("정상 공고", FUTURE_START, FUTURE_END);
        applicationFormLayoutService.saveLayout(okId, buildLayoutRequest(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER,
                ApplicationSectionType.MILITARY
        ));
        createPosting("기본 레이아웃 공고", FUTURE_START, FUTURE_END);

        PageResponse<AdminApplicationFormSummaryResponse> result = summaryService.getSummaries(
                new AdminApplicationFormSummarySearchRequest(null, null, ApplicationFormConfigState.OK, null, null),
                0,
                20
        );

        assertThat(result.content()).extracting(AdminApplicationFormSummaryResponse::configState)
                .containsOnly(ApplicationFormConfigState.OK);
        assertThat(result.content()).extracting(AdminApplicationFormSummaryResponse::jobPostingId).contains(okId);
    }

    @Test
    void 편집_가능한_공고만_볼_수_있다() {
        Long editableId = createPosting("접수 전 공고", FUTURE_START, FUTURE_END);
        Long startedId = createPosting("접수 중 공고", STARTED_START, STARTED_END);
        // DRAFT 는 접수일과 무관하게 편집 가능하므로, 잠기려면 게시되어 있어야 한다.
        jobPostingService.publish(startedId);

        PageResponse<AdminApplicationFormSummaryResponse> result = summaryService.getSummaries(
                new AdminApplicationFormSummarySearchRequest(null, null, null, true, null),
                0,
                20
        );

        List<Long> ids = result.content().stream().map(AdminApplicationFormSummaryResponse::jobPostingId).toList();
        assertThat(ids).contains(editableId).doesNotContain(startedId);
    }

    @Test
    void 제목으로_검색한다() {
        Long id = createPosting("2026 상반기 신입공채", FUTURE_START, FUTURE_END);
        createPosting("2026 경력 수시채용", FUTURE_START, FUTURE_END);

        PageResponse<AdminApplicationFormSummaryResponse> result = summaryService.getSummaries(
                new AdminApplicationFormSummarySearchRequest(null, null, null, null, "신입"),
                0,
                20
        );

        assertThat(result.content()).extracting(AdminApplicationFormSummaryResponse::jobPostingId).containsExactly(id);
    }

    @Test
    void 재배치가_필요한_공고를_먼저_보여준다() {
        Long okId = createPosting("정상 공고", FUTURE_START, FUTURE_END);
        applicationFormLayoutService.saveLayout(okId, buildLayoutRequest(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER,
                ApplicationSectionType.MILITARY
        ));
        Long relayoutId = createPosting("재배치 필요 공고", FUTURE_START, FUTURE_END);
        applicationFormLayoutService.saveLayout(relayoutId, buildLayoutRequest(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER,
                ApplicationSectionType.MILITARY
        ));
        addQuestion(relayoutId);

        PageResponse<AdminApplicationFormSummaryResponse> result = summaryService.getSummaries(NO_FILTER, 0, 20);

        List<Long> ids = result.content().stream().map(AdminApplicationFormSummaryResponse::jobPostingId).toList();
        assertThat(ids.indexOf(relayoutId)).isLessThan(ids.indexOf(okId));
    }

    @Test
    void 페이지_요청값이_잘못되면_예외() {
        assertThatThrownBy(() -> summaryService.getSummaries(NO_FILTER, -1, 20))
                .isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> summaryService.getSummaries(NO_FILTER, 0, 101))
                .isInstanceOf(InvalidJobPostingException.class);
    }

    private Long createPosting(String title, LocalDateTime receptionStart, LocalDateTime receptionEnd) {
        return jobPostingService.create(new JobPostingCreateRequest(
                title,
                "<p>내용</p>",
                receptionStart,
                receptionEnd,
                List.of(new JobPositionRequest("백엔드", 0)),
                // 학력·경력·병역만 사용해 활성 섹션을 4개(기본정보 포함)로 고정한다.
                new ApplicationFormConfigRequest(true, true, false, false, true, false, false)
        ));
    }

    private void addQuestion(Long jobPostingId) {
        jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "지원 동기를 작성해 주세요.",
                null,
                QuestionCategory.SELF_INTRODUCTION,
                QuestionAnswerType.LONG_TEXT,
                true,
                null,
                1000,
                0
        ));
    }

    private ApplicationFormLayoutSaveRequest buildLayoutRequest(ApplicationSectionType... sections) {
        List<ApplicationFormLayoutSaveRequest.ItemRequest> items = new ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            items.add(new ApplicationFormLayoutSaveRequest.ItemRequest(sections[i], i));
        }
        return new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Page 1", null, 0, items)
        ));
    }

    private AdminApplicationFormSummaryResponse findById(
            PageResponse<AdminApplicationFormSummaryResponse> result,
            Long jobPostingId
    ) {
        return result.content().stream()
                .filter(summary -> summary.jobPostingId().equals(jobPostingId))
                .findFirst()
                .orElseThrow();
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
