package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.AdminApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationSummaryResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminApplicationController {

    private final JobApplicationService jobApplicationService;

    @GetMapping("/admin/applications")
    public ResponseEntity<ApiResponse<PageResponse<AdminApplicationSummaryResponse>>> getApplications(
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Long jobPositionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.getApplicationsForAdmin(
                jobPostingId,
                jobPositionId,
                status,
                page,
                size
        )));
    }

    @GetMapping("/admin/applications/{applicationId}")
    public ResponseEntity<ApiResponse<AdminApplicationDetailResponse>> getApplication(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.getApplicationForAdmin(applicationId)));
    }

    @GetMapping("/admin/job-postings/{jobPostingId}/applications")
    public ResponseEntity<ApiResponse<PageResponse<AdminApplicationSummaryResponse>>> getApplicationsByJobPosting(
            @PathVariable Long jobPostingId,
            @RequestParam(required = false) Long jobPositionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobApplicationService.getApplicationsByJobPostingForAdmin(
                jobPostingId,
                jobPositionId,
                status,
                page,
                size
        )));
    }
}
