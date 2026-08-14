package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.service.AttachmentDownloadResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class AttachmentDownloadResponseFactory {

    public ResponseEntity<Resource> toResponse(AttachmentDownloadResource downloadResource) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, downloadResource.contentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(downloadResource.contentLength()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(downloadResource.originalFileName()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(downloadResource.resource());
    }

    private String contentDisposition(String originalFileName) {
        String fallback = asciiFallback(originalFileName);
        String encoded = URLEncoder.encode(safeOriginalFileName(originalFileName), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    private String safeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "download";
        }
        return originalFileName
                .replace('/', '_')
                .replace('\\', '_')
                .replaceAll("\\p{Cntrl}", "_")
                .trim();
    }

    private String asciiFallback(String originalFileName) {
        String safeName = safeOriginalFileName(originalFileName);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < safeName.length(); i++) {
            char ch = safeName.charAt(i);
            if (ch >= 0x20 && ch <= 0x7E && ch != '"') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }

        String fallback = builder.toString().replaceAll("_+", "_");
        if (fallback.isBlank() || fallback.chars().allMatch(ch -> ch == '_' || ch == '.')) {
            return fallbackFromExtension(safeName);
        }
        return fallback;
    }

    private String fallbackFromExtension(String safeName) {
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < safeName.length() - 1) {
            String extension = safeName.substring(dotIndex + 1).replaceAll("[^A-Za-z0-9]", "");
            if (!extension.isBlank()) {
                return "download." + extension;
            }
        }
        return "download";
    }
}
