package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.request.RetentionHoldCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchResponse;
import com.shinyoung.recruit.dto.response.RetentionAnchorResponse;
import com.shinyoung.recruit.dto.response.RetentionHoldResponse;
import com.shinyoung.recruit.dto.response.RetentionPolicyResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.PurgeBatchReadService;
import com.shinyoung.recruit.service.RetentionAnchorService;
import com.shinyoung.recruit.service.RetentionDryRunService;
import com.shinyoung.recruit.service.RetentionHoldService;
import com.shinyoung.recruit.service.RetentionPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Retention 관리 API(Phase 09c). 권한 게이팅은 SecurityConfig 의 narrow matcher(ADR-0007) —
 * write(policies CUD/holds set·release/anchor) = ROLE_PRIVACY_ADMIN, GET = RECRUIT·PRIVACY,
 * dry-run = RECRUIT·PRIVACY. execute 는 09d-1.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/retention")
public class AdminRetentionController {

    private final RetentionPolicyService retentionPolicyService;
    private final RetentionHoldService retentionHoldService;
    private final RetentionAnchorService retentionAnchorService;
    private final RetentionDryRunService retentionDryRunService;
    private final PurgeBatchReadService purgeBatchReadService;
    private final CurrentEmployeeService currentEmployeeService;

    // ---- RetentionPolicy ----

    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<RetentionPolicyResponse>>> getPolicies() {
        return ResponseEntity.ok(ApiResponse.success(retentionPolicyService.getPolicies()));
    }

    @PostMapping("/policies")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> createPolicy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RetentionPolicyRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(retentionPolicyService.create(request, actor)));
    }

    @PutMapping("/policies/{policyId}")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> updatePolicy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long policyId,
            @Valid @RequestBody RetentionPolicyRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(retentionPolicyService.update(policyId, request, actor)));
    }

    @DeleteMapping("/policies/{policyId}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long policyId
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        retentionPolicyService.delete(policyId, actor);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ---- RetentionHold (manual only) ----

    @GetMapping("/holds")
    public ResponseEntity<ApiResponse<List<RetentionHoldResponse>>> getHolds() {
        return ResponseEntity.ok(ApiResponse.success(retentionHoldService.getHolds()));
    }

    @PostMapping("/holds")
    public ResponseEntity<ApiResponse<RetentionHoldResponse>> setHold(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RetentionHoldCreateRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(retentionHoldService.set(request, actor)));
    }

    /** release — 행 삭제가 아니라 releasedAt 마킹(증적 보존). */
    @DeleteMapping("/holds/{holdId}")
    public ResponseEntity<ApiResponse<RetentionHoldResponse>> releaseHold(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long holdId
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(retentionHoldService.release(holdId, actor)));
    }

    // ---- retention anchor (hiringEndedAt 수동 확정) ----

    @PostMapping("/job-postings/{jobPostingId}/anchor")
    public ResponseEntity<ApiResponse<RetentionAnchorResponse>> fixAnchor(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long jobPostingId,
            @Valid @RequestBody RetentionAnchorRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                retentionAnchorService.fixAnchor(jobPostingId, request, actor)));
    }

    // ---- PurgeBatch (dry-run + 조회) ----

    @PostMapping("/purge-batches/dry-run")
    public ResponseEntity<ApiResponse<PurgeBatchDetailResponse>> dryRun(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(retentionDryRunService.dryRun(actor)));
    }

    @GetMapping("/purge-batches")
    public ResponseEntity<ApiResponse<List<PurgeBatchResponse>>> getBatches() {
        return ResponseEntity.ok(ApiResponse.success(purgeBatchReadService.getBatches()));
    }

    @GetMapping("/purge-batches/{batchId}")
    public ResponseEntity<ApiResponse<PurgeBatchDetailResponse>> getBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(ApiResponse.success(purgeBatchReadService.getBatch(batchId)));
    }
}
