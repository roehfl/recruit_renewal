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
    void arrivalDateTime이_startDateTime보다_이후면_실패한다() {
        assertThatThrownBy(() -> interviewWithArrival(start().plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Interview arrivalDateTime must not be after startDateTime.");
    }

    @Test
    void arrivalDateTime이_null이면_생성된다() {
        Interview interview = interviewWithArrival(null);

        assertThat(interview.getArrivalDateTime()).isNull();
    }

    @Test
    void arrivalDateTime이_startDateTime과_같거나_이전이면_생성된다() {
        assertThat(interviewWithArrival(start()).getArrivalDateTime()).isEqualTo(start());
        assertThat(interviewWithArrival(start().minusMinutes(30)).getArrivalDateTime())
                .isEqualTo(start().minusMinutes(30));
    }

    @Test
    void updateDraft시_arrivalDateTime이_startDateTime보다_이후면_실패한다() {
        Interview interview = interviewWithArrival(null);

        assertThatThrownBy(() -> interview.updateDraft(
                "1조",
                start(),
                start().plusMinutes(1),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Interview arrivalDateTime must not be after startDateTime.");
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
                null,
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
                null,
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
                null,
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
                null,
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
                null,
                method,
                locationName,
                null,
                onlineMeetingUrl,
                null
        );
    }

    private Interview interviewWithArrival(LocalDateTime arrivalDateTime) {
        JobPosting jobPosting = jobPosting();
        return Interview.createDraft(
                jobPosting,
                stage(jobPosting),
                "1조",
                start(),
                arrivalDateTime,
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        );
    }

    private JobPosting jobPosting() {
        return JobPosting.create("공고", "내용", start().minusDays(1), start().plusDays(1));
    }

    private Stage stage(JobPosting jobPosting) {
        return Stage.create(jobPosting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false);
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
