package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.JobPostingPublicDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingPublicListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.service.JobPostingPublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/job-postings")
public class JobPostingPublicController {

    private final JobPostingPublicService jobPostingPublicService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<JobPostingPublicListResponse>>> getJobPostings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingPublicService.getJobPostings(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobPostingPublicDetailResponse>> getJobPosting(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingPublicService.getJobPosting(id)));
    }
}
