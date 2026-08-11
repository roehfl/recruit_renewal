package com.shinyoung.recruit.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 공고 단위 일자별 지원 접수 추이 응답(read-only, 집계값만).
 *
 * <p>모집단은 funnel과 같은 기준(제출 이력 보유 = {@code submittedAt != null})이라
 * {@code totalSubmitted}가 funnel {@code population.p}와 일치한다. {@code WITHDRAWN}도 포함한다 —
 * 이 차트는 "접수 추이"이지 "현재 유효 지원자"가 아니기 때문이다.
 *
 * @param from           집계 구간 시작일(공고 접수 시작일)
 * @param to             집계 구간 종료일(공고 접수 종료일과 오늘 중 이른 날)
 * @param totalSubmitted 구간 내 총 제출 건수
 * @param days           구간의 모든 날짜. 제출이 없던 날도 0으로 포함된다.
 */
public record ApplicationDailyResponse(
        Long jobPostingId,
        String jobPostingTitle,
        LocalDate from,
        LocalDate to,
        long totalSubmitted,
        List<ApplicationDailyPointResponse> days
) {
}
