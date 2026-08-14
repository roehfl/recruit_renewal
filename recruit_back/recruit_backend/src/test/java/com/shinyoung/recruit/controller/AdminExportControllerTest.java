package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ExcelExportFile;
import com.shinyoung.recruit.service.JobPostingService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminExportControllerTest {

    private static final List<String> EXPECTED_HEADER = List.of(
            "applicationId", "applicantName", "phoneNumber", "email",
            "jobPostingTitle", "jobPositionName", "status",
            "submittedAt", "withdrawnAt", "createdAt", "updatedAt"
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void export_applications_returns_xlsx_with_contact_columns_and_no_sensitive_columns() throws Exception {
        Long jobPostingId = createJobPosting("2026 상반기 신입");
        persistApplication(jobPostingId, "exp-a", "Applicant A", "01011112222", "a@example.com", true, false);
        persistApplication(jobPostingId, "exp-b", "Applicant B", "01033334444", "b@example.com", true, false);

        MvcResult result = performExport(get("/api/admin/applications/export")
                .with(authentication(adminAuthentication())));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).isEqualTo(ExcelExportFile.CONTENT_TYPE);
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("applications-export");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        List<String> header = sheet.get(0);
        assertThat(header).isEqualTo(EXPECTED_HEADER);
        assertThat(header).doesNotContain("ci", "ciHash", "password");

        List<List<String>> dataRows = sheet.subList(1, sheet.size());
        assertThat(dataRows).hasSize(2);
        assertThat(dataRows).anySatisfy(row -> {
            assertThat(row.get(1)).isEqualTo("Applicant A");
            assertThat(row.get(2)).isEqualTo("01011112222");
            assertThat(row.get(3)).isEqualTo("a@example.com");
        });
    }

    @Test
    void export_applications_filters_by_status() throws Exception {
        Long jobPostingId = createJobPosting("status-filter");
        persistApplication(jobPostingId, "st-sub-1", "Sub One", "01000000001", "s1@example.com", true, false);
        persistApplication(jobPostingId, "st-sub-2", "Sub Two", "01000000002", "s2@example.com", true, false);
        persistApplication(jobPostingId, "st-draft", "Draft One", "01000000003", "d1@example.com", false, false);

        MvcResult all = performExport(get("/api/admin/applications/export")
                .with(authentication(adminAuthentication())));
        assertThat(dataRowsOf(all)).hasSize(3);

        MvcResult submitted = performExport(get("/api/admin/applications/export")
                .param("status", "SUBMITTED")
                .with(authentication(adminAuthentication())));
        List<List<String>> submittedRows = dataRowsOf(submitted);
        assertThat(submittedRows).hasSize(2);
        assertThat(submittedRows).allSatisfy(row -> assertThat(row.get(6)).isEqualTo("SUBMITTED"));
    }

    @Test
    void export_applications_filters_by_job_position() throws Exception {
        Long jobPostingId = createJobPostingWithPositions("position-filter", "Backend", "Frontend");
        persistApplicationForPosition(jobPostingId, "Backend", "jp-be-1", "BE One");
        persistApplicationForPosition(jobPostingId, "Backend", "jp-be-2", "BE Two");
        persistApplicationForPosition(jobPostingId, "Frontend", "jp-fe-1", "FE One");

        Long backendPositionId = positionId(jobPostingId, "Backend");

        MvcResult result = performExport(get("/api/admin/applications/export")
                .param("jobPositionId", backendPositionId.toString())
                .with(authentication(adminAuthentication())));

        List<List<String>> dataRows = dataRowsOf(result);
        assertThat(dataRows).hasSize(2);
        assertThat(dataRows).allSatisfy(row -> assertThat(row.get(5)).isEqualTo("Backend"));
    }

    @Test
    void export_applications_by_job_posting_returns_only_that_posting() throws Exception {
        Long postingA = createJobPosting("posting-A");
        Long postingB = createJobPosting("posting-B");
        persistApplication(postingA, "pa-1", "PA One", "01010000001", "pa1@example.com", true, false);
        persistApplication(postingA, "pa-2", "PA Two", "01010000002", "pa2@example.com", true, false);
        persistApplication(postingB, "pb-1", "PB One", "01020000001", "pb1@example.com", true, false);

        MvcResult global = performExport(get("/api/admin/applications/export")
                .with(authentication(adminAuthentication())));
        assertThat(dataRowsOf(global)).hasSize(3);

        MvcResult perPostingA = performExport(get("/api/admin/job-postings/{id}/applications/export", postingA)
                .with(authentication(adminAuthentication())));
        assertThat(dataRowsOf(perPostingA)).hasSize(2);

        MvcResult perPostingB = performExport(get("/api/admin/job-postings/{id}/applications/export", postingB)
                .with(authentication(adminAuthentication())));
        assertThat(dataRowsOf(perPostingB)).hasSize(1);
    }

    @Test
    void export_applications_by_unknown_job_posting_returns_not_found() throws Exception {
        mockMvc.perform(get("/api/admin/job-postings/{id}/applications/export", 999999L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());
    }

    @Test
    void export_escapes_formula_injection_in_free_text_cells() throws Exception {
        Long jobPostingId = createJobPosting("formula");
        persistApplication(jobPostingId, "formula-1", "=cmd()|calc", "01099998888", "f@example.com", true, false);

        MvcResult result = performExport(get("/api/admin/applications/export")
                .with(authentication(adminAuthentication())));

        List<List<String>> dataRows = dataRowsOf(result);
        assertThat(dataRows).hasSize(1);
        assertThat(dataRows.get(0).get(1)).isEqualTo("'=cmd()|calc");
    }

    @Test
    void export_blocks_applicant_and_anonymous() throws Exception {
        Applicant applicant = saveApplicant("blocked-applicant", "Blocked", "01000000099", "blk@example.com");

        mockMvc.perform(get("/api/admin/applications/export")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/applications/export").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult performExport(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult started = mockMvc.perform(builder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn();
    }

    private List<List<String>> dataRowsOf(MvcResult result) throws Exception {
        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        return sheet.subList(1, sheet.size());
    }

    private List<List<String>> readSheet(byte[] bytes) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            int columnCount = sheet.getRow(0).getLastCellNum();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < columnCount; c++) {
                    Cell cell = row == null ? null : row.getCell(c);
                    cells.add(cell == null ? "" : cell.getStringCellValue());
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private Long createJobPosting(String title) {
        return jobPostingService.create(new JobPostingCreateRequest(
                title,
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private Long createJobPostingWithPositions(String title, String... positionNames) {
        List<JobPositionRequest> positions = new ArrayList<>();
        int sortOrder = 1;
        for (String positionName : positionNames) {
            positions.add(new JobPositionRequest(positionName, sortOrder++));
        }
        return jobPostingService.create(new JobPostingCreateRequest(
                title,
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                positions,
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private Long positionId(Long jobPostingId, String positionName) {
        return jobPostingRepository.findDetailById(jobPostingId).orElseThrow()
                .getJobPositions().stream()
                .filter(position -> position.getPositionName().equals(positionName))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }

    private void persistApplication(
            Long jobPostingId,
            String loginId,
            String name,
            String phoneNumber,
            String email,
            boolean submitted,
            boolean withdrawn
    ) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        JobPosition jobPosition = jobPosting.getJobPositions().stream()
                .min(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .orElseThrow();
        persistApplication(jobPostingId, jobPosition, loginId, name, phoneNumber, email, submitted, withdrawn);
    }

    private void persistApplicationForPosition(Long jobPostingId, String positionName, String loginId, String name) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        JobPosition jobPosition = jobPosting.getJobPositions().stream()
                .filter(position -> position.getPositionName().equals(positionName))
                .findFirst()
                .orElseThrow();
        persistApplication(jobPostingId, jobPosition, loginId, name, "01000000000", loginId + "@example.com", true, false);
    }

    private void persistApplication(
            Long jobPostingId,
            JobPosition jobPosition,
            String loginId,
            String name,
            String phoneNumber,
            String email,
            boolean submitted,
            boolean withdrawn
    ) {
        Applicant applicant = saveApplicant(loginId, name, phoneNumber, email);
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();

        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                name,
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
        if (submitted) {
            application.submit(LocalDateTime.of(2026, 5, 10, 10, 0));
        }
        if (withdrawn) {
            application.withdraw(LocalDateTime.of(2026, 5, 12, 10, 0));
        }
        jobApplicationRepository.save(application);
    }

    private Applicant saveApplicant(String loginId, String name, String phoneNumber, String email) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber(phoneNumber);
        applicant.setEmail(email);
        return applicantRepository.save(applicant);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "export-admin",
                "Recruit",
                "Export Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
