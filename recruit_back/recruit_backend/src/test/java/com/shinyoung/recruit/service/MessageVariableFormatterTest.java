package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageVariableFormatterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JobPosting posting = JobPosting.create("2026 하반기 공채", "content",
            LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
    private final Stage interviewStage = Stage.create(posting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false);

    @Test
    void 결과발표는_공통_변수와_전형명만_담는다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.RESULT_ANNOUNCEMENT, new MessageVariableContext("김민준", posting, interviewStage, null));

        assertThat(values).containsExactly(
                Map.entry("이름", "김민준"),
                Map.entry("공고명", "2026 하반기 공채"),
                Map.entry("채용사이트", "https://recruit.example.co.kr"),
                Map.entry("전형명", "1차 면접")
        );
    }

    @Test
    void 마감임박은_마감일시와_남은기간을_계산한다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 23, 50))
                .format(MessageType.DEADLINE_REMINDER, new MessageVariableContext("김민준", posting, null, null));

        assertThat(values.get("마감일시")).isEqualTo("9월 22일(화) 18:00");
        assertThat(values.get("남은기간")).isEqualTo("D-3");
        assertThat(values).doesNotContainKey("전형명");
    }

    @Test
    void 마감_당일은_D_DAY다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 22, 9, 0))
                .format(MessageType.DEADLINE_REMINDER, new MessageVariableContext("김민준", posting, null, null));

        assertThat(values.get("남은기간")).isEqualTo("D-DAY");
    }

    @Test
    void 면접_일정은_일시_도착_장소_방식_조를_형식화한다() {
        Interview interview = Interview.createDraft(posting, interviewStage, "1",
                LocalDateTime.of(2026, 10, 14, 9, 30), LocalDateTime.of(2026, 10, 14, 9, 10),
                InterviewMethod.IN_PERSON, "본사", "12층 대회의실 A", null, null);

        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.INTERVIEW_SCHEDULE, new MessageVariableContext("김민준", posting, interviewStage, interview));

        assertThat(values.get("면접일시")).isEqualTo("2026-10-14(수) 09:30");
        assertThat(values.get("도착시각")).isEqualTo("09:10");
        assertThat(values.get("면접장소")).isEqualTo("본사 12층 대회의실 A");
        assertThat(values.get("면접방식")).isEqualTo("대면");
        assertThat(values.get("접속링크")).isEmpty();
        assertThat(values.get("조")).isEqualTo("1조");
    }

    @Test
    void 온라인_면접과_이름이_있는_조는_그대로_쓴다() {
        Interview interview = Interview.createDraft(posting, interviewStage, "오전A",
                LocalDateTime.of(2026, 10, 15, 14, 0), null,
                InterviewMethod.ONLINE, null, null, "https://meet.example.com/abc", null);

        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.INTERVIEW_SCHEDULE, new MessageVariableContext("김민준", posting, interviewStage, interview));

        assertThat(values.get("도착시각")).isEmpty();
        assertThat(values.get("면접장소")).isEmpty();
        assertThat(values.get("면접방식")).isEqualTo("온라인");
        assertThat(values.get("접속링크")).isEqualTo("https://meet.example.com/abc");
        assertThat(values.get("조")).isEqualTo("오전A");
    }

    @Test
    void 면접_공지는_허용된_면접_변수만_담는다() {
        Map<String, String> values = formatter(LocalDateTime.of(2026, 9, 19, 10, 0))
                .format(MessageType.INTERVIEW_NOTICE, new MessageVariableContext(null, posting, interviewStage, null));

        assertThat(values).containsOnlyKeys("이름", "공고명", "채용사이트", "전형명", "면접일시", "면접장소", "접속링크");
        assertThat(values.get("이름")).isEmpty();
        assertThat(values.get("면접일시")).isEmpty();
    }

    private MessageVariableFormatter formatter(LocalDateTime now) {
        MessageProperties properties = new MessageProperties();
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new MessageVariableFormatter(properties, clock);
    }
}
