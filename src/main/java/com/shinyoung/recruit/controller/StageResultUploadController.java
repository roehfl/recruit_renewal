package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.StageResultUploadCommitResponse;
import com.shinyoung.recruit.dto.response.StageResultUploadPreviewResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.ExcelExportFile;
import com.shinyoung.recruit.service.ExportAuditContext;
import com.shinyoung.recruit.service.ExportAuditLogger;
import com.shinyoung.recruit.service.StageResultUploadService;
import com.shinyoung.recruit.service.UploadAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 운영자 Excel upload(StageResult) 엔드포인트(admin 전용). Phase 07d는 Phase 07의 유일한 쓰기 경로다.
 *
 * <p>upload-template으로 받은 xlsx만 소스로 허용한다(applications/stage results export는 소스 아님).
 * preview는 검증·diff만(영속 변경 없음), commit은 재검증 후 all-or-nothing 적용(오류/STALE 1건이라도 있으면 전체 거부).
 * commit은 기존 {@code StageResultService.bulkUpdateResults}에 위임해 기존 불변식/정정 이력/audit을 상속한다.
 */
@RestController
@RequiredArgsConstructor
public class StageResultUploadController {

    private final StageResultUploadService uploadService;
    private final ExcelExportResponseFactory excelExportResponseFactory;
    private final ExportAuditLogger exportAuditLogger;
    private final UploadAuditLogger uploadAuditLogger;
    private final CurrentEmployeeService currentEmployeeService;

    @GetMapping("/admin/stages/{stageId}/results/upload-template")
    public ResponseEntity<StreamingResponseBody> uploadTemplate(
            @PathVariable Long stageId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        ExcelExportFile file = uploadService.generateTemplate(stageId);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("stageId", stageId);
        exportAuditLogger.logExport(
                "STAGE_RESULT_UPLOAD_TEMPLATE", auditContext(actor, userDetails, request), filters, file);
        return excelExportResponseFactory.toResponse(file);
    }

    @PostMapping("/admin/stages/{stageId}/results/upload/preview")
    public ResponseEntity<ApiResponse<StageResultUploadPreviewResponse>> previewUpload(
            @PathVariable Long stageId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.preview(stageId, file)));
    }

    @PostMapping("/admin/stages/{stageId}/results/upload/commit")
    public ResponseEntity<ApiResponse<StageResultUploadCommitResponse>> commitUpload(
            @PathVariable Long stageId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        StageResultUploadCommitResponse response = uploadService.commit(stageId, file, actor);
        uploadAuditLogger.logUploadCommit(auditContext(actor, userDetails, request), stageId, response, file);
        return switch (response.outcome()) {
            case APPLIED -> ResponseEntity.ok(ApiResponse.success(response));
            case REJECTED_VALIDATION -> ResponseEntity.badRequest()
                    .body(ApiResponse.fail("업로드 검증에 실패하여 적용하지 않았습니다.", response));
            case REJECTED_STALE -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail("다른 사용자가 변경하여 업로드를 적용하지 않았습니다.", response));
        };
    }

    private ExportAuditContext auditContext(String actor, CustomUserDetails userDetails, HttpServletRequest request) {
        String authority = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return new ExportAuditContext(
                actor,
                authority,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                requestId
        );
    }
}
