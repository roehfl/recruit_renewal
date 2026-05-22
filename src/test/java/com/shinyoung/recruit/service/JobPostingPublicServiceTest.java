package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.JobPostingPublicDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingPublicListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;
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
        assertThat(response.positions().get(0).workLocation()).isEqualTo("Seoul");
        assertThat(response.positions().get(0).employmentType()).isEqualTo(EmploymentType.FULL_TIME);
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
        assertThat(response.applicationFormConfig().useAward()).isTrue();
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
                                "Busan",
                                EmploymentType.CONTRACT,
                                2,
                                2
                        ),
                        new JobPositionRequest(
                                "Analyst",
                                JobPositionApplicationType.NEW_GRADUATE,
                                "Research",
                                "Equity Analyst",
                                "Seoul",
                                EmploymentType.FULL_TIME,
                                1,
                                1
                        )
                ),
                new ApplicationFormConfigRequest(true, false, true, false, true, true, false)
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
