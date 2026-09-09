package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationPdfBulkRequest;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationPdfBulkService;
import com.shinyoung.recruit.service.ApplicationPdfDocument;
import com.shinyoung.recruit.service.ApplicationPdfService;
import com.shinyoung.recruit.service.ApplicationPdfZipFile;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.ExportAuditContext;
import com.shinyoung.recruit.service.PdfAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final ApplicationPdfBulkService applicationPdfBulkService;
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

    /**
     * 목록에서 선택한 지원서를 zip 으로 내려준다. 개별 PDF 내용은 단건 다운로드와 같다.
     *
     * <p>zip 을 다 만든 뒤 audit 를 남기고 나서 스트리밍을 시작한다. 스트리밍이 시작되면 이미 200 이
     * 나가 오류로 되돌릴 수 없으므로, egress fail-close 를 지키려면 이 순서여야 한다(Phase 09b 규약).
     * 감사는 단건과 같은 수준으로 <b>건별</b>로 남기며, 같은 requestId 로 한 요청임을 묶어 볼 수 있다.
     */
    @PostMapping("/admin/applications/pdf/bulk")
    public ResponseEntity<StreamingResponseBody> applicationPdfBulk(
            @Valid @RequestBody ApplicationPdfBulkRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        ApplicationPdfZipFile zipFile = applicationPdfBulkService.generate(request.applicationIds());

        try {
            ExportAuditContext auditContext = auditContext(actor, userDetails, httpRequest);
            for (ApplicationPdfZipFile.Entry entry : zipFile.entries()) {
                pdfAuditLogger.logApplicationPdf(
                        auditContext, entry.applicationId(), entry.jobPostingId(), entry.jobPositionId());
            }
        } catch (RuntimeException e) {
            deleteQuietly(zipFile);
            throw e;
        }

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = Files.newInputStream(zipFile.path())) {
                in.transferTo(outputStream);
            } finally {
                Files.deleteIfExists(zipFile.path());
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ApplicationPdfZipFile.CONTENT_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(zipFile.fileName()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }

    private void deleteQuietly(ApplicationPdfZipFile zipFile) {
        try {
            Files.deleteIfExists(zipFile.path());
        } catch (IOException ignored) {
            // temp 파일 정리 실패는 원인 예외 전파를 막지 않는다.
        }
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
