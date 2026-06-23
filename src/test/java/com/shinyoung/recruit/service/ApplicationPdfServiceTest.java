package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.response.ApplicationPdfView;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationPdfServiceTest {

    @Autowired
    private ApplicationPdfService applicationPdfService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @MockitoBean
    private ApplicationPdfRenderer pdfRenderer;

    @Test
    void PDF_BasicInfo_존재시_BasicInfo값을_사용한다() {
        // given: Applicant with "Old" values
        Applicant applicant = saveApplicant("Old Name", "01000000000", "applicant@old.com");
        JobApplication application = saveDraftApplication(applicant, "Old Name");

        // BasicInfo row with DIFFERENT values
        ApplicationBasicInfo basicInfo = ApplicationBasicInfo.create(
                application,
                "새이름", null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1),
                "01099998888", null, "basic@new.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null);
        basicInfoRepository.saveAndFlush(basicInfo);

        // stub renderer to return dummy bytes
        given(pdfRenderer.render(any(ApplicationPdfView.class))).willReturn(new byte[]{0x25, 0x50, 0x44, 0x46});

        // when
        applicationPdfService.generate(application.getId());

        // then: captured view should have BasicInfo values
        ArgumentCaptor<ApplicationPdfView> captor = ArgumentCaptor.forClass(ApplicationPdfView.class);
        verify(pdfRenderer).render(captor.capture());
        ApplicationPdfView.Header header = captor.getValue().header();

        assertThat(header.applicantName()).isEqualTo("새이름");
        assertThat(header.phoneNumber()).isEqualTo("01099998888");
        assertThat(header.email()).isEqualTo("basic@new.com");
        // Must NOT use Applicant's live values
        assertThat(header.applicantName()).isNotEqualTo("Old Name");
        assertThat(header.phoneNumber()).isNotEqualTo("01000000000");
        assertThat(header.email()).isNotEqualTo("applicant@old.com");
    }

    @Test
    void PDF_BasicInfo_존재하지만_필드_null이면_Applicant로_fallback하지_않는다() {
        // given: Applicant with NON-null values
        Applicant applicant = saveApplicant("Live Name", "01011112222", "live@applicant.com");
        JobApplication application = saveDraftApplication(applicant, "Live Name");

        // BasicInfo row exists but all PII fields are null (simulates a purged row)
        ApplicationBasicInfo purgedBasicInfo = ApplicationBasicInfo.create(
                application,
                null, null, null, null,
                null,
                null, null, null,
                null, null, null,
                null, null, null, null, null);
        basicInfoRepository.saveAndFlush(purgedBasicInfo);

        given(pdfRenderer.render(any(ApplicationPdfView.class))).willReturn(new byte[]{0x25, 0x50, 0x44, 0x46});

        // when
        applicationPdfService.generate(application.getId());

        // then: header values must be null (NOT the Applicant's live values)
        ArgumentCaptor<ApplicationPdfView> captor = ArgumentCaptor.forClass(ApplicationPdfView.class);
        verify(pdfRenderer).render(captor.capture());
        ApplicationPdfView.Header header = captor.getValue().header();

        assertThat(header.applicantName()).isNull();
        assertThat(header.phoneNumber()).isNull();
        assertThat(header.email()).isNull();
        // Must NOT fall back to Applicant live values
        assertThat(header.applicantName()).isNotEqualTo("Live Name");
        assertThat(header.phoneNumber()).isNotEqualTo("01011112222");
        assertThat(header.email()).isNotEqualTo("live@applicant.com");
    }

    @Test
    void PDF_BasicInfo_없으면_Applicant값으로_fallback한다() {
        // given: Applicant with values, NO BasicInfo row
        Applicant applicant = saveApplicant("Snapshot Name", "01033334444", "snapshot@example.com");
        JobApplication application = saveDraftApplication(applicant, "Snapshot Name");
        // No BasicInfo row saved

        given(pdfRenderer.render(any(ApplicationPdfView.class))).willReturn(new byte[]{0x25, 0x50, 0x44, 0x46});

        // when
        applicationPdfService.generate(application.getId());

        // then: header uses snapshot/Applicant values
        ArgumentCaptor<ApplicationPdfView> captor = ArgumentCaptor.forClass(ApplicationPdfView.class);
        verify(pdfRenderer).render(captor.capture());
        ApplicationPdfView.Header header = captor.getValue().header();

        // name comes from applicantNameSnapshot
        assertThat(header.applicantName()).isEqualTo("Snapshot Name");
        // phone and email come from live Applicant
        assertThat(header.phoneNumber()).isEqualTo("01033334444");
        assertThat(header.email()).isEqualTo("snapshot@example.com");
    }

    // ---------- helpers ----------

    private Applicant saveApplicant(String name, String phone, String email) {
        String ci = "ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("pdf-test-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setPhoneNumber(phone);
        applicant.setEmail(email);
        return applicantRepository.saveAndFlush(applicant);
    }

    private JobApplication saveDraftApplication(Applicant applicant, String nameSnapshot) {
        JobPosting jobPosting = JobPosting.create(
                "Test Posting " + UUID.randomUUID(),
                "Content",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59));
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Engineer", 1)));
        jobPostingRepository.saveAndFlush(jobPosting);

        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                nameSnapshot,
                jobPosting.getTitle(),
                jobPosition.getPositionName());
        return jobApplicationRepository.saveAndFlush(application);
    }
}
