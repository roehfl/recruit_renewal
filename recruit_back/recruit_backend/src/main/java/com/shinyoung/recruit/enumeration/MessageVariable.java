package com.shinyoung.recruit.enumeration;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static com.shinyoung.recruit.enumeration.MessageType.APPLICATION_SUBMITTED;
import static com.shinyoung.recruit.enumeration.MessageType.DEADLINE_REMINDER;
import static com.shinyoung.recruit.enumeration.MessageType.FREE;
import static com.shinyoung.recruit.enumeration.MessageType.INTERVIEW_NOTICE;
import static com.shinyoung.recruit.enumeration.MessageType.INTERVIEW_SCHEDULE;
import static com.shinyoung.recruit.enumeration.MessageType.PASSWORD_RESET;
import static com.shinyoung.recruit.enumeration.MessageType.RESULT_ANNOUNCEMENT;
import static com.shinyoung.recruit.enumeration.MessageType.SIGNUP_VERIFICATION;

/**
 * 본문의 #{키} 변수. 키는 관리자가 읽기 쉽게 한글 이름 그대로 쓴다.
 * 종류별 허용 목록의 단일 출처이며 프론트는 변수 카탈로그 API로 받아 쓴다.
 */
public enum MessageVariable {
    NAME("이름", "지원자 이름", EnumSet.allOf(MessageType.class)),
    JOB_POSTING_TITLE("공고명", "공고 제목", EnumSet.of(RESULT_ANNOUNCEMENT, DEADLINE_REMINDER,
            INTERVIEW_SCHEDULE, INTERVIEW_NOTICE, FREE, APPLICATION_SUBMITTED)),
    SITE_URL("채용사이트", "채용 사이트 주소", EnumSet.allOf(MessageType.class)),
    STAGE_NAME("전형명", "전형 이름", EnumSet.of(RESULT_ANNOUNCEMENT, INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    DEADLINE("마감일시", "서류 접수 마감 일시", EnumSet.of(DEADLINE_REMINDER)),
    D_DAY("남은기간", "마감까지 남은 기간(D-n)", EnumSet.of(DEADLINE_REMINDER)),
    INTERVIEW_DATE_TIME("면접일시", "면접 시작 일시", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    ARRIVAL_TIME("도착시각", "면접 도착 시각", EnumSet.of(INTERVIEW_SCHEDULE)),
    INTERVIEW_PLACE("면접장소", "면접 장소·호실", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    INTERVIEW_METHOD("면접방식", "면접 방식", EnumSet.of(INTERVIEW_SCHEDULE)),
    MEETING_URL("접속링크", "온라인 면접 접속 링크", EnumSet.of(INTERVIEW_SCHEDULE, INTERVIEW_NOTICE)),
    GROUP("조", "면접 조", EnumSet.of(INTERVIEW_SCHEDULE)),
    VERIFICATION_CODE("인증번호", "메일 인증번호", EnumSet.of(SIGNUP_VERIFICATION, PASSWORD_RESET)),
    SUBMITTED_AT("제출일시", "지원서 제출 일시", EnumSet.of(APPLICATION_SUBMITTED));

    private final String key;
    private final String label;
    private final Set<MessageType> types;

    MessageVariable(String key, String label, Set<MessageType> types) {
        this.key = key;
        this.label = label;
        this.types = types;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public Set<MessageType> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    public boolean isAllowedFor(MessageType type) {
        return types.contains(type);
    }

    public static Optional<MessageVariable> fromKey(String key) {
        return Arrays.stream(values())
                .filter(variable -> variable.key.equals(key))
                .findFirst();
    }
}
