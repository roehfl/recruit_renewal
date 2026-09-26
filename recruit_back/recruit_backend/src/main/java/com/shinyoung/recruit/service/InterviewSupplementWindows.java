package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewSupplementWindow;
import com.shinyoung.recruit.domain.entity.JobApplication;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 추가사항 입력 가능 시간 계산(관리자·지원자 서비스 공용). 판정 기준 시각은 호출자가 주입 Clock 으로 넘긴다.
 */
final class InterviewSupplementWindows {

    static final long DEFAULT_WINDOW_HOURS = 2;

    private InterviewSupplementWindows() {
    }

    record Range(LocalDateTime start, LocalDateTime end) {

        /** 시작 포함, 종료 제외. 종료 시각이 되면 바로 닫힌다(유예 없음). */
        boolean isOpen(LocalDateTime now) {
            return !now.isBefore(start) && now.isBefore(end);
        }

        long remainingSeconds(LocalDateTime now) {
            return Math.max(0, Duration.between(now, end).getSeconds());
        }
    }

    /** 조의 도착시간 ~ +2시간. 도착시간이 없으면 기본값을 만들 수 없다(null). */
    static Range defaultRange(Interview interview) {
        LocalDateTime arrival = interview.getArrivalDateTime();
        return arrival == null ? null : new Range(arrival, arrival.plusHours(DEFAULT_WINDOW_HOURS));
    }

    static Range resolve(InterviewSupplementWindow override, Interview interview) {
        if (override != null) {
            return new Range(override.getStartDateTime(), override.getEndDateTime());
        }
        return defaultRange(interview);
    }

    static String applicantName(JobApplication application) {
        String snapshot = application.getApplicantNameSnapshot();
        return snapshot != null ? snapshot : application.getApplicant().getName();
    }
}
