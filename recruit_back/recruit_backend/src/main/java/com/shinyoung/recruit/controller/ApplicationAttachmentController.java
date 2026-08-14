package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.AttachmentReplaceRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AttachmentDeleteResponse;
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationAttachmentFileService;
import com.shinyoung.recruit.service.ApplicationAttachmentService;
import com.shinyoung.recruit.service.ApplicationAttachmentDeleteService;
import com.shinyoung.recruit.service.ApplicationAttachmentDownloadService;
import com.shinyoung.recruit.service.AttachmentDownloadResource;
import com.shinyoung.recruit.service.CurrentApplicantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ApplicationAttachmentController {

    private final ApplicationAttachmentService applicationAttachmentService;
    private final ApplicationAttachmentFileService applicationAttachmentFileService;
    private final ApplicationAttachmentDeleteService applicationAttachmentDeleteService;
    private final ApplicationAttachmentDownloadService applicationAttachmentDownloadService;
    private final AttachmentDownloadResponseFactory attachmentDownloadResponseFactory;
    private final CurrentApplicantService currentApplicantService;

    private static final Set<String> FORBIDDEN_UPLOAD_PARTS = Set.of(
            "sortOrder",
            "displayName",
            "originalFileName",
            "storedFileName",
            "storagePath"
    );

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

    @PostMapping(
            value = "/applications/{applicationId}/attachments/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadAttachmentFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam AttachmentType attachmentType,
            @RequestParam ApplicationSectionType sectionType,
            @RequestParam(required = false) Long sectionRecordId,
            HttpServletRequest request
    ) {
        validateForbiddenUploadParts(request);
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        AttachmentResponse response = applicationAttachmentFileService.upload(
                applicantId,
                applicationId,
                file,
                attachmentType,
                sectionType,
                sectionRecordId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/applications/{applicationId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachmentFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        AttachmentDownloadResource response = applicationAttachmentDownloadService.downloadForApplicant(
                applicantId,
                applicationId,
                attachmentId
        );
        return attachmentDownloadResponseFactory.toResponse(response);
    }

    @PostMapping("/applications/{applicationId}/attachments/{attachmentId}/delete")
    public ResponseEntity<ApiResponse<AttachmentDeleteResponse>> deleteAttachment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        AttachmentDeleteResponse response = applicationAttachmentDeleteService.deleteForApplicant(
                applicantId,
                applicationId,
                attachmentId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private void validateForbiddenUploadParts(HttpServletRequest request) {
        for (String forbiddenPart : FORBIDDEN_UPLOAD_PARTS) {
            if (request.getParameterMap().containsKey(forbiddenPart)) {
                throw new InvalidJobApplicationException(forbiddenPart + " cannot be supplied by client.");
            }
        }
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            for (String forbiddenPart : FORBIDDEN_UPLOAD_PARTS) {
                if (multipartRequest.getFileMap().containsKey(forbiddenPart)) {
                    throw new InvalidJobApplicationException(forbiddenPart + " cannot be supplied by client.");
                }
            }
        }
    }
}
