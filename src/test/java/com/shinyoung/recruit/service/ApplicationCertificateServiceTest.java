package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.CertificateReplaceRequest;
import com.shinyoung.recruit.dto.request.CertificateRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.CertificateResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationCertificateServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationCertificateService applicationCertificateService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationCertificateRepository certificateRepository;

    @Test
    void replace_certificates_success_and_get_success() {
        Applicant applicant = createApplicant("certificate-success", "Certificate Success");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        List<CertificateResponse> responses = applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(
                        certificate("SQLD", 1),
                        certificate("정보처리기사", 0)
                ))
        );
        List<CertificateResponse> getResponses = applicationCertificateService.getCertificates(applicant.getId(), applicationId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(CertificateResponse::certificateName)
                .containsExactly("정보처리기사", "SQLD");
        assertThat(getResponses).extracting(CertificateResponse::certificateId)
                .containsExactlyElementsOf(responses.stream().map(CertificateResponse::certificateId).toList());
    }

    @Test
    void replace_deletes_existing_certificates_and_empty_list_is_allowed() {
        Applicant applicant = createApplicant("certificate-replace", "Certificate Replace");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));
        applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(certificate("Old Certificate", 0)))
        );
        List<Long> oldIds = certificateRepository.findByJobApplicationId(applicationId).stream()
                .map(ApplicationCertificate::getId)
                .toList();

        List<CertificateResponse> responses = applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of())
        );

        assertThat(responses).isEmpty();
        assertThat(certificateRepository.findByJobApplicationId(applicationId)).isEmpty();
        assertThat(certificateRepository.findAll()).extracting(ApplicationCertificate::getId)
                .doesNotContainAnyElementsOf(oldIds);
    }

    @Test
    void replace_fails_when_certificate_section_is_disabled() {
        Applicant applicant = createApplicant("certificate-disabled", "Certificate Disabled");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(false));

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(certificate("SQLD", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_application_is_not_draft() {
        Applicant submittedApplicant = createApplicant("certificate-submitted", "Certificate Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, createPublishedJobPosting(true));
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                submittedApplicant.getId(),
                submittedApplicationId,
                new CertificateReplaceRequest(List.of(certificate("SQLD", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("certificate-withdrawn", "Certificate Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, createPublishedJobPosting(true));
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new CertificateReplaceRequest(List.of(certificate("SQLD", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_replace() {
        Applicant owner = createApplicant("certificate-owner", "Certificate Owner");
        Applicant other = createApplicant("certificate-other", "Certificate Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationCertificateService.getCertificates(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                other.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(certificate("SQLD", 0)))
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void replace_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("certificate-before", "Certificate Before");
        Long beforePostingId = createPublishedJobPosting(true);
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                beforeApplicant.getId(),
                beforeApplicationId,
                new CertificateReplaceRequest(List.of(certificate("SQLD", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("certificate-after", "Certificate After");
        Long afterPostingId = createPublishedJobPosting(true);
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                afterApplicant.getId(),
                afterApplicationId,
                new CertificateReplaceRequest(List.of(certificate("SQLD", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("certificate-draft", "Certificate Draft");
        Long draftPostingId = createPublishedJobPosting(true);
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                draftApplicant.getId(),
                draftApplicationId,
                new CertificateReplaceRequest(List.of(certificate("SQLD", 0)))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_required_or_cross_field_validation_fails() {
        Applicant applicant = createApplicant("certificate-validation", "Certificate Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting(true));

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(null)
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(new CertificateRequest(
                        "",
                        "Korea Data Agency",
                        LocalDate.of(2024, 1, 1),
                        null,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(new CertificateRequest(
                        "SQLD",
                        "",
                        LocalDate.of(2024, 1, 1),
                        null,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(new CertificateRequest(
                        "SQLD",
                        "Korea Data Agency",
                        null,
                        null,
                        null,
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(new CertificateRequest(
                        "SQLD",
                        "Korea Data Agency",
                        LocalDate.of(2025, 1, 1),
                        null,
                        LocalDate.of(2024, 12, 31),
                        null,
                        0
                )))
        )).isInstanceOf(InvalidJobApplicationException.class);

        assertThatThrownBy(() -> applicationCertificateService.replaceCertificates(
                applicant.getId(),
                applicationId,
                new CertificateReplaceRequest(List.of(certificate("A", 0), certificate("B", 0)))
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

    private Long createPublishedJobPosting(boolean useCertificate) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(true, true, useCertificate, true, true, true, true)
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

    private CertificateRequest certificate(String certificateName, Integer sortOrder) {
        return new CertificateRequest(
                certificateName,
                "Korea Data Agency",
                LocalDate.of(2024, 1, 1),
                "CERT-001",
                LocalDate.of(2029, 1, 1),
                "A",
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
