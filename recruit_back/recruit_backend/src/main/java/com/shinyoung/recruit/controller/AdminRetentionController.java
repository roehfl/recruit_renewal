package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ForcedPurgeRequest;
import com.shinyoung.recruit.dto.request.PurgeExecuteRequest;
import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.request.RetentionHoldCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.request.RetentionScheduleRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.DataSubjectDetailResponse;
import com.shinyoung.recruit.dto.response.DataSubjectSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchResponse;
import com.shinyoung.recruit.dto.response.PurgeReconcileResponse;
import com.shinyoung.recruit.dto.response.RetentionAnchorResponse;
import com.shinyoung.recruit.dto.response.RetentionHoldResponse;
import com.shinyoung.recruit.dto.response.RetentionPolicyResponse;
import com.shinyoung.recruit.dto.response.RetentionScheduleResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.DataSubjectLookupService;
import com.shinyoung.recruit.service.ForcedPurgeService;
import com.shinyoung.recruit.service.PurgeBatchReadService;
import com.shinyoung.recruit.service.PurgeExecutionService;
import com.shinyoung.recruit.service.PurgeReconciliationService;
import com.shinyoung.recruit.service.RetentionAnchorService;
import com.shinyoung.recruit.service.RetentionDryRunService;
import com.shinyoung.recruit.service.RetentionHoldService;
import com.shinyoung.recruit.service.RetentionPolicyService;
import com.shinyoung.recruit.service.RetentionScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final PurgeExecutionService purgeExecutionService;
    private final PurgeReconciliationService purgeReconciliationService;
    private final CurrentEmployeeService currentEmployeeService;
    private final DataSubjectLookupService dataSubjectLookupService;
    private final ForcedPurgeService forcedPurgeService;
    private final RetentionScheduleService retentionScheduleService;

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

    /** 정책 수정 — 전 엔드포인트 GET/POST 정책에 따라 POST(과거 PUT). */
    @PostMapping("/policies/{policyId}")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> updatePolicy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long policyId,
            @Valid @RequestBody RetentionPolicyRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(retentionPolicyService.update(policyId, request, actor)));
    }

    /** 정책 삭제 — 전 엔드포인트 GET/POST 정책에 따라 POST(과거 DELETE). */
    @PostMapping("/policies/{policyId}/delete")
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

    /** release — 행 삭제가 아니라 releasedAt 마킹(증적 보존). 전 엔드포인트 GET/POST 정책에 따라 POST(과거 DELETE). */
    @PostMapping("/holds/{holdId}/release")
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

    /**
     * 비가역 파기 실행(09d-1, ROLE_PRIVACY_ADMIN 전용 matcher). confirm=true 필수,
     * bulk 는 sourceDryRunBatchId·단건은 applicationId — 실행 시 eligibility 재검증.
     */
    @PostMapping("/purge-batches/execute")
    public ResponseEntity<ApiResponse<PurgeBatchDetailResponse>> execute(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PurgeExecuteRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(purgeExecutionService.execute(request, actor)));
    }

    /**
     * PURGE_PENDING 잔여 건의 바이너리 삭제 재처리 sweep(09e, ROLE_PRIVACY_ADMIN 전용 matcher).
     * execute 재실행은 ALREADY_PURGED skip 이라 본 sweep 이 유일한 재처리 경로다.
     */
    @PostMapping("/purge-batches/reconcile")
    public ResponseEntity<ApiResponse<PurgeReconcileResponse>> reconcile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "100") int limit
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(purgeReconciliationService.reconcile(actor, limit)));
    }

    @GetMapping("/purge-batches")
    public ResponseEntity<ApiResponse<PageResponse<PurgeBatchResponse>>> getBatches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(purgeBatchReadService.getBatches(page, size)));
    }

    @GetMapping("/purge-batches/{batchId}")
    public ResponseEntity<ApiResponse<PurgeBatchDetailResponse>> getBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(ApiResponse.success(purgeBatchReadService.getBatch(batchId)));
    }

    // ---- 파기 대상자(정보주체) 조회·강제 파기 ----

    /** 이름·휴대폰·이메일로 파기 대상자 검색(조건 1개 이상 필수, 상한 50건). */
    @GetMapping("/data-subjects")
    public ResponseEntity<ApiResponse<List<DataSubjectSummaryResponse>>> searchDataSubjects(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String email
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dataSubjectLookupService.search(name, phoneNumber, email)));
    }

    /** 지원자 1명의 지원서 목록과 지원서별 적격성 판정. hold 사유 원문은 주지 않는다. */
    @GetMapping("/data-subjects/{applicantId}")
    public ResponseEntity<ApiResponse<DataSubjectDetailResponse>> getDataSubject(@PathVariable Long applicantId) {
        return ResponseEntity.ok(ApiResponse.success(dataSubjectLookupService.getDetail(applicantId)));
    }

    /**
     * 강제 파기(정보주체 삭제 요청). 보존기간과 무관하게 지원자 1명의 모든 지원서와 계정을 파기한다.
     * 보류가 걸려 있으면 400.
     */
    @PostMapping("/purge-batches/force")
    public ResponseEntity<ApiResponse<PurgeBatchDetailResponse>> forcePurge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ForcedPurgeRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(forcedPurgeService.forcePurge(request, actor)));
    }

    // ---- 자동 파기 스케줄 설정 ----

    /** 자동 파기 on/off, 다음 파기 예정일, 마지막 실행 결과. */
    @GetMapping("/schedule")
    public ResponseEntity<ApiResponse<RetentionScheduleResponse>> getSchedule() {
        return ResponseEntity.ok(ApiResponse.success(retentionScheduleService.getSchedule()));
    }

    /** 자동 파기 켜기/끄기. 보존 정책이 없으면 켤 수 없다(켜도 전건 스킵되므로). */
    @PostMapping("/schedule")
    public ResponseEntity<ApiResponse<RetentionScheduleResponse>> updateSchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RetentionScheduleRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                retentionScheduleService.updateEnabled(request.enabled(), actor)));
    }
}
