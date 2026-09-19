package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDate;

/**
 * 발송 이력 검색 조건. null 은 "조건 없음": from·to 가 없으면 종료일 오늘, 시작일 = 종료일 - 29일(최근 30일, 양끝 포함).
 * test null = 실발송+테스트, true = 테스트만, false = 실발송만.
 */
public record MessageHistoryCondition(
        LocalDate from,
        LocalDate to,
        MessageType type,
        Long jobPostingId,
        Boolean test
) {
}
