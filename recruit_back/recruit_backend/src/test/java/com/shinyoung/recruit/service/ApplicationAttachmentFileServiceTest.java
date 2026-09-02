package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
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
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.attachment.storage-root=build/test-attachments/application-attachment-file-service",
        "recruit.attachment.max-file-size=5KB",
        "recruit.attachment.max-files-per-application=2",
        "recruit.attachment.max-total-size-per-application=3KB"
})
@Transactional
class ApplicationAttachmentFileServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationAttachmentFileService applicationAttachmentFileService;

    @Autowired
    private ApplicationAttachmentService applicationAttachmentService;

    @Autowired
    private AttachmentStorageService attachmentStorageService;

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

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Test
    void upload_success_creates_stored_row_and_append_only_sort_order() {
        Applicant applicant = createApplicant("upload-success", "Upload Success");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(
                        attachment("metadata.pdf", AttachmentType.RESUME, 2)
                ))
        );

        AttachmentResponse response = applicationAttachmentFileService.upload(
                applicant.getId(),
                applicationId,
                file("portfolio.pdf", "application/pdf", "content"),
                AttachmentType.PORTFOLIO,
                ApplicationSectionType.APPLICATION,
                null
        );

        ApplicationAttachment stored = attachmentRepository.findById(response.attachmentId()).orElseThrow();
        assertThat(response.originalFileName()).isEqualTo("portfolio.pdf");
        assertThat(response.sortOrder()).isEqualTo(3);
        assertThat(stored.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.STORED);
        assertThat(stored.getStoredFileName()).isNotBlank().isNotEqualTo("portfolio.pdf");
        assertThat(stored.getStoragePath()).startsWith("applications/" + applicationId + "/");
        assertThat(attachmentStorageService.exists(stored.getStoragePath())).isTrue();
    }

    @Test
    void upload_response_does_not_expose_storage_or_download_fields() {
        List<String> componentNames = Arrays.stream(AttachmentResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain(
                "storedFileName",
                "storagePath",
                "physicalFileStatus",
                "downloadAvailable"
        );
    }

    @Test
    void upload_limits_count_only_stored_rows() {
        Applicant applicant = createApplicant("upload-count", "Upload Count");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(
                        attachment("metadata-a.pdf", AttachmentType.RESUME, 0),
                        attachment("metadata-b.pdf", AttachmentType.PORTFOLIO, 1)
                ))
        );

        applicationAttachmentFileService.upload(applicant.getId(), applicationId, file("a.pdf", "application/pdf", "a"), AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null);
        applicationAttachmentFileService.upload(applicant.getId(), applicationId, file("b.pdf", "application/pdf", "b"), AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, null);

        assertThatThrownBy(() -> applicationAttachmentFileService.upload(
                applicant.getId(),
                applicationId,
                file("c.pdf", "application/pdf", "c"),
                AttachmentType.ETC,
                ApplicationSectionType.APPLICATION,
                null
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void upload_limits_total_size_only_stored_rows() {
        Applicant applicant = createApplicant("upload-total-size", "Upload Total Size");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachmentWithSize("metadata-huge.pdf", AttachmentType.RESUME, 0, 100_000L)))
        );

        applicationAttachmentFileService.upload(
                applicant.getId(),
                applicationId,
                file("a.pdf", "application/pdf", "a".repeat(2048)),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        );

        assertThatThrownBy(() -> applicationAttachmentFileService.upload(
                applicant.getId(),
                applicationId,
                file("b.pdf", "application/pdf", "b".repeat(2048)),
                AttachmentType.PORTFOLIO,
                ApplicationSectionType.APPLICATION,
                null
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void upload_fails_for_invalid_file_policy() {
        Applicant applicant = createApplicant("upload-invalid-file", "Upload Invalid File");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());

        assertThatThrownBy(() -> applicationAttachmentFileService.upload(
                applicant.getId(),
                applicationId,
                file("folder/resume.pdf", "application/pdf", "content"),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentFileService.upload(
                applicant.getId(),
                applicationId,
                file("resume.exe", "application/octet-stream", "content"),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentFileService.upload(
                applicant.getId(),
                applicationId,
                file("NUL.pdf", "application/pdf", "content"),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void upload_fails_when_application_is_not_writable_or_not_owned() {
        Applicant owner = createApplicant("upload-owner", "Upload Owner");
        Applicant other = createApplicant("upload-other", "Upload Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());

        assertThatThrownBy(() -> applicationAttachmentFileService.upload(
                other.getId(),
                applicationId,
                file("resume.pdf", "application/pdf", "content"),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        )).isInstanceOf(JobApplicationNotFoundException.class);

        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(owner.getId(), applicationId);

        assertThatCode(() -> applicationAttachmentFileService.upload(
                owner.getId(),
                applicationId,
                file("resume.pdf", "application/pdf", "content"),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        )).doesNotThrowAnyException();
    }

    private MockMultipartFile file(String originalFileName, String contentType, String content) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                contentType,
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
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
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

    private AttachmentRequest attachment(String originalFileName, AttachmentType attachmentType, Integer sortOrder) {
        return attachmentWithSize(originalFileName, attachmentType, sortOrder, 1024L);
    }

    private AttachmentRequest attachmentWithSize(
            String originalFileName,
            AttachmentType attachmentType,
            Integer sortOrder,
            Long fileSize
    ) {
        return new AttachmentRequest(
                attachmentType,
                ApplicationSectionType.APPLICATION,
                null,
                originalFileName,
                null,
                null,
                "application/pdf",
                fileSize,
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
