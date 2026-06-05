package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.AttachmentReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.AttachmentDeleteResponse;
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentDeleteActorType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.attachment.storage-root=build/test-attachments/application-attachment-delete-service",
        "recruit.attachment.max-file-size=5KB",
        "recruit.attachment.max-files-per-application=10",
        "recruit.attachment.max-total-size-per-application=50KB"
})
class ApplicationAttachmentDeleteServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationAttachmentDeleteService deleteService;

    @Autowired
    private ApplicationAttachmentFileService fileService;

    @Autowired
    private ApplicationAttachmentService attachmentService;

    @Autowired
    private ApplicationAttachmentDownloadService downloadService;

    @Autowired
    private AdminApplicationSectionService adminApplicationSectionService;

    @Autowired
    private AttachmentStorageService storageService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationAttachmentRepository attachmentRepository;

    @Test
    void applicant_owner_draft_stored_delete_marks_deleted_and_deletes_physical_file_after_commit() {
        Applicant applicant = createApplicant("delete-stored", "Delete Stored");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "stored.pdf", "stored-content");
        ApplicationAttachment beforeDelete = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(storageService.exists(beforeDelete.getStoragePath())).isTrue();

        AttachmentDeleteResponse response = deleteService.deleteForApplicant(applicant.getId(), applicationId, attachmentId);

        ApplicationAttachment deleted = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(response.deleted()).isTrue();
        assertThat(response.physicalDeleteRequested()).isTrue();
        assertThat(deleted.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.SOFT_DELETED);
        assertThat(deleted.getDeletedBy()).isEqualTo(String.valueOf(applicant.getId()));
        assertThat(deleted.getDeletedByType()).isEqualTo(AttachmentDeleteActorType.APPLICANT);
        assertThat(deleted.getDeletionReason()).isEqualTo("APPLICANT_SELF_DELETE");
        assertThat(deleted.getDeletedAt()).isEqualTo(LocalDateTime.ofInstant(FIXED_CLOCK.instant(), FIXED_CLOCK.getZone()));
        assertThat(storageService.exists(beforeDelete.getStoragePath())).isFalse();
    }

    @Test
    void applicant_owner_draft_metadata_only_delete_does_not_request_physical_delete() {
        Applicant applicant = createApplicant("delete-metadata", "Delete Metadata");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        attachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("metadata.pdf", 0)))
        );
        Long attachmentId = attachmentRepository.findByJobApplicationId(applicationId).get(0).getId();

        AttachmentDeleteResponse response = deleteService.deleteForApplicant(applicant.getId(), applicationId, attachmentId);

        ApplicationAttachment deleted = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(response.physicalDeleteRequested()).isFalse();
        assertThat(deleted.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.SOFT_DELETED);
    }

    @Test
    void deleted_rows_are_excluded_from_metadata_lists_and_download() {
        Applicant applicant = createApplicant("delete-list", "Delete List");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long deletedAttachmentId = upload(applicant, applicationId, "deleted.pdf", "deleted");
        Long activeAttachmentId = upload(applicant, applicationId, "active.pdf", "active");

        deleteService.deleteForApplicant(applicant.getId(), applicationId, deletedAttachmentId);

        assertThat(attachmentService.getAttachments(applicant.getId(), applicationId))
                .extracting(AttachmentResponse::attachmentId)
                .containsExactly(activeAttachmentId);
        assertThat(adminApplicationSectionService.getAttachments(applicationId))
                .extracting(response -> response.attachmentId())
                .containsExactly(activeAttachmentId);
        assertThatThrownBy(() -> downloadService.downloadForApplicant(applicant.getId(), applicationId, deletedAttachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void applicant_delete_rejects_non_writable_or_not_owned_application() {
        Applicant owner = createApplicant("delete-owner", "Delete Owner");
        Applicant other = createApplicant("delete-other", "Delete Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        Long attachmentId = upload(owner, applicationId, "resume.pdf", "resume");

        assertThatThrownBy(() -> deleteService.deleteForApplicant(other.getId(), applicationId, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);

        jobApplicationService.submit(owner.getId(), applicationId);
        assertThatThrownBy(() -> deleteService.deleteForApplicant(owner.getId(), applicationId, attachmentId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("delete-withdrawn", "Delete Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting());
        Long withdrawnAttachmentId = upload(withdrawnApplicant, withdrawnApplicationId, "withdrawn.pdf", "withdrawn");
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);
        assertThatThrownBy(() -> deleteService.deleteForApplicant(withdrawnApplicant.getId(), withdrawnApplicationId, withdrawnAttachmentId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void applicant_delete_rejects_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("delete-before", "Delete Before");
        Long beforePostingId = createPublishedJobPosting();
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        Long beforeAttachmentId = upload(beforeApplicant, beforeApplicationId, "before.pdf", "before");
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> deleteService.deleteForApplicant(beforeApplicant.getId(), beforeApplicationId, beforeAttachmentId))
                .isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftPostingApplicant = createApplicant("delete-draft-posting", "Delete Draft Posting");
        Long draftPostingId = createPublishedJobPosting();
        Long draftApplicationId = createApplication(draftPostingApplicant, draftPostingId);
        Long draftAttachmentId = upload(draftPostingApplicant, draftApplicationId, "draft.pdf", "draft");
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> deleteService.deleteForApplicant(draftPostingApplicant.getId(), draftApplicationId, draftAttachmentId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void delete_attachment_application_mismatch_and_already_deleted_return_not_found() {
        Applicant applicant = createApplicant("delete-404", "Delete 404");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long otherApplicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "resume.pdf", "resume");

        assertThatThrownBy(() -> deleteService.deleteForApplicant(applicant.getId(), otherApplicationId, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);

        deleteService.deleteForApplicant(applicant.getId(), applicationId, attachmentId);
        assertThatThrownBy(() -> deleteService.deleteForApplicant(applicant.getId(), applicationId, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> deleteService.deleteForAdmin(applicationId, attachmentId, "admin01", "duplicate"))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void admin_delete_allows_application_statuses_and_persists_reason() {
        Applicant submittedApplicant = createApplicant("delete-admin-submitted", "Delete Admin Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting());
        Long submittedAttachmentId = upload(submittedApplicant, submittedApplicationId, "submitted.pdf", "submitted");
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        AttachmentDeleteResponse submittedResponse = deleteService.deleteForAdmin(
                submittedApplicationId,
                submittedAttachmentId,
                "admin01",
                " submitted evidence cleanup "
        );

        ApplicationAttachment submittedDeleted = attachmentRepository.findById(submittedAttachmentId).orElseThrow();
        assertThat(submittedResponse.physicalDeleteRequested()).isTrue();
        assertThat(submittedDeleted.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.SOFT_DELETED);
        assertThat(submittedDeleted.getDeletedBy()).isEqualTo("admin01");
        assertThat(submittedDeleted.getDeletedByType()).isEqualTo(AttachmentDeleteActorType.EMPLOYEE);
        assertThat(submittedDeleted.getDeletionReason()).isEqualTo("submitted evidence cleanup");

        Applicant withdrawnApplicant = createApplicant("delete-admin-withdrawn", "Delete Admin Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting());
        Long withdrawnAttachmentId = upload(withdrawnApplicant, withdrawnApplicationId, "withdrawn.pdf", "withdrawn");
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThat(deleteService.deleteForAdmin(withdrawnApplicationId, withdrawnAttachmentId, "admin02", "withdrawn cleanup").deleted())
                .isTrue();
    }

    @Test
    void admin_delete_validates_reason_actor_and_scope() {
        Applicant applicant = createApplicant("delete-admin-validation", "Delete Admin Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long otherApplicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "admin.pdf", "admin");

        assertThatThrownBy(() -> deleteService.deleteForAdmin(applicationId, attachmentId, "admin01", " "))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> deleteService.deleteForAdmin(applicationId, attachmentId, "admin01", "a".repeat(1001)))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> deleteService.deleteForAdmin(applicationId, attachmentId, " ", "reason"))
                .isInstanceOf(InvalidJobApplicationException.class);
        assertThatThrownBy(() -> deleteService.deleteForAdmin(otherApplicationId, attachmentId, "admin01", "reason"))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> deleteService.deleteForAdmin(999999L, attachmentId, "admin01", "reason"))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void missing_physical_file_delete_still_marks_deleted() {
        Applicant applicant = createApplicant("delete-missing-physical", "Delete Missing Physical");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "missing.pdf", "missing");
        ApplicationAttachment attachment = attachmentRepository.findById(attachmentId).orElseThrow();
        storageService.deleteIfExists(attachment.getStoragePath());

        AttachmentDeleteResponse response = deleteService.deleteForApplicant(applicant.getId(), applicationId, attachmentId);

        assertThat(response.physicalDeleteRequested()).isTrue();
        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getPhysicalFileStatus())
                .isEqualTo(PhysicalFileStatus.SOFT_DELETED);
    }

    @Test
    void invalid_storage_path_delete_still_keeps_api_contract_and_marks_deleted() {
        Applicant applicant = createApplicant("delete-invalid-storage", "Delete Invalid Storage");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "invalid.pdf", "invalid");
        ApplicationAttachment attachment = attachmentRepository.findById(attachmentId).orElseThrow();
        ReflectionTestUtils.setField(attachment, "storagePath", "../outside.pdf");
        attachmentRepository.saveAndFlush(attachment);

        AttachmentDeleteResponse response = deleteService.deleteForApplicant(applicant.getId(), applicationId, attachmentId);

        assertThat(response.deleted()).isTrue();
        assertThat(response.physicalDeleteRequested()).isTrue();
        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getPhysicalFileStatus())
                .isEqualTo(PhysicalFileStatus.SOFT_DELETED);
    }

    @Test
    void upload_append_sort_order_includes_deleted_rows_and_metadata_replace_ignores_deleted_conflict() {
        Applicant applicant = createApplicant("delete-sort", "Delete Sort");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long deletedAttachmentId = upload(applicant, applicationId, "deleted.pdf", "deleted");
        ApplicationAttachment uploaded = attachmentRepository.findById(deletedAttachmentId).orElseThrow();
        assertThat(uploaded.getSortOrder()).isZero();

        deleteService.deleteForApplicant(applicant.getId(), applicationId, deletedAttachmentId);

        AttachmentResponse newUpload = fileService.upload(
                applicant.getId(),
                applicationId,
                file("new.pdf", "new"),
                AttachmentType.PORTFOLIO,
                ApplicationSectionType.APPLICATION,
                null
        );
        assertThat(newUpload.sortOrder()).isEqualTo(1);

        deleteService.deleteForApplicant(applicant.getId(), applicationId, newUpload.attachmentId());
        List<AttachmentResponse> metadataResponses = attachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("metadata.pdf", 0)))
        );

        assertThat(metadataResponses).singleElement()
                .extracting(AttachmentResponse::sortOrder)
                .isEqualTo(0);
    }

    @Test
    void delete_response_does_not_expose_storage_or_status_fields() {
        List<String> componentNames = Arrays.stream(AttachmentDeleteResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain(
                "storedFileName",
                "storagePath",
                "storageRoot",
                "physicalFileStatus",
                "absolutePath",
                "downloadAvailable"
        );
    }

    private Long upload(Applicant applicant, Long applicationId, String originalFileName, String content) {
        AttachmentResponse response = fileService.upload(
                applicant.getId(),
                applicationId,
                file(originalFileName, content),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        );
        return response.attachmentId();
    }

    private MockMultipartFile file(String originalFileName, String content) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                "application/pdf",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Applicant createApplicant(String loginId, String applicantName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + applicantName);
        applicant.setUserName(applicantName);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long createPublishedJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
    }

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }

    private AttachmentRequest attachment(String originalFileName, Integer sortOrder) {
        return new AttachmentRequest(
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null,
                originalFileName,
                null,
                null,
                "application/pdf",
                1024L,
                sortOrder
        );
    }

    private void setReceptionPeriod(Long jobPostingId, LocalDateTime start, LocalDateTime end) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        jobPosting.updateBasicInfo(jobPosting.getTitle(), jobPosting.getContentHtml(), start, end);
        jobPostingRepository.saveAndFlush(jobPosting);
    }

    private void setJobPostingStatus(Long jobPostingId, JobPostingStatus status) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        ReflectionTestUtils.setField(jobPosting, "status", status);
        jobPostingRepository.saveAndFlush(jobPosting);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
