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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
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
        "recruit.attachment.storage-root=build/test-attachments/application-attachment-service"
})
@Transactional
class ApplicationAttachmentServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationAttachmentService applicationAttachmentService;

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
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Test
    void replace_attachments_success_and_get_success() {
        Applicant applicant = createApplicant("attachment-success", "Attachment Success");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());

        List<AttachmentResponse> responses = applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(
                        attachment("portfolio.pdf", AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, null, 1),
                        attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)
                ))
        );
        List<AttachmentResponse> getResponses = applicationAttachmentService.getAttachments(applicant.getId(), applicationId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(AttachmentResponse::originalFileName)
                .containsExactly("resume.pdf", "portfolio.pdf");
        assertThat(getResponses).extracting(AttachmentResponse::attachmentId)
                .containsExactlyElementsOf(responses.stream().map(AttachmentResponse::attachmentId).toList());
    }

    @Test
    void replace_deletes_existing_attachments_and_empty_list_is_allowed() {
        Applicant applicant = createApplicant("attachment-replace", "Attachment Replace");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("old.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        );
        List<Long> oldIds = attachmentRepository.findByJobApplicationId(applicationId).stream()
                .map(ApplicationAttachment::getId)
                .toList();

        List<AttachmentResponse> responses = applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of())
        );

        assertThat(responses).isEmpty();
        assertThat(attachmentRepository.findByJobApplicationId(applicationId)).isEmpty();
        assertThat(attachmentRepository.findAll()).extracting(ApplicationAttachment::getId)
                .doesNotContainAnyElementsOf(oldIds);
    }

    @Test
    void replace_preserves_stored_rows_and_replaces_only_metadata_rows() {
        Applicant applicant = createApplicant("attachment-preserve-stored", "Attachment Preserve Stored");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("metadata.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        );
        ApplicationAttachment stored = attachmentRepository.save(ApplicationAttachment.createStored(
                jobApplicationRepository.findById(applicationId).orElseThrow(),
                AttachmentType.PORTFOLIO,
                ApplicationSectionType.APPLICATION,
                null,
                "portfolio.pdf",
                "stored-portfolio.pdf",
                "applications/" + applicationId + "/portfolio.pdf",
                "application/pdf",
                2048L,
                5
        ));

        List<AttachmentResponse> responses = applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("new-metadata.pdf", AttachmentType.ETC, ApplicationSectionType.APPLICATION, null, 1)))
        );

        assertThat(responses).extracting(AttachmentResponse::attachmentId).contains(stored.getId());
        assertThat(attachmentRepository.findByJobApplicationId(applicationId))
                .extracting(ApplicationAttachment::getPhysicalFileStatus)
                .containsExactlyInAnyOrder(PhysicalFileStatus.METADATA_ONLY, PhysicalFileStatus.STORED);
        ApplicationAttachment storedAfterReplace = attachmentRepository.findById(stored.getId()).orElseThrow();
        assertThat(storedAfterReplace.getAttachmentType()).isEqualTo(AttachmentType.PORTFOLIO);
        assertThat(storedAfterReplace.getSortOrder()).isEqualTo(5);
    }

    @Test
    void replace_fails_when_metadata_sort_order_conflicts_with_stored_row() {
        Applicant applicant = createApplicant("attachment-conflict-stored", "Attachment Conflict Stored");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        attachmentRepository.save(ApplicationAttachment.createStored(
                jobApplicationRepository.findById(applicationId).orElseThrow(),
                AttachmentType.PORTFOLIO,
                ApplicationSectionType.APPLICATION,
                null,
                "portfolio.pdf",
                "stored-portfolio.pdf",
                "applications/" + applicationId + "/portfolio.pdf",
                "application/pdf",
                2048L,
                5
        ));

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("metadata-conflict.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 5)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_application_is_withdrawn() {
        Applicant submittedApplicant = createApplicant("attachment-submitted", "Attachment Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting());
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(submittedApplicationId).orElseThrow());
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatCode(() -> applicationAttachmentService.replaceAttachments(
                submittedApplicant.getId(),
                submittedApplicationId,
                new AttachmentReplaceRequest(List.of(attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        )).doesNotThrowAnyException();

        Applicant withdrawnApplicant = createApplicant("attachment-withdrawn", "Attachment Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting());
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(withdrawnApplicationId).orElseThrow());
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new AttachmentReplaceRequest(List.of(attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_replace() {
        Applicant owner = createApplicant("attachment-owner", "Attachment Owner");
        Applicant other = createApplicant("attachment-other", "Attachment Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());

        assertThatThrownBy(() -> applicationAttachmentService.getAttachments(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                other.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void replace_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("attachment-before", "Attachment Before");
        Long beforePostingId = createPublishedJobPosting();
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                beforeApplicant.getId(),
                beforeApplicationId,
                new AttachmentReplaceRequest(List.of(attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("attachment-after", "Attachment After");
        Long afterPostingId = createPublishedJobPosting();
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                afterApplicant.getId(),
                afterApplicationId,
                new AttachmentReplaceRequest(List.of(attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("attachment-draft", "Attachment Draft");
        Long draftPostingId = createPublishedJobPosting();
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                draftApplicant.getId(),
                draftApplicationId,
                new AttachmentReplaceRequest(List.of(attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_required_fields_or_lengths_are_invalid() {
        Applicant applicant = createApplicant("attachment-validation", "Attachment Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(null)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(null, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "/tmp/a.pdf", "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, null, null, "a.pdf", "a.pdf", "/tmp/a.pdf", "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "", "a.pdf", "/tmp/a.pdf", "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "", "/tmp/a.pdf", "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "", "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "/tmp/a.pdf", "", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "/tmp/a.pdf", "application/pdf", null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "/tmp/a.pdf", "application/pdf", 0L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "/tmp/a.pdf", "application/pdf", 1L, null)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("a.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, 0), attachment("b.pdf", AttachmentType.PORTFOLIO, ApplicationSectionType.APPLICATION, null, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a".repeat(256), "a.pdf", "/tmp/a.pdf", "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a".repeat(256), "/tmp/a.pdf", "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "a".repeat(1001), "application/pdf", 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, null, "a.pdf", "a.pdf", "/tmp/a.pdf", "a".repeat(101), 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_section_record_policy_is_invalid() {
        Applicant applicant = createApplicant("attachment-section", "Attachment Section");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("resume.pdf", AttachmentType.RESUME, ApplicationSectionType.APPLICATION, 1L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("transcript.pdf", AttachmentType.TRANSCRIPT, ApplicationSectionType.EDUCATION, 0L, 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        List<AttachmentResponse> responses = applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("transcript.pdf", AttachmentType.TRANSCRIPT, ApplicationSectionType.EDUCATION, null, 0)))
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).sectionRecordId()).isNull();
    }

    @Test
    void response_does_not_expose_internal_storage_fields() {
        List<String> componentNames = Arrays.stream(AttachmentResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain("storedFileName", "storagePath", "physicalFileStatus", "downloadAvailable");
    }

    @Test
    void replace_rejects_client_supplied_storage_fields() {
        Applicant applicant = createApplicant("attachment-forbidden-storage", "Attachment Forbidden Storage");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(
                        AttachmentType.RESUME,
                        ApplicationSectionType.APPLICATION,
                        null,
                        "resume.pdf",
                        "stored-resume.pdf",
                        null,
                        "application/pdf",
                        1024L,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationAttachmentService.replaceAttachments(
                applicant.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(new AttachmentRequest(
                        AttachmentType.RESUME,
                        ApplicationSectionType.APPLICATION,
                        null,
                        "resume.pdf",
                        null,
                        "applications/1/resume.pdf",
                        "application/pdf",
                        1024L,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);
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

    private AttachmentRequest attachment(
            String originalFileName,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId,
            Integer sortOrder
    ) {
        return new AttachmentRequest(
                attachmentType,
                sectionType,
                sectionRecordId,
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
    }

    private void setJobPostingStatus(Long jobPostingId, JobPostingStatus status) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        ReflectionTestUtils.setField(jobPosting, "status", status);
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
