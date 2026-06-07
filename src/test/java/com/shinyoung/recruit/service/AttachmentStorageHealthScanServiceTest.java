package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.AttachmentReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.dto.response.AttachmentStorageHealthIssueResponse;
import com.shinyoung.recruit.dto.response.AttachmentStorageHealthScanResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentStorageHealthIssueType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.attachment.storage-root=build/test-attachments/attachment-storage-health-scan-service",
        "recruit.attachment.max-file-size=5KB",
        "recruit.attachment.max-files-per-application=10",
        "recruit.attachment.max-total-size-per-application=50KB"
})
@Transactional
class AttachmentStorageHealthScanServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private AttachmentStorageHealthScanService scanService;

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
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationAttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentProperties attachmentProperties;

    @BeforeEach
    void setUp() throws Exception {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        FileSystemUtils.deleteRecursively(attachmentProperties.getStorageRoot());
        Files.createDirectories(attachmentProperties.getStorageRoot());
        TestTransaction.start();
    }

    @Test
    void scan_reports_clean_stored_attachment_without_issues() {
        Applicant applicant = createApplicant("health-clean", "Health Clean");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        upload(applicant, applicationId, "stored.pdf", "stored");

        AttachmentStorageHealthScanResponse response = scanService.scanDryRun();

        assertThat(response.dryRun()).isTrue();
        assertThat(response.storedRowCount()).isEqualTo(1);
        assertThat(response.managedPhysicalFileCount()).isEqualTo(1);
        assertThat(response.issues()).isEmpty();
    }

    @Test
    void scan_reports_stored_missing_deleted_remaining_orphan_missing_present_and_invalid_paths() throws Exception {
        Applicant applicant = createApplicant("health-issues", "Health Issues");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long storedMissingId = upload(applicant, applicationId, "missing.pdf", "missing");
        Long deletedRemainingId = upload(applicant, applicationId, "deleted.pdf", "deleted");
        Long missingPresentId = upload(applicant, applicationId, "missing-present.pdf", "missing-present");

        ApplicationAttachment storedMissing = attachmentRepository.findById(storedMissingId).orElseThrow();
        storageService.deleteIfExists(storedMissing.getStoragePath());

        ApplicationAttachment deletedRemaining = attachmentRepository.findById(deletedRemainingId).orElseThrow();
        ReflectionTestUtils.setField(deletedRemaining, "physicalFileStatus", PhysicalFileStatus.DELETED);

        ApplicationAttachment missingPresent = attachmentRepository.findById(missingPresentId).orElseThrow();
        ReflectionTestUtils.setField(missingPresent, "physicalFileStatus", PhysicalFileStatus.MISSING);

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        attachmentRepository.save(ApplicationAttachment.createStored(
                application,
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null,
                "invalid-absolute.pdf",
                "invalid-absolute.pdf",
                attachmentProperties.getStorageRoot().resolve("bad.pdf").toAbsolutePath().toString(),
                "application/pdf",
                1L,
                100
        ));
        attachmentRepository.save(ApplicationAttachment.createStored(
                application,
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null,
                "invalid-traversal.pdf",
                "invalid-traversal.pdf",
                "../outside.pdf",
                "application/pdf",
                1L,
                101
        ));
        attachmentRepository.saveAndFlush(deletedRemaining);
        attachmentRepository.saveAndFlush(missingPresent);

        Path orphan = attachmentProperties.getStorageRoot()
                .resolve("applications/999/2026/06/15/orphan.pdf");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "orphan", StandardCharsets.UTF_8);
        Path unmanaged = attachmentProperties.getStorageRoot().resolve("tmp/unmanaged.txt");
        Files.createDirectories(unmanaged.getParent());
        Files.writeString(unmanaged, "ignored", StandardCharsets.UTF_8);

        AttachmentStorageHealthScanResponse response = scanService.scanDryRun();

        assertThat(response.storedRowCount()).isEqualTo(3);
        assertThat(response.deletedRowCount()).isEqualTo(1);
        assertThat(response.missingRowCount()).isEqualTo(1);
        assertThat(response.storedMissingPhysicalFileCount()).isEqualTo(1);
        assertThat(response.deletedPhysicalFileRemainingCount()).isEqualTo(1);
        assertThat(response.orphanPhysicalFileCount()).isEqualTo(1);
        assertThat(response.invalidStoragePathCount()).isEqualTo(2);
        assertThat(categories(response)).contains(
                AttachmentStorageHealthIssueType.STORED_MISSING_PHYSICAL_FILE.name(),
                AttachmentStorageHealthIssueType.DELETED_PHYSICAL_FILE_REMAINING.name(),
                AttachmentStorageHealthIssueType.MISSING_ROW_PHYSICAL_FILE_PRESENT.name(),
                AttachmentStorageHealthIssueType.ORPHAN_PHYSICAL_FILE.name(),
                AttachmentStorageHealthIssueType.INVALID_STORAGE_PATH.name(),
                AttachmentStorageHealthIssueType.IGNORED_UNMANAGED_FILE.name()
        );
        assertThat(response.issues())
                .extracting(AttachmentStorageHealthIssueResponse::fileKeyHash)
                .filteredOn(hash -> hash != null)
                .allMatch(hash -> hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void scan은_purge_saga_진행중_파일을_orphan으로_오탐하지_않는다() {
        Applicant applicant = createApplicant("health-deferred", "Health Deferred");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long pendingId = upload(applicant, applicationId, "purge-pending.pdf", "pending");
        Long failedId = upload(applicant, applicationId, "purge-failed.pdf", "failed");

        ApplicationAttachment pending = attachmentRepository.findById(pendingId).orElseThrow();
        ReflectionTestUtils.setField(pending, "physicalFileStatus", PhysicalFileStatus.BINARY_DELETE_PENDING);
        ApplicationAttachment failed = attachmentRepository.findById(failedId).orElseThrow();
        ReflectionTestUtils.setField(failed, "physicalFileStatus", PhysicalFileStatus.BINARY_DELETE_FAILED);
        attachmentRepository.saveAndFlush(pending);
        attachmentRepository.saveAndFlush(failed);

        AttachmentStorageHealthScanResponse response = scanService.scanDryRun();

        // BINARY_DELETE_PENDING/FAILED 의 파일은 9e 재처리 대상(known but deferred) —
        // ORPHAN 오탐 금지 + row 이슈/카운트에도 미포함(9d-2 리뷰 Medium 1).
        assertThat(response.orphanPhysicalFileCount()).isZero();
        assertThat(response.storedRowCount()).isZero();
        assertThat(response.deletedRowCount()).isZero();
        assertThat(response.missingRowCount()).isZero();
        assertThat(response.issues()).isEmpty();
    }

    @Test
    void scan_excludes_metadata_only_rows_and_does_not_mutate_db_or_files() throws Exception {
        Applicant applicant = createApplicant("health-dry-run", "Health Dry Run");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        attachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("metadata.pdf", 0)))
        );
        Long deletedRemainingId = upload(applicant, applicationId, "deleted-remaining.pdf", "deleted");
        ApplicationAttachment deletedRemaining = attachmentRepository.findById(deletedRemainingId).orElseThrow();
        ReflectionTestUtils.setField(deletedRemaining, "physicalFileStatus", PhysicalFileStatus.DELETED);
        attachmentRepository.saveAndFlush(deletedRemaining);
        Path deletedPhysicalFile = attachmentProperties.getStorageRoot().resolve(deletedRemaining.getStoragePath());

        AttachmentStorageHealthScanResponse response = scanService.scanDryRun();

        assertThat(response.storedRowCount()).isZero();
        assertThat(response.deletedRowCount()).isEqualTo(1);
        assertThat(response.deletedPhysicalFileRemainingCount()).isEqualTo(1);
        assertThat(Files.exists(deletedPhysicalFile)).isTrue();
        assertThat(attachmentRepository.findById(deletedRemainingId).orElseThrow().getPhysicalFileStatus())
                .isEqualTo(PhysicalFileStatus.DELETED);
    }

    @Test
    void response_does_not_expose_storage_internals() {
        List<String> responseFields = Arrays.stream(AttachmentStorageHealthScanResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        List<String> issueFields = Arrays.stream(AttachmentStorageHealthIssueResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(responseFields).doesNotContain("storageRoot", "absolutePath", "storagePath", "storedFileName");
        assertThat(issueFields).doesNotContain("storageRoot", "absolutePath", "storagePath", "storedFileName");
    }

    private List<String> categories(AttachmentStorageHealthScanResponse response) {
        return response.issues().stream()
                .map(AttachmentStorageHealthIssueResponse::category)
                .toList();
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
