package com.shinyoung.recruit.dto.response;

import java.time.LocalDate;

/**
 * 일자별 지원 접수 추이의 한 점.
 *
 * @param date            제출일
 * @param submittedCount  그날 제출 건수. 제출이 없던 날도 0으로 채워 라인 차트가 끊기지 않게 한다.
 * @param cumulativeCount 구간 시작일부터의 누적 제출 건수(단조 증가)
 */
public record ApplicationDailyPointResponse(
        LocalDate date,
        long submittedCount,
        long cumulativeCount
) {
}
