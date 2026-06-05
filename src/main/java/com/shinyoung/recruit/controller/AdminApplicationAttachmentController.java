package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.AttachmentAdminDeleteRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AttachmentDeleteResponse;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ActivityLogService;
import com.shinyoung.recruit.service.ApplicationAttachmentDeleteService;
import com.shinyoung.recruit.service.ApplicationAttachmentDownloadService;
import com.shinyoung.recruit.service.AttachmentDownloadResource;
import com.shinyoung.recruit.service.AuditEvent;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AdminApplicationAttachmentController {

    private final ApplicationAttachmentDownloadService applicationAttachmentDownloadService;
    private final ApplicationAttachmentDeleteService applicationAttachmentDeleteService;
    private final AttachmentDownloadResponseFactory attachmentDownloadResponseFactory;
    private final CurrentEmployeeService currentEmployeeService;
    private final ActivityLogService activityLogService;

    @GetMapping("/admin/applications/{applicationId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachmentFile(
            @PathVariable Long applicationId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        AttachmentDownloadResource response = applicationAttachmentDownloadService.downloadForAdmin(
                applicationId,
                attachmentId
        );
        // 정보 반출(admin download) fail-close(ADR-0006 / Phase 09b): 감사 commit 성공 전에는 바이너리가 나가지 않는다.
        activityLogService.recordRequiresNew(AuditEvent.builder()
                .actorType(ActorType.EMPLOYEE)
                .actorId(actor)
                .actorRoleSnapshot(authoritySnapshot(userDetails))
                .actionType(AuditActionType.ATTACHMENT_ADMIN_DOWNLOAD)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.APPLICATION_ATTACHMENT)
                .targetId(String.valueOf(attachmentId))
                .applicationId(applicationId)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .build());
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

    private String authoritySnapshot(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }
}
