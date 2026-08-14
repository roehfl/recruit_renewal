package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewTest {

    @Test
    void 정상_draft_생성시_status가_DRAFT다() {
        Interview interview = interview(InterviewMethod.IN_PERSON, "본사", null);

        assertThat(interview.getStatus()).isEqualTo(InterviewStatus.DRAFT);
        assertThat(interview.isDraft()).isTrue();
    }

    @Test
    void endDateTime이_startDateTime보다_이후가_아니면_실패한다() {
        JobPosting jobPosting = jobPosting();
        Stage stage = stage(jobPosting);
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);

        assertThatThrownBy(() -> Interview.createDraft(
                jobPosting,
                stage,
                "1조",
                start,
                start,
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groupName이_blank면_실패한다() {
        assertThatThrownBy(() -> interview(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jobPosting이_null이면_실패한다() {
        Stage stage = stage(jobPosting());

        assertThatThrownBy(() -> Interview.createDraft(
                null,
                stage,
                "1조",
                start(),
                end(),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stage가_null이면_실패한다() {
        assertThatThrownBy(() -> Interview.createDraft(
                jobPosting(),
                null,
                "1조",
                start(),
                end(),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void method가_null이면_실패한다() {
        JobPosting jobPosting = jobPosting();

        assertThatThrownBy(() -> Interview.createDraft(
                jobPosting,
                stage(jobPosting),
                "1조",
                start(),
                end(),
                null,
                "본사",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void IN_PERSON인데_locationName이_blank면_실패한다() {
        assertThatThrownBy(() -> interview(InterviewMethod.IN_PERSON, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ONLINE인데_onlineMeetingUrl이_blank면_실패한다() {
        assertThatThrownBy(() -> interview(InterviewMethod.ONLINE, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void HYBRID인데_locationName이_blank면_실패한다() {
        assertThatThrownBy(() -> interview(InterviewMethod.HYBRID, " ", "https://meeting.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void HYBRID인데_onlineMeetingUrl이_blank면_실패한다() {
        assertThatThrownBy(() -> interview(InterviewMethod.HYBRID, "본사", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void OTHER는_location_url이_없어도_생성_가능하다() {
        Interview interview = interview(InterviewMethod.OTHER, null, null);

        assertThat(interview.getMethod()).isEqualTo(InterviewMethod.OTHER);
    }

    @Test
    void cancel_호출시_status가_CANCELLED가_된다() {
        Interview interview = interview(InterviewMethod.IN_PERSON, "본사", null);

        interview.cancel();

        assertThat(interview.getStatus()).isEqualTo(InterviewStatus.CANCELLED);
        assertThat(interview.isCancelled()).isTrue();
    }

    private Interview interview(String groupName) {
        JobPosting jobPosting = jobPosting();
        return Interview.createDraft(
                jobPosting,
                stage(jobPosting),
                groupName,
                start(),
                end(),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        );
    }

    private Interview interview(InterviewMethod method, String locationName, String onlineMeetingUrl) {
        JobPosting jobPosting = jobPosting();
        return Interview.createDraft(
                jobPosting,
                stage(jobPosting),
                "1조",
                start(),
                end(),
                method,
                locationName,
                null,
                onlineMeetingUrl,
                null
        );
    }

    private JobPosting jobPosting() {
        return JobPosting.create("공고", "내용", start().minusDays(1), end().plusDays(1));
    }

    private Stage stage(JobPosting jobPosting) {
        return Stage.create(jobPosting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false);
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }

    private LocalDateTime end() {
        return LocalDateTime.of(2026, 6, 1, 11, 0);
    }
}
