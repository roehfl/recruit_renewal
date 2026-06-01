package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ExcelExportFile;
import com.shinyoung.recruit.service.InterviewEvaluationAdminService;
import com.shinyoung.recruit.service.StageResultService;
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
import java.util.List;
import java.util.UUID;

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
class AdminDatasetExportControllerTest {

    private static final List<String> STAGE_RESULT_HEADER = List.of(
            "stageResultId", "stageId", "applicationId", "applicantName", "jobPositionId",
            "jobPositionName", "applicationStatus", "resultStatus", "score", "comment",
            "submittedAt", "decidedAt"
    );

    private static final List<String> INTERVIEW_HEADER = List.of(
            "interviewId", "jobPostingTitle", "stageName", "stageType", "groupName",
            "startDateTime", "endDateTime", "method", "locationName", "roomName",
            "onlineMeetingUrl", "status", "candidateCount", "interviewerCount"
    );

    private static final List<String> EVALUATION_HEADER = List.of(
            "interviewId", "groupName", "applicantName", "positionName", "interviewerName",
            "status", "grade", "recommendation", "comment", "submittedAt"
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewParticipantRepository participantRepository;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private InterviewEvaluationAdminService evaluationAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ---------- stage results ----------

    @Test
    void export_stage_results_returns_xlsx_with_list_parity_columns() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        saveSubmittedApplication(jobPosting, "Result A");
        saveSubmittedApplication(jobPosting, "Result B");
        stageResultService.initialize(stage.getId());

        MvcResult result = performExport(get("/api/admin/stages/{stageId}/results/export", stage.getId())
                .with(authentication(adminAuthentication())));

        assertThat(result.getResponse().getContentType()).isEqualTo(ExcelExportFile.CONTENT_TYPE);
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("stage-results-export");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).isEqualTo(STAGE_RESULT_HEADER);
        assertThat(sheet.get(0)).doesNotContain("ci", "ciHash", "password");
        assertThat(sheet.subList(1, sheet.size())).hasSize(2);
    }

    @Test
    void export_stage_results_unknown_stage_returns_not_found() throws Exception {
        mockMvc.perform(get("/api/admin/stages/{stageId}/results/export", 999999L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());
    }

    // ---------- interviews ----------

    @Test
    void export_interviews_returns_xlsx_with_participant_counts() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, "Group A", InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting, "Cand"), 1);
        saveInterviewer(interview, saveEmployee("Interviewer One"), 1);
        saveInterviewer(interview, saveEmployee("Interviewer Two"), 2);

        MvcResult result = performExport(get("/api/admin/job-postings/{jobPostingId}/interviews/export", jobPosting.getId())
                .with(authentication(adminAuthentication())));

        assertThat(result.getResponse().getContentType()).isEqualTo(ExcelExportFile.CONTENT_TYPE);
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).isEqualTo(INTERVIEW_HEADER);

        List<List<String>> dataRows = sheet.subList(1, sheet.size());
        assertThat(dataRows).hasSize(1);
        assertThat(dataRows.get(0).get(12)).isEqualTo("1"); // candidateCount
        assertThat(dataRows.get(0).get(13)).isEqualTo("2"); // interviewerCount
    }

    @Test
    void export_interviews_filters_by_status() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        saveInterview(jobPosting, stage, "Confirmed Group", InterviewStatus.CONFIRMED);
        saveInterview(jobPosting, stage, "Draft Group", InterviewStatus.DRAFT);

        MvcResult all = performExport(get("/api/admin/job-postings/{jobPostingId}/interviews/export", jobPosting.getId())
                .with(authentication(adminAuthentication())));
        assertThat(dataRowsOf(all)).hasSize(2);

        MvcResult confirmed = performExport(get("/api/admin/job-postings/{jobPostingId}/interviews/export", jobPosting.getId())
                .param("status", "CONFIRMED")
                .with(authentication(adminAuthentication())));
        List<List<String>> confirmedRows = dataRowsOf(confirmed);
        assertThat(confirmedRows).hasSize(1);
        assertThat(confirmedRows.get(0).get(11)).isEqualTo("CONFIRMED"); // status column
    }

    @Test
    void export_interviews_filters_by_stage() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stageOne = saveStage(jobPosting);
        Stage stageTwo = saveStage(jobPosting, "Second interview", 2);
        saveInterview(jobPosting, stageOne, "Stage One Group", InterviewStatus.CONFIRMED);
        saveInterview(jobPosting, stageTwo, "Stage Two Group", InterviewStatus.CONFIRMED);

        MvcResult result = performExport(get("/api/admin/job-postings/{jobPostingId}/interviews/export", jobPosting.getId())
                .param("stageId", stageOne.getId().toString())
                .with(authentication(adminAuthentication())));

        List<List<String>> dataRows = dataRowsOf(result);
        assertThat(dataRows).hasSize(1);
        assertThat(dataRows.get(0).get(2)).isEqualTo("First interview"); // stageName
    }

    @Test
    void export_interviews_filters_by_time_range() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        saveInterviewAt(jobPosting, stage, "Early", LocalDateTime.of(2026, 6, 1, 9, 0));
        saveInterviewAt(jobPosting, stage, "Late", LocalDateTime.of(2026, 6, 1, 15, 0));

        MvcResult result = performExport(get("/api/admin/job-postings/{jobPostingId}/interviews/export", jobPosting.getId())
                .param("from", "2026-06-01T14:00:00")
                .param("to", "2026-06-01T17:00:00")
                .with(authentication(adminAuthentication())));

        List<List<String>> dataRows = dataRowsOf(result);
        assertThat(dataRows).hasSize(1);
        assertThat(dataRows.get(0).get(4)).isEqualTo("Late"); // groupName
    }

    @Test
    void export_interviews_rejects_from_after_to() throws Exception {
        JobPosting jobPosting = saveJobPosting();

        mockMvc.perform(get("/api/admin/job-postings/{jobPostingId}/interviews/export", jobPosting.getId())
                        .param("from", "2026-06-02T10:00:00")
                        .param("to", "2026-06-01T10:00:00")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_interviews_escapes_formula_injection_in_free_text() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        saveInterview(jobPosting, stage, "+inject", InterviewStatus.CONFIRMED);

        MvcResult result = performExport(get("/api/admin/job-postings/{jobPostingId}/interviews/export", jobPosting.getId())
                .with(authentication(adminAuthentication())));

        List<List<String>> dataRows = dataRowsOf(result);
        assertThat(dataRows).hasSize(1);
        assertThat(dataRows.get(0).get(4)).isEqualTo("'+inject"); // groupName escaped
    }

    @Test
    void export_interviews_unknown_job_posting_returns_not_found() throws Exception {
        mockMvc.perform(get("/api/admin/job-postings/{jobPostingId}/interviews/export", 999999L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());
    }

    // ---------- interview evaluations ----------

    @Test
    void export_stage_evaluations_returns_flat_rows_with_interviewer_identity() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, "Group A", InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting, "Eval Cand"), 1);
        saveInterviewer(interview, saveEmployee("Evaluator One"), 1);
        saveInterviewer(interview, saveEmployee("Evaluator Two"), 2);
        evaluationAdminService.initialize(interview.getId());

        MvcResult result = performExport(get("/api/admin/stages/{stageId}/interview-evaluations/export", stage.getId())
                .with(authentication(adminAuthentication())));

        assertThat(result.getResponse().getContentType()).isEqualTo(ExcelExportFile.CONTENT_TYPE);
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).isEqualTo(EVALUATION_HEADER);

        List<List<String>> dataRows = sheet.subList(1, sheet.size());
        assertThat(dataRows).hasSize(2); // 1 candidate x 2 interviewers
        assertThat(dataRows).allSatisfy(row -> assertThat(row.get(2)).isEqualTo("Eval Cand")); // applicantName
        assertThat(dataRows).anySatisfy(row -> assertThat(row.get(4)).isEqualTo("Evaluator One")); // interviewerName
        assertThat(dataRows).allSatisfy(row -> assertThat(row.get(5)).isEqualTo("DRAFT")); // status
    }

    @Test
    void export_stage_evaluations_unknown_stage_returns_not_found() throws Exception {
        mockMvc.perform(get("/api/admin/stages/{stageId}/interview-evaluations/export", 999999L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());
    }

    // ---------- authorization ----------

    @Test
    void export_blocks_applicant_and_anonymous() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Applicant applicant = saveApplicant("Blocked");

        mockMvc.perform(get("/api/admin/stages/{stageId}/results/export", stage.getId())
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stages/{stageId}/results/export", stage.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

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

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create(
                "Posting " + UUID.randomUUID(),
                "Content",
                start().minusDays(1),
                start().plusDays(10)
        );
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Stage saveStage(JobPosting jobPosting) {
        return saveStage(jobPosting, "First interview", 1);
    }

    private Stage saveStage(JobPosting jobPosting, String name, int order) {
        return stageRepository.saveAndFlush(
                Stage.create(jobPosting, name, StageType.FIRST_INTERVIEW, order, null, false)
        );
    }

    private JobApplication saveSubmittedApplication(JobPosting jobPosting, String name) {
        Applicant applicant = saveApplicant(name);
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                name,
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
        application.submit(start().minusHours(1));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private Interview saveInterview(JobPosting jobPosting, Stage stage, String groupName, InterviewStatus status) {
        return saveInterview(jobPosting, stage, groupName, status, start());
    }

    private Interview saveInterviewAt(JobPosting jobPosting, Stage stage, String groupName, LocalDateTime startDateTime) {
        return saveInterview(jobPosting, stage, groupName, InterviewStatus.CONFIRMED, startDateTime);
    }

    private Interview saveInterview(
            JobPosting jobPosting,
            Stage stage,
            String groupName,
            InterviewStatus status,
            LocalDateTime startDateTime
    ) {
        Interview interview = Interview.createDraft(
                jobPosting,
                stage,
                groupName,
                startDateTime,
                startDateTime.plusHours(1),
                InterviewMethod.IN_PERSON,
                "Head office",
                "Room 1",
                null,
                "admin memo"
        );
        if (status == InterviewStatus.CONFIRMED) {
            interview.confirm();
        }
        if (status == InterviewStatus.CANCELLED) {
            interview.confirm();
            interview.cancel();
        }
        return interviewRepository.saveAndFlush(interview);
    }

    private void saveCandidate(Interview interview, JobApplication application, int sortOrder) {
        participantRepository.saveAndFlush(com.shinyoung.recruit.domain.entity.InterviewParticipant
                .candidate(interview, application, sortOrder));
    }

    private void saveInterviewer(Interview interview, Employee employee, int sortOrder) {
        participantRepository.saveAndFlush(com.shinyoung.recruit.domain.entity.InterviewParticipant
                .interviewer(interview, employee, sortOrder));
    }

    private Applicant saveApplicant(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Employee saveEmployee(String name) {
        Employee employee = new Employee();
        employee.setLoginId("employee-" + UUID.randomUUID());
        employee.setName(name);
        employee.setDeptName("HR-" + UUID.randomUUID());
        return employeeRepository.saveAndFlush(employee);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "export-admin-" + UUID.randomUUID(),
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

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
