package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationAttachmentFileService {

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationAttachmentRepository attachmentRepository;
    private final AttachmentFilePolicy filePolicy;
    private final AttachmentStorageService storageService;
    private final AttachmentProperties attachmentProperties;

    @Transactional
    public AttachmentResponse upload(
            Long applicantId,
            Long applicationId,
            MultipartFile file,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId
    ) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        validateMetadata(attachmentType, sectionType, sectionRecordId);
        AttachmentFilePolicy.ValidatedAttachmentFile validatedFile = filePolicy.validate(file);
        validateStoredLimits(applicationId, file.getSize());

        StoredAttachmentFile storedFile = storageService.store(
                applicationId,
                file,
                validatedFile.originalFileName(),
                validatedFile.extension()
        );
        registerRollbackCleanup(storedFile.storagePath());

        try {
            ApplicationAttachment attachment = ApplicationAttachment.createStored(
                    application,
                    attachmentType,
                    sectionType,
                    sectionRecordId,
                    validatedFile.originalFileName(),
                    storedFile.storedFileName(),
                    storedFile.storagePath(),
                    storedFile.contentType(),
                    storedFile.fileSize(),
                    nextSortOrder(applicationId)
            );
            ApplicationAttachment saved = attachmentRepository.saveAndFlush(attachment);
            return AttachmentResponse.from(saved);
        } catch (RuntimeException e) {
            cleanupStoredFile(storedFile.storagePath());
            throw e;
        }
    }

    private void validateMetadata(
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId
    ) {
        if (attachmentType == null) {
            throw new InvalidJobApplicationException("Attachment type is required.");
        }
        if (sectionType == null) {
            throw new InvalidJobApplicationException("Section type is required.");
        }
        if (sectionType == ApplicationSectionType.APPLICATION && sectionRecordId != null) {
            throw new InvalidJobApplicationException("Application section attachment cannot have a section record id.");
        }
        if (sectionType != ApplicationSectionType.APPLICATION
                && sectionRecordId != null
                && sectionRecordId <= 0) {
            throw new InvalidJobApplicationException("Section record id must be greater than 0.");
        }
    }

    private void validateStoredLimits(Long applicationId, long newFileSize) {
        long storedCount = attachmentRepository.countByJobApplicationIdAndPhysicalFileStatus(
                applicationId,
                PhysicalFileStatus.STORED
        );
        if (storedCount + 1 > attachmentProperties.getMaxFilesPerApplication()) {
            throw new InvalidJobApplicationException("Attachment file count exceeds the allowed limit.");
        }

        Long currentTotal = attachmentRepository.sumFileSizeByJobApplicationIdAndPhysicalFileStatus(
                applicationId,
                PhysicalFileStatus.STORED
        );
        long totalSize = (currentTotal == null ? 0L : currentTotal) + newFileSize;
        if (totalSize > attachmentProperties.getMaxTotalSizePerApplication().toBytes()) {
            throw new InvalidJobApplicationException("Attachment total file size exceeds the allowed limit.");
        }
    }

    private Integer nextSortOrder(Long applicationId) {
        Integer currentMax = attachmentRepository.findMaxSortOrderByJobApplicationId(applicationId);
        return currentMax == null ? 0 : currentMax + 1;
    }

    private void registerRollbackCleanup(String storagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    cleanupStoredFile(storagePath);
                }
            }
        });
    }

    private void cleanupStoredFile(String storagePath) {
        try {
            storageService.deleteIfExists(storagePath);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to cleanup attachment file.", cleanupFailure);
        }
    }
}
