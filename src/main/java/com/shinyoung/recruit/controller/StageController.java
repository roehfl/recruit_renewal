package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageReorderRequest;
import com.shinyoung.recruit.dto.request.StageUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.StageDetailResponse;
import com.shinyoung.recruit.dto.response.StageListResponse;
import com.shinyoung.recruit.service.StageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings/{jobPostingId}/stages")
public class StageController {

    private final StageService stageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StageListResponse>>> getStages(@PathVariable Long jobPostingId) {
        return ResponseEntity.ok(ApiResponse.success(stageService.getStages(jobPostingId)));
    }

    @GetMapping("/{stageId}")
    public ResponseEntity<ApiResponse<StageDetailResponse>> getStage(
            @PathVariable Long jobPostingId,
            @PathVariable Long stageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.getStage(jobPostingId, stageId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(
            @PathVariable Long jobPostingId,
            @Valid @RequestBody StageCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.create(jobPostingId, request)));
    }

    @PostMapping("/{stageId}")
    public ResponseEntity<ApiResponse<Long>> update(
            @PathVariable Long jobPostingId,
            @PathVariable Long stageId,
            @Valid @RequestBody StageUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.update(jobPostingId, stageId, request)));
    }

    @PostMapping("/reorder")
    public ResponseEntity<ApiResponse<List<StageListResponse>>> reorder(
            @PathVariable Long jobPostingId,
            @Valid @RequestBody StageReorderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.reorder(jobPostingId, request)));
    }

    @PostMapping("/{stageId}/start")
    public ResponseEntity<ApiResponse<Long>> start(
            @PathVariable Long jobPostingId,
            @PathVariable Long stageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.start(jobPostingId, stageId)));
    }

    @PostMapping("/{stageId}/announce")
    public ResponseEntity<ApiResponse<Long>> announce(
            @PathVariable Long jobPostingId,
            @PathVariable Long stageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.announce(jobPostingId, stageId)));
    }

    @PostMapping("/{stageId}/close")
    public ResponseEntity<ApiResponse<Long>> close(
            @PathVariable Long jobPostingId,
            @PathVariable Long stageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.close(jobPostingId, stageId)));
    }

    @PostMapping("/{stageId}/delete")
    public ResponseEntity<ApiResponse<Long>> delete(
            @PathVariable Long jobPostingId,
            @PathVariable Long stageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(stageService.delete(jobPostingId, stageId)));
    }
}
