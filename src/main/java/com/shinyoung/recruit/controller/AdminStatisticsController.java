package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicationDailyResponse;
import com.shinyoung.recruit.dto.response.FunnelResponse;
import com.shinyoung.recruit.service.ApplicationTrendStatisticsService;
import com.shinyoung.recruit.service.FunnelStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 통계 엔드포인트(read-only, admin 전용). 관리자 대시보드(AdminHomeView)의 데이터 공급원이며
 * 집계 단위는 공고 1건이다. statistics는 집계값만 노출하므로 audit를 남기지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final FunnelStatisticsService funnelStatisticsService;
    private final ApplicationTrendStatisticsService applicationTrendStatisticsService;

    /**
     * 공고 단위 전형 funnel. 최상위는 항상 overall이고, 요청한 dimension 축의 그룹 funnel이 함께 담긴다.
     *
     * <p>{@code dimension}은 <strong>콤마 구분 다중 값</strong>을 허용한다(예:
     * {@code POSITION,SCHOOL,CERTIFICATE}). 대시보드가 축마다 따로 호출하면 같은 코호트를 축 개수만큼 다시
     * 읽으므로, 한 번에 받아 로드를 1회로 줄인다. 잘못된 dimension 값이 하나라도 섞이면 400이다.
     *
     * <p>결과는 {@code dimensionGroups}에 축 단위로 담긴다. 응답의 {@code dimension}/{@code dimensions}는
     * 단일 축 요청에서만 채워지는 <strong>하위호환 필드</strong>이므로 신규 소비자는 쓰지 않는다.
     *
     * <p>{@code topN}은 free-text 축 cardinality 제한용으로 <strong>SCHOOL/CERTIFICATE에 공통 적용</strong>
     * (기본 10, 초과는 '기타')되며, <strong>POSITION에서는 무시</strong>된다(전체 분야 반환).
     */
    @GetMapping("/admin/job-postings/{jobPostingId}/statistics/funnel")
    public ResponseEntity<ApiResponse<FunnelResponse>> getFunnel(
            @PathVariable Long jobPostingId,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) Integer topN
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                funnelStatisticsService.getFunnel(jobPostingId, dimension, topN)));
    }

    /**
     * 공고 단위 일자별 지원 접수 추이. 구간은 접수 시작일 ~ min(접수 종료일, 오늘)이며 제출이 없던 날도
     * 0으로 채워 반환한다. 모집단은 funnel과 같은 기준(제출 이력 보유)이라 총계가 funnel의 P와 일치한다.
     */
    @GetMapping("/admin/job-postings/{jobPostingId}/statistics/applications-daily")
    public ResponseEntity<ApiResponse<ApplicationDailyResponse>> getApplicationsDaily(
            @PathVariable Long jobPostingId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                applicationTrendStatisticsService.getDailySubmissions(jobPostingId)));
    }
}
