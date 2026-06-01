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
 * <p>dimension 미지정 → overall funnel만. dimension=POSITION → 분야별 그룹 funnel을 추가로 반환한다.
 * free-text 축(SCHOOL/CERTIFICATE)은 아직 미지원(400). statistics는 집계값만 노출하므로 audit를 남기지 않는다.
 *
 * <p>{@code topN}은 free-text 축(SCHOOL/CERTIFICATE)의 cardinality 제한을 위한 파라미터로, 해당 축
 * 활성화 전까지 동작하지 않으며 <strong>POSITION에서는 무시</strong>된다(전체 분야 반환).
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
