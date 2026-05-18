package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.StageResultBulkUpdateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.StageResultBulkUpdateResponse;
import com.shinyoung.recruit.dto.response.StageResultInitializeResponse;
import com.shinyoung.recruit.service.StageResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    private final StageResultService stageResultService;

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
            @Valid @RequestBody StageResultUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageResultService.updateResult(stageId, resultId, request)));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<StageResultBulkUpdateResponse>> bulkUpdateResults(
            @PathVariable Long stageId,
            @Valid @RequestBody StageResultBulkUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageResultService.bulkUpdateResults(stageId, request)));
    }
}
