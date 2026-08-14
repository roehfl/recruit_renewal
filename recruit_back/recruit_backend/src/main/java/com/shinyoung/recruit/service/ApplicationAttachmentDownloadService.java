package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationAttachmentDownloadService {

    private static final String OCTET_STREAM = "application/octet-stream";

    private final ApplicationSectionAccessService sectionAccessService;
    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationAttachmentRepository attachmentRepository;
    private final AttachmentStorageService storageService;

    public AttachmentDownloadResource downloadForApplicant(
            Long applicantId,
            Long applicationId,
            Long attachmentId
    ) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        ApplicationAttachment attachment = findStoredAttachment(applicationId, attachmentId);
        return toDownloadResource(attachment);
    }

    public AttachmentDownloadResource downloadForAdmin(Long applicationId, Long attachmentId) {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new JobApplicationNotFoundException("Application was not found.");
        }
        ApplicationAttachment attachment = findStoredAttachment(applicationId, attachmentId);
        return toDownloadResource(attachment);
    }

    private ApplicationAttachment findStoredAttachment(Long applicationId, Long attachmentId) {
        return attachmentRepository.findByIdAndJobApplicationIdAndPhysicalFileStatus(
                attachmentId,
                applicationId,
                PhysicalFileStatus.STORED
        ).orElseThrow(() -> new JobApplicationNotFoundException("Attachment was not found."));
    }

    private AttachmentDownloadResource toDownloadResource(ApplicationAttachment attachment) {
        AttachmentStorageResource storageResource;
        try {
            storageResource = storageService.load(attachment.getStoragePath());
        } catch (JobApplicationNotFoundException e) {
            log.warn("Stored attachment physical file was not found. attachmentId={}", attachment.getId());
            throw e;
        }

        if (attachment.getFileSize() != null && attachment.getFileSize() != storageResource.contentLength()) {
            log.warn(
                    "Attachment file size mismatch. attachmentId={}, dbFileSize={}, actualFileSize={}",
                    attachment.getId(),
                    attachment.getFileSize(),
                    storageResource.contentLength()
            );
        }

        return new AttachmentDownloadResource(
                storageResource.resource(),
                storageResource.contentLength(),
                normalizeContentType(attachment.getContentType()),
                attachment.getOriginalFileName()
        );
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return OCTET_STREAM;
        }
        return contentType;
    }
}
