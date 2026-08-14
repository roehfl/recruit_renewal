package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationPdfDocument;
import com.shinyoung.recruit.service.ApplicationPdfService;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.ExportAuditContext;
import com.shinyoung.recruit.service.PdfAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 운영자 Application PDF 다운로드(admin 전용). 지원자 1명 = PDF 1개. 식별된 1인의 전체 지원서이므로 생성 시 audit를 남긴다.
 * 응답은 export와 동일한 보안 헤더(no-store/no-cache/nosniff)와 attachment disposition을 적용한다.
 */
@RestController
@RequiredArgsConstructor
public class ApplicationPdfController {

    private final ApplicationPdfService applicationPdfService;
    private final PdfAuditLogger pdfAuditLogger;
    private final CurrentEmployeeService currentEmployeeService;

    @GetMapping("/admin/applications/{applicationId}/pdf")
    public ResponseEntity<byte[]> applicationPdf(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        ApplicationPdfDocument document = applicationPdfService.generate(applicationId);
        pdfAuditLogger.logApplicationPdf(
                auditContext(actor, userDetails, request),
                applicationId,
                document.jobPostingId(),
                document.jobPositionId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ApplicationPdfDocument.CONTENT_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(document.fileName()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(document.content());
    }

    private String contentDisposition(String fileName) {
        String safe = fileName.replace('"', '_');
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
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
                requestId);
    }
}
