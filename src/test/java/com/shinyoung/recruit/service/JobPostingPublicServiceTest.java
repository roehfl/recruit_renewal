package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.JobPostingPublicDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingPublicListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
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

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class JobPostingPublicServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingPublicService jobPostingPublicService;

    @Test
    void PUBLISHED_공고는_공개_목록에_노출된다() {
        Long id = createPublishedPosting(currentPeriodRequest("공개 공고"));

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content())
                .extracting(JobPostingPublicListResponse::id)
                .contains(id);
    }

    @Test
    void DRAFT_공고는_공개_목록에_노출되지_않는다() {
        Long id = jobPostingService.create(currentPeriodRequest("임시 공고"));

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content())
                .extracting(JobPostingPublicListResponse::id)
                .doesNotContain(id);
    }

    @Test
    void CLOSED_공고는_공개_목록에_노출되지_않는다() {
        Long id = createPublishedPosting(currentPeriodRequest("마감 공고"));
        jobPostingService.close(id);

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        assertThat(response.content())
                .extracting(JobPostingPublicListResponse::id)
                .doesNotContain(id);
    }

    @Test
    void PUBLISHED_공고_상세_조회_성공() {
        Long id = createPublishedPosting(currentPeriodRequest("상세 공고"));

        JobPostingPublicDetailResponse response = jobPostingPublicService.getJobPosting(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.title()).isEqualTo("상세 공고");
        assertThat(response.accepting()).isTrue();
    }

    @Test
    void DRAFT_공고_상세는_조회할_수_없다() {
        Long id = jobPostingService.create(currentPeriodRequest("임시 상세 공고"));

        assertThatThrownBy(() -> jobPostingPublicService.getJobPosting(id))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void CLOSED_공고_상세는_조회할_수_없다() {
        Long id = createPublishedPosting(currentPeriodRequest("마감 상세 공고"));
        jobPostingService.close(id);

        assertThatThrownBy(() -> jobPostingPublicService.getJobPosting(id))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void 공개_상세_응답에_모집분야와_지원서_항목_설정이_포함된다() {
        Long id = createPublishedPosting(requestWithTwoPositions("상세 포함 공고"));

        JobPostingPublicDetailResponse response = jobPostingPublicService.getJobPosting(id);

        assertThat(response.jobPositions())
                .extracting(position -> position.positionName())
                .containsExactly("리서치", "백엔드");
        assertThat(response.applicationFormConfig().useEducation()).isTrue();
        assertThat(response.applicationFormConfig().useAward()).isTrue();
    }

    @Test
    void 접수기간에_따라_accepting_값을_계산한다() {
        Long acceptingId = createPublishedPosting(currentPeriodRequest("접수중 공고"));
        Long notAcceptingId = createPublishedPosting(futurePeriodRequest("접수예정 공고"));

        PageResponse<JobPostingPublicListResponse> response = jobPostingPublicService.getJobPostings(0, 10);

        JobPostingPublicListResponse accepting = findById(response, acceptingId);
        JobPostingPublicListResponse notAccepting = findById(response, notAcceptingId);

        assertThat(accepting.accepting()).isTrue();
        assertThat(notAccepting.accepting()).isFalse();
    }

    @Test
    void 공개_목록_페이지_요청값이_잘못되면_예외() {
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

    private JobPostingCreateRequest currentPeriodRequest(String title) {
        return new JobPostingCreateRequest(
                title,
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(new JobPositionRequest("백엔드", 2, 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
    }

    private JobPostingCreateRequest futurePeriodRequest(String title) {
        return new JobPostingCreateRequest(
                title,
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 4, 18, 0),
                List.of(new JobPositionRequest("백엔드", 2, 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
    }

    private JobPostingCreateRequest requestWithTwoPositions(String title) {
        return new JobPostingCreateRequest(
                title,
                "<p>상세 내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(
                        new JobPositionRequest("백엔드", 2, 2),
                        new JobPositionRequest("리서치", 1, 1)
                ),
                new ApplicationFormConfigRequest(true, false, true, false, true, true, false)
        );
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
