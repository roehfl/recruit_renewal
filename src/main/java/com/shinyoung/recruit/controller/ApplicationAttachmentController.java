package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.AttachmentReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationAttachmentService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationAttachmentController {

    private final ApplicationAttachmentService applicationAttachmentService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/attachments")
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> getAttachments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<AttachmentResponse> response = applicationAttachmentService.getAttachments(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/attachments")
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> replaceAttachments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody AttachmentReplaceRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        List<AttachmentResponse> response = applicationAttachmentService.replaceAttachments(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
