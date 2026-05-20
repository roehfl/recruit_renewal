package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.AttachmentAdminDeleteRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AttachmentDeleteResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationAttachmentDeleteService;
import com.shinyoung.recruit.service.ApplicationAttachmentDownloadService;
import com.shinyoung.recruit.service.AttachmentDownloadResource;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminApplicationAttachmentController {

    private final ApplicationAttachmentDownloadService applicationAttachmentDownloadService;
    private final ApplicationAttachmentDeleteService applicationAttachmentDeleteService;
    private final AttachmentDownloadResponseFactory attachmentDownloadResponseFactory;
    private final CurrentEmployeeService currentEmployeeService;

    @GetMapping("/admin/applications/{applicationId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachmentFile(
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId
    ) {
        AttachmentDownloadResource response = applicationAttachmentDownloadService.downloadForAdmin(
                applicationId,
                attachmentId
        );
        return attachmentDownloadResponseFactory.toResponse(response);
    }

    @PostMapping("/admin/applications/{applicationId}/attachments/{attachmentId}/delete")
    public ResponseEntity<ApiResponse<AttachmentDeleteResponse>> deleteAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId,
            @Valid @RequestBody AttachmentAdminDeleteRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        AttachmentDeleteResponse response = applicationAttachmentDeleteService.deleteForAdmin(
                applicationId,
                attachmentId,
                actor,
                request.reason()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
