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
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.attachment.storage-root=build/test-attachments/application-attachment-download-service",
        "recruit.attachment.max-file-size=5KB",
        "recruit.attachment.max-files-per-application=10",
        "recruit.attachment.max-total-size-per-application=50KB"
})
@Transactional
class ApplicationAttachmentDownloadServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationAttachmentDownloadService downloadService;

    @Autowired
    private ApplicationAttachmentFileService fileService;

    @Autowired
    private ApplicationAttachmentService attachmentService;

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
    void applicant_owner_can_download_stored_attachment_from_draft_submitted_and_withdrawn_application() throws Exception {
        Applicant draftApplicant = createApplicant("download-draft", "Download Draft");
        Long draftApplicationId = createApplication(draftApplicant, createPublishedJobPosting());
        Long draftAttachmentId = upload(draftApplicant, draftApplicationId, "draft.pdf", "draft-content");

        AttachmentDownloadResource draft = downloadService.downloadForApplicant(
                draftApplicant.getId(),
                draftApplicationId,
                draftAttachmentId
        );
        assertThat(draft.contentLength()).isEqualTo("draft-content".getBytes(StandardCharsets.UTF_8).length);

        Applicant submittedApplicant = createApplicant("download-submitted", "Download Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting());
        Long submittedAttachmentId = upload(submittedApplicant, submittedApplicationId, "submitted.pdf", "submitted");
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        AttachmentDownloadResource submitted = downloadService.downloadForApplicant(
                submittedApplicant.getId(),
                submittedApplicationId,
                submittedAttachmentId
        );
        assertThat(submitted.originalFileName()).isEqualTo("submitted.pdf");

        Applicant withdrawnApplicant = createApplicant("download-withdrawn", "Download Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting());
        Long withdrawnAttachmentId = upload(withdrawnApplicant, withdrawnApplicationId, "withdrawn.pdf", "withdrawn");
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        AttachmentDownloadResource withdrawn = downloadService.downloadForApplicant(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                withdrawnAttachmentId
        );
        assertThat(withdrawn.originalFileName()).isEqualTo("withdrawn.pdf");
    }

    @Test
    void applicant_download_hides_other_applicant_and_attachment_application_mismatch() {
        Applicant owner = createApplicant("download-owner", "Download Owner");
        Applicant other = createApplicant("download-other", "Download Other");
        Long ownerApplicationId = createApplication(owner, createPublishedJobPosting());
        Long otherApplicationId = createApplication(other, createPublishedJobPosting());
        Long attachmentId = upload(owner, ownerApplicationId, "resume.pdf", "resume");

        assertThatThrownBy(() -> downloadService.downloadForApplicant(other.getId(), ownerApplicationId, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);

        assertThatThrownBy(() -> downloadService.downloadForApplicant(other.getId(), otherApplicationId, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void metadata_only_and_missing_attachments_are_not_downloadable() {
        Applicant applicant = createApplicant("download-status", "Download Status");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        attachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(
                        attachment("metadata.pdf", 0),
                        attachment("missing.pdf", 1)
                ))
        );
        List<ApplicationAttachment> attachments = attachmentRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId);
        ApplicationAttachment missing = attachments.get(1);
        ReflectionTestUtils.setField(missing, "physicalFileStatus", PhysicalFileStatus.MISSING);
        attachmentRepository.saveAndFlush(missing);

        assertThatThrownBy(() -> downloadService.downloadForApplicant(applicant.getId(), applicationId, attachments.get(0).getId()))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> downloadService.downloadForApplicant(applicant.getId(), applicationId, missing.getId()))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void missing_physical_file_returns_controlled_not_found_without_status_mutation() {
        Applicant applicant = createApplicant("download-missing-file", "Download Missing File");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "resume.pdf", "resume");
        ApplicationAttachment stored = attachmentRepository.findById(attachmentId).orElseThrow();
        storageService.deleteIfExists(stored.getStoragePath());

        assertThatThrownBy(() -> downloadService.downloadForApplicant(applicant.getId(), applicationId, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class)
                .hasMessage("Attachment file was not found.");

        ApplicationAttachment afterFailure = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(afterFailure.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.STORED);
    }

    @Test
    void admin_download_validates_application_and_attachment_scope() {
        Applicant applicant = createApplicant("download-admin", "Download Admin");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long otherApplicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "admin.pdf", "admin");

        AttachmentDownloadResource response = downloadService.downloadForAdmin(applicationId, attachmentId);

        assertThat(response.originalFileName()).isEqualTo("admin.pdf");
        assertThatThrownBy(() -> downloadService.downloadForAdmin(99999L, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> downloadService.downloadForAdmin(otherApplicationId, attachmentId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void content_type_falls_back_and_content_length_uses_actual_physical_file_size() {
        Applicant applicant = createApplicant("download-content", "Download Content");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "content.pdf", "abcde");
        ApplicationAttachment attachment = attachmentRepository.findById(attachmentId).orElseThrow();
        ReflectionTestUtils.setField(attachment, "contentType", " ");
        ReflectionTestUtils.setField(attachment, "fileSize", 999L);
        attachmentRepository.saveAndFlush(attachment);

        AttachmentDownloadResource response = downloadService.downloadForApplicant(
                applicant.getId(),
                applicationId,
                attachmentId
        );

        assertThat(response.contentType()).isEqualTo("application/octet-stream");
        assertThat(response.contentLength()).isEqualTo(5L);
    }

    private Long upload(Applicant applicant, Long applicationId, String originalFileName, String content) {
        AttachmentResponse response = fileService.upload(
                applicant.getId(),
                applicationId,
                new MockMultipartFile(
                        "file",
                        originalFileName,
                        "application/pdf",
                        content.getBytes(StandardCharsets.UTF_8)
                ),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        );
        return response.attachmentId();
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
