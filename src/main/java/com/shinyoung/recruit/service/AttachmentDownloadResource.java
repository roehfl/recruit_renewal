package com.shinyoung.recruit.service;

import org.springframework.core.io.Resource;

public record AttachmentDownloadResource(
        Resource resource,
        long contentLength,
        String contentType,
        String originalFileName
) {
}
