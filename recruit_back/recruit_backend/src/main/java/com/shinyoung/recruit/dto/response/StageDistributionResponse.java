package com.shinyoung.recruit.dto.response;

/**
 * 한 stage에서 모집단 P 전체를 분류한 raw 7-bucket 분포. 7개 필드 합은 항상 |P|이다.
 *
 * <p>{@code noResult}는 그 stage에 {@code StageResult} row 자체가 없는 P 멤버(미초기화/미도달)로,
 * <strong>응답 전용 synthetic 버킷</strong>이다(DB enum/입력값 아님). row가 있으나 결정 전인
 * {@code pending}과 구분된다. {@code withdrawn}은 그 stage의 {@code StageResult.resultStatus == WITHDRAWN}
 * 수이며, 모집단 요약의 application-level withdrawnCount와 다르다.
 */
public record StageDistributionResponse(
        long passed,
        long failed,
        long absent,
        long hold,
        long pending,
        long withdrawn,
        long noResult
) {
}
