package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.dto.request.AttachmentReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequest;
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationAttachmentService {

    private static final int ORIGINAL_FILE_NAME_MAX_LENGTH = 255;
    private static final int STORED_FILE_NAME_MAX_LENGTH = 255;
    private static final int STORAGE_PATH_MAX_LENGTH = 1000;
    private static final int CONTENT_TYPE_MAX_LENGTH = 100;

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationAttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachments(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return getAttachmentResponses(applicationId);
    }

    @Transactional
    public List<AttachmentResponse> replaceAttachments(
            Long applicantId,
            Long applicationId,
            AttachmentReplaceRequest request
    ) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        validateRequest(request, applicationId);

        attachmentRepository.deleteByJobApplicationIdAndPhysicalFileStatus(applicationId, PhysicalFileStatus.METADATA_ONLY);
        List<ApplicationAttachment> attachments = request.attachments().stream()
                .map(attachment -> toAttachment(application, attachment))
                .toList();
        attachmentRepository.saveAll(attachments);

        return getAttachmentResponses(applicationId);
    }

    private void validateRequest(AttachmentReplaceRequest request, Long applicationId) {
        if (request == null || request.attachments() == null) {
            throw new InvalidJobApplicationException("Attachment list is required.");
        }

        Set<Integer> sortOrders = new HashSet<>();
        for (AttachmentRequest attachment : request.attachments()) {
            validateAttachmentRequiredFields(attachment);
            if (!sortOrders.add(attachment.sortOrder())) {
                throw new InvalidJobApplicationException("Attachment sort order must be unique.");
            }
        }

        Set<Integer> storedSortOrders = new HashSet<>(
                attachmentRepository.findSortOrdersByJobApplicationIdAndPhysicalFileStatus(
                        applicationId,
                        PhysicalFileStatus.STORED
                )
        );
        for (Integer sortOrder : sortOrders) {
            if (storedSortOrders.contains(sortOrder)) {
                throw new InvalidJobApplicationException("Attachment sort order conflicts with stored file attachment.");
            }
        }
    }

    private void validateAttachmentRequiredFields(AttachmentRequest attachment) {
        if (attachment == null) {
            throw new InvalidJobApplicationException("Attachment item is required.");
        }
        if (attachment.attachmentType() == null) {
            throw new InvalidJobApplicationException("Attachment type is required.");
        }
        if (attachment.sectionType() == null) {
            throw new InvalidJobApplicationException("Section type is required.");
        }
        validateRequiredString(attachment.originalFileName(), "Original file name is required.");
        validateForbiddenStorageField(attachment.storedFileName(), "storedFileName");
        validateForbiddenStorageField(attachment.storagePath(), "storagePath");
        validateRequiredString(attachment.contentType(), "Content type is required.");
        validateMaxLength(attachment.originalFileName(), ORIGINAL_FILE_NAME_MAX_LENGTH, "Original file name");
        validateMaxLength(attachment.contentType(), CONTENT_TYPE_MAX_LENGTH, "Content type");
        if (attachment.fileSize() == null || attachment.fileSize() <= 0) {
            throw new InvalidJobApplicationException("File size must be greater than 0.");
        }
        if (attachment.sortOrder() == null || attachment.sortOrder() < 0) {
            throw new InvalidJobApplicationException("Sort order must be greater than or equal to 0.");
        }
        validateSectionRecord(attachment);
    }

    private void validateRequiredString(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidJobApplicationException(message);
        }
    }

    private void validateMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidJobApplicationException(fieldName + " must be " + maxLength + " characters or less.");
        }
    }

    private void validateForbiddenStorageField(String value, String fieldName) {
        if (value != null) {
            throw new InvalidJobApplicationException(fieldName + " cannot be supplied by client.");
        }
    }

    private void validateSectionRecord(AttachmentRequest attachment) {
        if (attachment.sectionType() == ApplicationSectionType.APPLICATION && attachment.sectionRecordId() != null) {
            throw new InvalidJobApplicationException("Application section attachment cannot have a section record id.");
        }
        if (attachment.sectionType() != ApplicationSectionType.APPLICATION
                && attachment.sectionRecordId() != null
                && attachment.sectionRecordId() <= 0) {
            throw new InvalidJobApplicationException("Section record id must be greater than 0.");
        }
    }

    private ApplicationAttachment toAttachment(JobApplication application, AttachmentRequest request) {
        String metadataKey = "metadata-only-" + UUID.randomUUID();
        return ApplicationAttachment.create(
                application,
                request.attachmentType(),
                request.sectionType(),
                request.sectionRecordId(),
                request.originalFileName(),
                metadataKey,
                "metadata-only/" + application.getId() + "/" + metadataKey,
                request.contentType(),
                request.fileSize(),
                request.sortOrder()
        );
    }

    private List<AttachmentResponse> getAttachmentResponses(Long applicationId) {
        return attachmentRepository.findByJobApplicationIdAndPhysicalFileStatusNotOrderBySortOrderAscIdAsc(
                        applicationId,
                        PhysicalFileStatus.DELETED
                )
                .stream()
                .map(AttachmentResponse::from)
                .toList();
    }
}
