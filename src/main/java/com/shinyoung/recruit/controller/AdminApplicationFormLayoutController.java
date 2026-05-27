package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormLayoutSaveRequest;
import com.shinyoung.recruit.dto.response.AdminApplicationFormLayoutResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicationFormLayoutPreviewResponse;
import com.shinyoung.recruit.service.ApplicationFormLayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings/{jobPostingId}/application-form-layout")
public class AdminApplicationFormLayoutController {

    private final ApplicationFormLayoutService layoutService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminApplicationFormLayoutResponse>> getLayout(
            @PathVariable Long jobPostingId
    ) {
        return ResponseEntity.ok(ApiResponse.success(layoutService.getLayout(jobPostingId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminApplicationFormLayoutResponse>> saveLayout(
            @PathVariable Long jobPostingId,
            @Valid @RequestBody ApplicationFormLayoutSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(layoutService.saveLayout(jobPostingId, request)));
    }

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<ApplicationFormLayoutPreviewResponse>> getPreview(
            @PathVariable Long jobPostingId
    ) {
        return ResponseEntity.ok(ApiResponse.success(layoutService.getPreview(jobPostingId)));
    }
}
