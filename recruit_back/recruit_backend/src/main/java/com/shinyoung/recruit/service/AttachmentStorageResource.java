package com.shinyoung.recruit.service;

import org.springframework.core.io.Resource;

public record AttachmentStorageResource(
        Resource resource,
        long contentLength
) {
}
