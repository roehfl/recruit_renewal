package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.FunnelResponse;
import com.shinyoung.recruit.service.FunnelStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 통계 엔드포인트(read-only, admin 전용). Phase 07c는 공고 단위 전형 funnel을 담당한다.
 *
 * <p>dimension 미지정 → overall funnel만. POSITION → 분야별, SCHOOL → 학교별(08d, schoolId 최종학력 매칭 + 미매칭/초과='기타'),
 * CERTIFICATE → 자격별(08e, 자격명 정규화 보유 지원자 distinct + topN/'기타')을 추가로 반환한다. 잘못된 dimension 값은 400.
 * statistics는 집계값만 노출하므로 audit를 남기지 않는다.
 *
 * <p>{@code topN}은 free-text 축 cardinality 제한용으로 <strong>SCHOOL/CERTIFICATE에서 적용</strong>(기본 10, 초과는 '기타')되며,
 * <strong>POSITION에서는 무시</strong>된다(전체 분야 반환).
 */
@RestController
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final FunnelStatisticsService funnelStatisticsService;

    @GetMapping("/admin/job-postings/{jobPostingId}/statistics/funnel")
    public ResponseEntity<ApiResponse<FunnelResponse>> getFunnel(
            @PathVariable Long jobPostingId,
            @RequestParam(required = false) String dimension,
            // topN: free-text 축 대비 파라미터. POSITION에서는 무시(SCHOOL/CERTIFICATE 활성화 전까지 미동작).
            @RequestParam(required = false) Integer topN
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                funnelStatisticsService.getFunnel(jobPostingId, dimension, topN)));
    }
}
