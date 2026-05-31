package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.AdminApplicationEvaluationResponse;
import com.shinyoung.recruit.dto.response.AdminEvaluationItemResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewEvaluationResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.InterviewEvaluationInitializeResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.InterviewEvaluationAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-facing interview evaluation APIs: initialize and read at interview, stage, and application level.
 * All routes live under {@code /admin/**} and are therefore restricted to admin authorities by SecurityConfig.
 */
@RestController
@RequiredArgsConstructor
public class InterviewEvaluationAdminController {

    private final InterviewEvaluationAdminService interviewEvaluationAdminService;
    private final CurrentEmployeeService currentEmployeeService;

    @PostMapping("/admin/interviews/{interviewId:[0-9]+}/evaluations/initialize")
    public ResponseEntity<ApiResponse<InterviewEvaluationInitializeResponse>> initialize(
            @PathVariable Long interviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewEvaluationAdminService.initialize(interviewId)));
    }

    @PostMapping("/admin/interviews/{interviewId:[0-9]+}/evaluations/{evaluationId:[0-9]+}/reopen")
    public ResponseEntity<ApiResponse<AdminEvaluationItemResponse>> reopen(
            @PathVariable Long interviewId,
            @PathVariable Long evaluationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                interviewEvaluationAdminService.reopen(interviewId, evaluationId, actor)
        ));
    }

    @GetMapping("/admin/interviews/{interviewId:[0-9]+}/evaluations")
    public ResponseEntity<ApiResponse<AdminInterviewEvaluationResponse>> getInterviewEvaluations(
            @PathVariable Long interviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewEvaluationAdminService.getInterviewEvaluations(interviewId)
        ));
    }

    @GetMapping("/admin/stages/{stageId:[0-9]+}/interview-evaluations")
    public ResponseEntity<ApiResponse<List<AdminInterviewEvaluationResponse>>> getStageEvaluations(
            @PathVariable Long stageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewEvaluationAdminService.getStageEvaluations(stageId)
        ));
    }

    @GetMapping("/admin/applications/{applicationId:[0-9]+}/interview-evaluations")
    public ResponseEntity<ApiResponse<List<AdminApplicationEvaluationResponse>>> getApplicationEvaluations(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewEvaluationAdminService.getApplicationEvaluations(applicationId)
        ));
    }
}
