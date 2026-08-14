package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicationDashboardResponse;
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.ApplicationFormPageResponse;
import com.shinyoung.recruit.dto.response.MyApplicationResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationDashboardService;
import com.shinyoung.recruit.service.ApplicationFormPageService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import com.shinyoung.recruit.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final JobApplicationService jobApplicationService;
    private final ApplicationDashboardService applicationDashboardService;
    private final ApplicationFormPageService applicationFormPageService;
    private final CurrentApplicantService currentApplicantService;

    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicationCreateRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.create(applicantId, request)));
    }

    @GetMapping("/applications/{applicationId:[0-9]+}")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> getApplication(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.getApplication(applicantId, applicationId)));
    }

    @GetMapping("/applications/me")
    public ResponseEntity<ApiResponse<PageResponse<MyApplicationResponse>>> getMyApplications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.getMyApplications(applicantId, page, size)));
    }

    @GetMapping("/applications/{applicationId:[0-9]+}/dashboard")
    public ResponseEntity<ApiResponse<ApplicationDashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(applicationDashboardService.getDashboard(applicantId, applicationId)));
    }

    @GetMapping("/applications/{applicationId:[0-9]+}/form-page")
    public ResponseEntity<ApiResponse<ApplicationFormPageResponse>> getFormPage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(applicationFormPageService.getFormPage(applicantId, applicationId)));
    }

    @PostMapping("/applications/{applicationId:[0-9]+}")
    public ResponseEntity<ApiResponse<Long>> updateDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationUpdateRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.updateDraft(applicantId, applicationId, request)));
    }

    @PostMapping("/applications/{applicationId:[0-9]+}/submit")
    public ResponseEntity<ApiResponse<Long>> submit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.submit(applicantId, applicationId)));
    }

    @PostMapping("/applications/{applicationId:[0-9]+}/withdraw")
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
