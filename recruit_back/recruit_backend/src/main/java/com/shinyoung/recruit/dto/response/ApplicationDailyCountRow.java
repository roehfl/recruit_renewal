package com.shinyoung.recruit.dto.response;

import java.time.LocalDate;

/**
 * 일자별 지원 접수 추이 내부 projection: 제출일 하루의 건수. 응답 DTO가 아니라 집계 입력이며,
 * JPA 생성자 표현식으로 직접 조회된다. 결과 행 수는 제출이 있었던 날짜 수만큼이라 작다.
 */
public record ApplicationDailyCountRow(
        LocalDate date,
        Long submittedCount
) {
}
