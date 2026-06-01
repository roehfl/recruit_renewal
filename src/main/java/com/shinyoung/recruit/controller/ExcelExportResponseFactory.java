package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.service.ExcelExportFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Export temp xlsx 파일을 download 응답으로 변환한다.
 *
 * <p>{@link StreamingResponseBody}로 temp 파일을 전송하고, <strong>전송 완료 후 finally에서
 * temp 파일을 삭제</strong>한다(성공/실패/예외 모두). 헤더 규약은 첨부 download factory와 동일하다:
 * xlsx content-type, attachment disposition(ASCII fallback + UTF-8 filename*), nosniff, no-store.
 */
@Component
public class ExcelExportResponseFactory {

    public ResponseEntity<StreamingResponseBody> toResponse(ExcelExportFile file) {
        StreamingResponseBody body = outputStream -> {
            try (InputStream in = Files.newInputStream(file.path())) {
                in.transferTo(outputStream);
            } finally {
                Files.deleteIfExists(file.path());
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ExcelExportFile.CONTENT_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.fileName()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }

    private String contentDisposition(String fileName) {
        String safe = safeFileName(fileName);
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "export.xlsx";
        }
        return fileName
                .replace('/', '_')
                .replace('\\', '_')
                .replace('"', '_')
                .replaceAll("\\p{Cntrl}", "_")
                .trim();
    }
}
