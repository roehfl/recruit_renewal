package com.shinyoung.recruit.service;

public record StoredAttachmentFile(
        String storedFileName,
        String storagePath,
        String contentType,
        Long fileSize
) {
}
