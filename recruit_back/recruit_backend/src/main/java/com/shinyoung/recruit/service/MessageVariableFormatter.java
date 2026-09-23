package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 수신자별 #{변수} 값을 계산한다. 종류에 허용된 변수만 MessageVariable 선언 순서로 담는다(설계서 5절). */
@Component
@RequiredArgsConstructor
public class MessageVariableFormatter {

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter INTERVIEW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd(E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageProperties messageProperties;
    private final Clock clock;

    public Map<String, String> format(MessageType type, MessageVariableContext context) {
        Map<String, String> values = new LinkedHashMap<>();
        for (MessageVariable variable : MessageVariable.values()) {
            if (variable.isAllowedFor(type)) {
                values.put(variable.getKey(), valueOf(variable, context));
            }
        }
        return values;
    }

    private String valueOf(MessageVariable variable, MessageVariableContext context) {
        Interview interview = context.interview();
        return switch (variable) {
            case NAME -> Objects.toString(context.name(), "");
            case JOB_POSTING_TITLE -> context.jobPosting().getTitle();
            case SITE_URL -> messageProperties.getSiteUrl();
            case STAGE_NAME -> context.stage() == null ? "" : context.stage().getStageName();
            case DEADLINE -> context.jobPosting().getReceptionEndDateTime().format(DEADLINE_FORMAT);
            case D_DAY -> dDay(context.jobPosting().getReceptionEndDateTime());
            case INTERVIEW_DATE_TIME -> interview == null ? "" : interview.getStartDateTime().format(INTERVIEW_FORMAT);
            case ARRIVAL_TIME -> interview == null || interview.getArrivalDateTime() == null
                    ? "" : interview.getArrivalDateTime().format(TIME_FORMAT);
            case INTERVIEW_PLACE -> interview == null ? "" : place(interview);
            case INTERVIEW_METHOD -> interview == null ? "" : methodLabel(interview.getMethod());
            case MEETING_URL -> interview == null ? "" : Objects.toString(interview.getOnlineMeetingUrl(), "");
            case GROUP -> interview == null ? "" : groupLabel(interview.getGroupName());
            // 시스템 자동발송 전용 변수. 관리자 종류에는 허용되지 않아 여기서 값이 쓰이지 않는다(값은 SystemMailService 호출자가 넣는다).
            case VERIFICATION_CODE, SUBMITTED_AT -> "";
        };
    }

    private String dDay(LocalDateTime deadline) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), deadline.toLocalDate());
        return days <= 0 ? "D-DAY" : "D-" + days;
    }

    private static String place(Interview interview) {
        String location = Objects.toString(interview.getLocationName(), "").trim();
        String room = Objects.toString(interview.getRoomName(), "").trim();
        return (location + " " + room).trim();
    }

    private static String methodLabel(InterviewMethod method) {
        return switch (method) {
            case IN_PERSON -> "대면";
            case ONLINE -> "온라인";
            case HYBRID -> "대면+온라인";
            case OTHER -> "기타";
        };
    }

    static String groupLabel(String groupName) {
        return groupName.matches("\\d+") ? groupName + "조" : groupName;
    }
}
