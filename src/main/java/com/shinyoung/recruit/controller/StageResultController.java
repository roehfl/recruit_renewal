package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.StageResultBulkUpdateRequest;
import com.shinyoung.recruit.dto.request.StageResultCorrectionRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.StageResultBulkUpdateResponse;
import com.shinyoung.recruit.dto.response.StageResultCorrectionHistoryResponse;
import com.shinyoung.recruit.dto.response.StageResultInitializeResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.StageResultCorrectionService;
import com.shinyoung.recruit.service.StageResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/stages/{stageId}/results")
public class StageResultController {

    private final CurrentEmployeeService currentEmployeeService;
    private final StageResultService stageResultService;
    private final StageResultCorrectionService stageResultCorrectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminStageResultResponse>>> getResults(@PathVariable Long stageId) {
        return ResponseEntity.ok(ApiResponse.success(stageResultService.getResults(stageId)));
    }

    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<StageResultInitializeResponse>> initialize(@PathVariable Long stageId) {
        return ResponseEntity.ok(ApiResponse.success(stageResultService.initialize(stageId)));
    }

    @PostMapping("/{resultId}")
    public ResponseEntity<ApiResponse<AdminStageResultResponse>> updateResult(
            @PathVariable Long stageId,
            @PathVariable Long resultId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody StageResultUpdateRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(stageResultService.updateResult(stageId, resultId, request, actor)));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<StageResultBulkUpdateResponse>> bulkUpdateResults(
            @PathVariable Long stageId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody StageResultBulkUpdateRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(stageResultService.bulkUpdateResults(stageId, request, actor)));
    }

    @PostMapping("/{resultId}/correct")
    public ResponseEntity<ApiResponse<AdminStageResultResponse>> correctResult(
            @PathVariable Long stageId,
            @PathVariable Long resultId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody StageResultCorrectionRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                stageResultCorrectionService.correctResult(stageId, resultId, request, actor)
        ));
    }

    @GetMapping("/{resultId}/histories")
    public ResponseEntity<ApiResponse<List<StageResultCorrectionHistoryResponse>>> getCorrectionHistories(
            @PathVariable Long stageId,
            @PathVariable Long resultId
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageResultCorrectionService.getHistories(stageId, resultId)));
    }
}
