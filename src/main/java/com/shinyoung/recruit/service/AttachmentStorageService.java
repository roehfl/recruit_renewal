package com.shinyoung.recruit.service;

import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorageService {

    StoredAttachmentFile store(
            Long applicationId,
            MultipartFile file,
            String sanitizedOriginalFileName,
            String extension
    );

    void deleteIfExists(String storagePath);

    boolean exists(String storagePath);

    AttachmentStorageResource load(String storagePath);
}
