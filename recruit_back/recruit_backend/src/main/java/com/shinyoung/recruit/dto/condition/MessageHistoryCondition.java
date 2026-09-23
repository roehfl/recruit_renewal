package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDate;

/**
 * 발송 이력 검색 조건. null 은 "조건 없음": from·to 가 없으면 종료일 오늘, 시작일 = 종료일 - 29일(최근 30일, 양끝 포함).
 * test null = 실발송+테스트, true = 테스트만, false = 실발송만. origin null = 관리자 발송+시스템 자동발송.
 * jobPostingId 로 거르면 공고 없는 시스템 발송(가입 인증·비밀번호 재설정)은 빠진다.
 */
public record MessageHistoryCondition(
        LocalDate from,
        LocalDate to,
        MessageType type,
        Long jobPostingId,
        Boolean test,
        MessageOrigin origin
) {
}
