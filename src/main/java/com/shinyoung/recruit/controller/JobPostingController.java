package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.JobPostingDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.service.JobPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings")
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<JobPostingListResponse>>> getJobPostings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.getJobPostings(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobPostingDetailResponse>> getJobPosting(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.getJobPosting(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody JobPostingCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.create(request)));
    }

    @PostMapping("/{id}")
    public ResponseEntity<ApiResponse<Long>> update(@PathVariable Long id, @Valid @RequestBody JobPostingUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.update(id, request)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<Long>> publish(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.publish(id)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<Long>> close(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.close(id)));
    }
}
