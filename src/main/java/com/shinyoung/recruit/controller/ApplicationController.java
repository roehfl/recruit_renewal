package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentApplicantService;
import com.shinyoung.recruit.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final JobApplicationService jobApplicationService;
    private final CurrentApplicantService currentApplicantService;

    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicationCreateRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.create(applicantId, request)));
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> getApplication(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.getApplication(applicantId, applicationId)));
    }

    @PostMapping("/applications/{applicationId}")
    public ResponseEntity<ApiResponse<Long>> updateDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationUpdateRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.updateDraft(applicantId, applicationId, request)));
    }

    @PostMapping("/applications/{applicationId}/submit")
    public ResponseEntity<ApiResponse<Long>> submit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.submit(applicantId, applicationId)));
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    public ResponseEntity<ApiResponse<Long>> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.withdraw(applicantId, applicationId)));
    }

    @GetMapping("/job-postings/{jobPostingId}/application")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> getMyApplicationByJobPosting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long jobPostingId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.getMyApplicationByJobPosting(applicantId, jobPostingId)));
    }
}
