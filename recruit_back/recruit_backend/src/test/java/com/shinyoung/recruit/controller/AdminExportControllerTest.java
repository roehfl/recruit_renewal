package com.shinyoung.recruit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.enumeration.AuditActionType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminExportControllerTest {

    private static final List<String> EXPECTED_HEADER = List.of(
            "수험번호", "지원분야", "근무지", "지원상태", "최종제출일시", "최신 전형결과",
            "이름", "생년월일", "나이", "휴대폰", "이메일", "최종학력", "최종학교", "졸업년월"
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

    @Autowired
    private ActivityLogRepository activityLogRepository;

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

        List<Map<String, String>> dataRows = recordsOf(sheet);
        assertThat(dataRows).hasSize(2);
        assertThat(dataRows).anySatisfy(row -> {
            assertThat(row.get("이름")).isEqualTo("Applicant A");
            assertThat(row.get("휴대폰")).isEqualTo("01011112222");
            assertThat(row.get("이메일")).isEqualTo("a@example.com");
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
        assertThat(dataRecordsOf(all)).hasSize(3);

        MvcResult submitted = performExport(get("/api/admin/applications/export")
                .param("status", "SUBMITTED")
                .with(authentication(adminAuthentication())));
        List<Map<String, String>> submittedRows = dataRecordsOf(submitted);
        assertThat(submittedRows).hasSize(2);
        assertThat(submittedRows).allSatisfy(row -> assertThat(row.get("지원상태")).isEqualTo("제출 완료"));
    }

    /*
     * export 는 오랫동안 jobPostingId/jobPositionId/status 3개만 지원해, 화면에서 이름으로 검색한 뒤
     * 엑셀을 받으면 필터가 빠진 전체 결과가 내려갔다. 목록 조회와 같은 조건을 쓰는지 고정한다.
     */
    @Test
    void export_applications_filters_by_name_like_the_list_query() throws Exception {
        Long jobPostingId = createJobPosting("name-filter");
        persistApplication(jobPostingId, "nm-1", "홍길동", "01000000001", "n1@example.com", true, false);
        persistApplication(jobPostingId, "nm-2", "홍길순", "01000000002", "n2@example.com", true, false);
        persistApplication(jobPostingId, "nm-3", "김철수", "01000000003", "n3@example.com", true, false);

        MvcResult all = performExport(get("/api/admin/applications/export")
                .with(authentication(adminAuthentication())));
        assertThat(dataRecordsOf(all)).hasSize(3);

        MvcResult filtered = performExport(get("/api/admin/applications/export")
                .param("name", "홍길")
                .with(authentication(adminAuthentication())));
        List<Map<String, String>> rows = dataRecordsOf(filtered);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("이름")).startsWith("홍길"));
    }

    @Test
    void export_applications_filters_by_phone_number_ignoring_hyphen() throws Exception {
        Long jobPostingId = createJobPosting("phone-filter");
        persistApplication(jobPostingId, "ph-1", "대상자", "010-1234-5678", "p1@example.com", true, false);
        persistApplication(jobPostingId, "ph-2", "비대상자", "01099998888", "p2@example.com", true, false);

        MvcResult filtered = performExport(get("/api/admin/applications/export")
                .param("phoneNumber", "01012345678")
                .with(authentication(adminAuthentication())));

        List<Map<String, String>> rows = dataRecordsOf(filtered);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("이름")).isEqualTo("대상자");
    }

    @Test
    void export_applications_rejects_invalid_enum_filter() throws Exception {
        createJobPosting("invalid-enum");

        mockMvc.perform(get("/api/admin/applications/export")
                        .param("applicationType", "NOT_A_TYPE")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest());
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

        List<Map<String, String>> dataRows = dataRecordsOf(result);
        assertThat(dataRows).hasSize(2);
        assertThat(dataRows).allSatisfy(row -> assertThat(row.get("지원분야")).isEqualTo("Backend"));
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
        assertThat(dataRecordsOf(global)).hasSize(3);

        MvcResult perPostingA = performExport(get("/api/admin/job-postings/{id}/applications/export", postingA)
                .with(authentication(adminAuthentication())));
        assertThat(dataRecordsOf(perPostingA)).hasSize(2);

        MvcResult perPostingB = performExport(get("/api/admin/job-postings/{id}/applications/export", postingB)
                .with(authentication(adminAuthentication())));
        assertThat(dataRecordsOf(perPostingB)).hasSize(1);
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

        List<Map<String, String>> dataRows = dataRecordsOf(result);
        assertThat(dataRows).hasSize(1);
        assertThat(dataRows.get(0).get("이름")).isEqualTo("'=cmd()|calc");
    }

    @Test
    void export_applications_writes_only_requested_columns_in_catalog_order() throws Exception {
        Long jobPostingId = createJobPosting("columns-select");
        persistApplication(jobPostingId, "col-1", "컬럼대상", "01012340000", "col@example.com", true, false);

        // job-posting-scoped 엔드포인트를 써서 audit 조회도 이 jobPostingId 로 좁힌다(다른 테스트의 export 로그와 섞이지 않게).
        // 대소문자/공백 섞인 입력도 trim+upper 로 정규화되고, 출력은 요청 순서가 아니라 카탈로그 선언 순서를 따른다.
        MvcResult result = performExport(get("/api/admin/job-postings/{id}/applications/export", jobPostingId)
                .param("columns", " email , APPLICATION_ID,name")
                .with(authentication(adminAuthentication())));

        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).containsExactly("수험번호", "이름", "이메일");
        assertThat(sheet.get(1).get(1)).isEqualTo("컬럼대상");
        assertThat(sheet.get(1).get(2)).isEqualTo("col@example.com");

        // 감사 로그에도 정규화된 컬럼 key 가 카탈로그 순서로 정확히 남는지 end-to-end 로 확인한다.
        // metadataJson 안에 filtersSafeJson 이 escape 된 JSON 문자열로 중첩되므로 파싱해서 꺼낸다 — 단순
        // indexOf("NAME") 는 JOB_POSITION_NAME 에도 매치되어 14개 기본 컬럼이 감사된 회귀도 통과시킨다.
        ActivityLog auditLog = latestExportApplicationsLog(jobPostingId);
        JsonNode metadata = new ObjectMapper().readTree(auditLog.getMetadataJson());
        String filtersSafeJson = metadata.get("filtersSafeJson").asText();
        assertThat(filtersSafeJson).contains("\"columns\":[\"APPLICATION_ID\",\"NAME\",\"EMAIL\"]");
    }

    @Test
    void export_applications_rejects_unknown_column() throws Exception {
        mockMvc.perform(get("/api/admin/applications/export")
                        .param("columns", "APPLICATION_ID,NOT_A_COLUMN")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("NOT_A_COLUMN")));
    }

    @Test
    void export_application_columns_returns_catalog_in_declaration_order() throws Exception {
        mockMvc.perform(get("/api/admin/applications/export/columns")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].group").value("지원사항"))
                .andExpect(jsonPath("$.data[0].columns[0].key").value("APPLICATION_ID"))
                .andExpect(jsonPath("$.data[0].columns[0].label").value("수험번호"))
                .andExpect(jsonPath("$.data[0].columns[0].defaultSelected").value(true))
                .andExpect(jsonPath("$.data[5].group").value("다건 요약"));
    }

    @Test
    void export_blocks_applicant_and_anonymous() throws Exception {
        Applicant applicant = saveApplicant("blocked-applicant", "Blocked", "01000000099", "blk@example.com");

        mockMvc.perform(get("/api/admin/applications/export")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/applications/export").with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/applications/export/columns")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());
    }

    private MvcResult performExport(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult started = mockMvc.perform(builder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn();
    }

    private List<Map<String, String>> dataRecordsOf(MvcResult result) throws Exception {
        return recordsOf(readSheet(result.getResponse().getContentAsByteArray()));
    }

    /** 헤더 라벨 → 셀 값. 컬럼 구성이 바뀌어도 헤더 이름으로 값을 찾게 한다. */
    private List<Map<String, String>> recordsOf(List<List<String>> sheet) {
        List<String> header = sheet.get(0);
        List<Map<String, String>> records = new ArrayList<>();
        for (List<String> row : sheet.subList(1, sheet.size())) {
            Map<String, String> record = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                record.put(header.get(c), row.get(c));
            }
            records.add(record);
        }
        return records;
    }

    /**
     * 가장 최근 EXPORT_APPLICATIONS 감사 로그 1건. {@code recordRequiresNew} 는 별도 트랜잭션에서 커밋되므로
     * 테스트 메서드 트랜잭션(rollback 대상)과 무관하게 조회된다. 다른 테스트가 남긴 로그와 섞이지 않도록
     * actionType + jobPostingId 로 거르고 occurredAt/id 내림차순 1건만 가져온다(repository.search 는 이미
     * 그 순서로 정렬).
     */
    private ActivityLog latestExportApplicationsLog(Long jobPostingId) {
        Page<ActivityLog> page = activityLogRepository.search(
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(5),
                null,
                AuditActionType.EXPORT_APPLICATIONS,
                null,
                null,
                jobPostingId,
                null,
                PageRequest.of(0, 1));
        return page.getContent().get(0);
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
