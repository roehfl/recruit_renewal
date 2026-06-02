package com.shinyoung.recruit.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 07f 하드닝: Application PDF의 보안 불변식을 회귀로 고정한다.
 * - 템플릿이 {@code th:utext}를 쓰지 않고 외부 resource(src/href/url http) 로드가 없음(convention).
 * - applicant free-text가 HTML로 해석되지 않고 escape되어 출력됨(injection 방어).
 * - PDF 생성 시 audit 로그가 남음.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationPdfSecurityHardeningTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/application-pdf.html");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void template_uses_only_th_text_and_no_external_resources() throws Exception {
        String template = Files.readString(TEMPLATE);

        // th:utext 금지(HTML injection 차단).
        assertThat(template).doesNotContain("th:utext");
        // 외부 URL resource(src/href/css url) 로드 금지. xmlns 네임스페이스 선언은 대상이 아니다.
        assertThat(template).doesNotContainPattern("(?i)(src|href)\\s*=\\s*[\"']?https?://");
        assertThat(template).doesNotContainPattern("(?i)url\\(\\s*[\"']?https?://");
    }

    @Test
    void applicant_free_text_is_escaped_not_interpreted_as_html() throws Exception {
        // HTML 태그로 감싼 마커. escape되면 마커가 텍스트로 남고, HTML로 해석되면 태그가 소거되어 사라진다.
        JobApplication application = saveSubmittedApplication("<ZZINJECTZZ>", "ci-" + UUID.randomUUID());

        MvcResult result = mockMvc.perform(get("/api/admin/applications/{id}/pdf", application.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andReturn();

        String text = extractText(result.getResponse().getContentAsByteArray());
        // 마커가 텍스트로 보존됨 = th:text가 escape함(태그 해석 안 됨).
        assertThat(text).contains("ZZINJECTZZ");
    }

    @Test
    void pdf_generation_writes_audit_log() throws Exception {
        Logger auditLogger = (Logger) LoggerFactory.getLogger("recruit.audit.pdf");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            JobApplication application = saveSubmittedApplication("감사대상", "ci-" + UUID.randomUUID());

            mockMvc.perform(get("/api/admin/applications/{id}/pdf", application.getId())
                            .with(authentication(adminAuthentication())))
                    .andExpect(status().isOk());

            assertThat(appender.list)
                    .anySatisfy(event -> {
                        String message = event.getFormattedMessage();
                        assertThat(message).contains("eventType=APPLICATION_PDF");
                        assertThat(message).contains("applicationId=" + application.getId());
                    });
        } finally {
            auditLogger.detachAppender(appender);
        }
    }

    // ---------- helpers ----------

    private String extractText(byte[] pdf) throws Exception {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private JobApplication saveSubmittedApplication(String name, String ci) {
        JobPosting jobPosting = saveJobPosting();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setEmail("hardening-" + UUID.randomUUID() + "@example.com");
        applicant.setPhoneNumber("01000000000");
        applicantRepository.saveAndFlush(applicant);

        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                name,
                jobPosting.getTitle(),
                jobPosition.getPositionName());
        application.submit(start().minusHours(1));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create(
                "Posting " + UUID.randomUUID(),
                "Content",
                start().minusDays(1),
                start().plusDays(10));
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "pdf-hardening-" + UUID.randomUUID(),
                "Recruit",
                "Pdf Hardening Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
