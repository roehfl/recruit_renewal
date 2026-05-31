package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.InterviewEvaluationSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.InterviewerEvaluationDetailResponse;
import com.shinyoung.recruit.dto.response.InterviewerEvaluationListResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.InterviewerEvaluationService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/interviewer/interviews/{interviewId:[0-9]+}/evaluations")
public class InterviewerEvaluationController {

    private final InterviewerEvaluationService interviewerEvaluationService;
    private final CurrentEmployeeService currentEmployeeService;

    @GetMapping
    public ResponseEntity<ApiResponse<InterviewerEvaluationListResponse>> getMyEvaluations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long interviewId
    ) {
        Long employeeId = currentEmployeeService.getCurrentEmployeeId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                interviewerEvaluationService.getMyEvaluations(employeeId, interviewId)
        ));
    }

    @GetMapping("/{evaluationId:[0-9]+}")
    public ResponseEntity<ApiResponse<InterviewerEvaluationDetailResponse>> getMyEvaluationDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long interviewId,
            @PathVariable Long evaluationId
    ) {
        Long employeeId = currentEmployeeService.getCurrentEmployeeId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                interviewerEvaluationService.getMyEvaluationDetail(employeeId, interviewId, evaluationId)
        ));
    }

    @PostMapping("/{evaluationId:[0-9]+}")
    public ResponseEntity<ApiResponse<InterviewerEvaluationDetailResponse>> save(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long interviewId,
            @PathVariable Long evaluationId,
            @Valid @RequestBody InterviewEvaluationSaveRequest request
    ) {
        Long employeeId = currentEmployeeService.getCurrentEmployeeId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                interviewerEvaluationService.save(employeeId, interviewId, evaluationId, request)
        ));
    }

    @PostMapping("/{evaluationId:[0-9]+}/submit")
    public ResponseEntity<ApiResponse<InterviewerEvaluationDetailResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long interviewId,
            @PathVariable Long evaluationId,
            @Valid @RequestBody(required = false) InterviewEvaluationSaveRequest request
    ) {
        Long employeeId = currentEmployeeService.getCurrentEmployeeId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                interviewerEvaluationService.submit(employeeId, interviewId, evaluationId, request)
        ));
    }
}
