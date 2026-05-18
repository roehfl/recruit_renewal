package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.StageResultInitializeResponse;
import com.shinyoung.recruit.service.StageResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
