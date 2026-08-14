package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.AttachmentRequirementReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.JobPostingAttachmentRequirementResponse;
import com.shinyoung.recruit.service.JobPostingAttachmentRequirementService;
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
@RequestMapping("/admin/job-postings/{jobPostingId}/attachment-requirements")
public class JobPostingAttachmentRequirementController {

    private final JobPostingAttachmentRequirementService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobPostingAttachmentRequirementResponse>>> getRequirements(
            @PathVariable Long jobPostingId
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getRequirements(jobPostingId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<JobPostingAttachmentRequirementResponse>>> replaceRequirements(
            @PathVariable Long jobPostingId,
            @RequestBody(required = false) AttachmentRequirementReplaceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.replaceRequirements(jobPostingId, request)));
    }
}
