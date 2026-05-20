package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.response.AttachmentDeleteResponse;
import com.shinyoung.recruit.enumeration.AttachmentDeleteActorType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationAttachmentDeleteService {

    private static final String APPLICANT_SELF_DELETE_REASON = "APPLICANT_SELF_DELETE";
    private static final int DELETION_REASON_MAX_LENGTH = 1000;

    private final ApplicationSectionAccessService sectionAccessService;
    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationAttachmentRepository attachmentRepository;
    private final AttachmentStorageService storageService;
    private final Clock clock;

    @Transactional
    public AttachmentDeleteResponse deleteForApplicant(Long applicantId, Long applicationId, Long attachmentId) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        ApplicationAttachment attachment = findActiveAttachment(applicationId, attachmentId);
        return delete(
                applicationId,
                attachment,
                String.valueOf(applicantId),
                AttachmentDeleteActorType.APPLICANT,
                APPLICANT_SELF_DELETE_REASON
        );
    }

    @Transactional
    public AttachmentDeleteResponse deleteForAdmin(
            Long applicationId,
            Long attachmentId,
            String adminActor,
            String reason
    ) {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new JobApplicationNotFoundException("Application was not found.");
        }
        validateAdminActor(adminActor);
        String normalizedReason = validateReason(reason);
        ApplicationAttachment attachment = findActiveAttachment(applicationId, attachmentId);
        return delete(
                applicationId,
                attachment,
                adminActor.trim(),
                AttachmentDeleteActorType.EMPLOYEE,
                normalizedReason
        );
    }

    private AttachmentDeleteResponse delete(
            Long applicationId,
            ApplicationAttachment attachment,
            String deletedBy,
            AttachmentDeleteActorType deletedByType,
            String deletionReason
    ) {
        PhysicalFileStatus previousStatus = attachment.getPhysicalFileStatus();
        String storagePath = attachment.getStoragePath();
        boolean physicalDeleteRequested = previousStatus == PhysicalFileStatus.STORED
                && storagePath != null
                && !storagePath.isBlank();

        attachment.markDeleted(
                deletedBy,
                deletedByType,
                deletionReason,
                LocalDateTime.now(clock)
        );

        if (physicalDeleteRequested) {
            registerAfterCommitPhysicalDelete(applicationId, attachment.getId(), storagePath);
        }

        return AttachmentDeleteResponse.deleted(applicationId, attachment.getId(), physicalDeleteRequested);
    }

    private ApplicationAttachment findActiveAttachment(Long applicationId, Long attachmentId) {
        return attachmentRepository.findByIdAndJobApplicationIdAndPhysicalFileStatusNot(
                attachmentId,
                applicationId,
                PhysicalFileStatus.DELETED
        ).orElseThrow(() -> new JobApplicationNotFoundException("Attachment was not found."));
    }

    private void validateAdminActor(String adminActor) {
        if (adminActor == null || adminActor.isBlank()) {
            throw new InvalidJobApplicationException("Admin actor is required.");
        }
    }

    private String validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidJobApplicationException("Attachment deletion reason is required.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > DELETION_REASON_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Attachment deletion reason must be 1000 characters or less.");
        }
        return trimmed;
    }

    private void registerAfterCommitPhysicalDelete(Long applicationId, Long attachmentId, String storagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePhysicalFile(applicationId, attachmentId, storagePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePhysicalFile(applicationId, attachmentId, storagePath);
            }
        });
    }

    private void deletePhysicalFile(Long applicationId, Long attachmentId, String storagePath) {
        try {
            storageService.deleteIfExists(storagePath);
        } catch (RuntimeException e) {
            log.warn(
                    "Attachment physical delete failed after DB soft delete. applicationId={}, attachmentId={}",
                    applicationId,
                    attachmentId,
                    e
            );
        }
    }
}
