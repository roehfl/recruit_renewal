package com.shinyoung.recruit.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.PdfProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 일괄 지원서 PDF 다운로드 엔드포인트(S3). 권한/헤더/zip 내용/상한/감사를 고정한다. */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationPdfBulkControllerTest {

    private static final String PATH = "/api/admin/applications/pdf/bulk";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private PdfProperties pdfProperties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 선택한_지원서들이_zip으로_내려오고_보안_헤더가_붙는다() throws Exception {
        JobApplication first = saveSubmittedApplication("홍길동");
        JobApplication second = saveSubmittedApplication("김철수");

        MvcResult result = performBulk(post(PATH)
                .with(authentication(adminAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(first.getId(), second.getId())));

        assertThat(result.getResponse().getContentType()).isEqualTo("application/zip");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("applications-").contains(".zip");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");

        List<String> names = entryNames(result.getResponse().getContentAsByteArray());
        assertThat(names).containsExactly(
                first.getId() + "_홍길동.pdf",
                second.getId() + "_김철수.pdf");
    }

    @Test
    void 상한을_넘으면_400과_안내_메시지를_준다() throws Exception {
        int max = pdfProperties.getBulkMaxCount();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i <= max; i++) {
            ids.add((long) (900000 + i));
        }

        mockMvc.perform(post(PATH)
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ids.toArray(new Long[0]))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 빈_목록은_400이다() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_지원서가_섞이면_전체가_실패한다() throws Exception {
        JobApplication application = saveSubmittedApplication("홍길동");

        mockMvc.perform(post(PATH)
                        .with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(application.getId(), 999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 지원자와_익명은_차단된다() throws Exception {
        JobApplication application = saveSubmittedApplication("차단대상");
        Applicant applicant = saveApplicant("Blocked", "ci-" + UUID.randomUUID());

        mockMvc.perform(post(PATH)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(application.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(PATH)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(application.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 담긴_지원서마다_감사_로그가_남는다() throws Exception {
        Logger auditLogger = (Logger) LoggerFactory.getLogger("recruit.audit.pdf");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            JobApplication first = saveSubmittedApplication("감사1");
            JobApplication second = saveSubmittedApplication("감사2");

            performBulk(post(PATH)
                    .with(authentication(adminAuthentication()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(first.getId(), second.getId())));

            List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anyMatch(m -> m.contains("applicationId=" + first.getId()));
            assertThat(messages).anyMatch(m -> m.contains("applicationId=" + second.getId()));
        } finally {
            auditLogger.detachAppender(appender);
        }
    }

    // ---------- helpers ----------

    /** StreamingResponseBody 는 비동기라 asyncDispatch 로 한 번 더 디스패치해야 본문이 채워진다. */
    private MvcResult performBulk(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult started = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn();
    }

    private String body(Long... applicationIds) {
        StringBuilder builder = new StringBuilder("{\"applicationIds\":[");
        for (int i = 0; i < applicationIds.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(applicationIds[i]);
        }
        return builder.append("]}").toString();
    }

    private List<String> entryNames(byte[] zipBytes) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private JobApplication saveSubmittedApplication(String name) {
        JobPosting jobPosting = saveJobPosting();
        Applicant applicant = saveApplicant(name, "ci-" + UUID.randomUUID());
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                name,
                jobPosting.getTitle(),
                jobPosition.getPositionName());
        application.submit(LocalDateTime.now().minusHours(1));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private Applicant saveApplicant(String name, String ci) {
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setEmail("bulk-" + UUID.randomUUID() + "@example.com");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create(
                "Posting " + UUID.randomUUID(),
                "Content",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "pdf-bulk-" + UUID.randomUUID(),
                "Recruit",
                "Pdf Bulk Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
