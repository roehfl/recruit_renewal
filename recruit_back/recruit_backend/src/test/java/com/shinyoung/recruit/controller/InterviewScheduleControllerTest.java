package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.InterviewScheduleUploadParser;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewScheduleControllerTest {

    private static final String BASE = "/api/admin/job-postings/{jobPostingId}/interview-schedules";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private StageResultRepository stageResultRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private MockMvc mockMvc;
    private JobPosting posting;
    private Stage documentStage;
    private Stage interviewStage;
    private Employee kim;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        JobPosting jobPosting = JobPosting.create(
                "Posting",
                "Content",
                LocalDateTime.of(2026, 5, 31, 10, 0),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(jobPosting);
        documentStage = stageRepository.saveAndFlush(
                Stage.create(posting, "서류", StageType.DOCUMENT, 0, null, false));
        interviewStage = stageRepository.saveAndFlush(
                Stage.create(posting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false));
        ReflectionTestUtils.setField(documentStage, "status", StageStatus.RESULT_ANNOUNCED);
        stageRepository.saveAndFlush(documentStage);
        Employee employee = new Employee();
        employee.setLoginId("emp-" + UUID.randomUUID());
        employee.setName("김면접");
        employee.setDeptName("HR");
        kim = employeeRepository.saveAndFlush(employee);
    }

    @Test
    void 업로드하면_200과_건수를_돌려주고_목록에서_조회된다() throws Exception {
        JobApplication hong = passedApplication("홍길동");

        mockMvc.perform(upload(row("10:00", hong.getId(), "홍길동")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interviewCount").value(1))
                .andExpect(jsonPath("$.data.candidateCount").value(1))
                .andExpect(jsonPath("$.data.rowErrors").isEmpty());

        mockMvc.perform(get(BASE, posting.getId())
                        .param("stageId", String.valueOf(interviewStage.getId()))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].groupName").value("1"))
                .andExpect(jsonPath("$.data[0].candidateOrder").value(1))
                .andExpect(jsonPath("$.data[0].interviewDateTime").value("2026-07-01T10:00:00"))
                .andExpect(jsonPath("$.data[0].arrivalDateTime").value("2026-07-01T09:30:00"))
                .andExpect(jsonPath("$.data[0].interviewers[0].loginId").value(kim.getLoginId()))
                .andExpect(jsonPath("$.data[0].applicationId").value(hong.getId()))
                .andExpect(jsonPath("$.data[0].applicantName").value("홍길동"));
    }

    @Test
    void 행_오류가_있으면_400과_행별_오류를_돌려준다() throws Exception {
        JobApplication hong = passedApplication("홍길동");

        mockMvc.perform(upload(row("10:00", hong.getId(), "다른이름")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("업로드 검증에 실패하여 반영하지 않았습니다."))
                .andExpect(jsonPath("$.data.interviewCount").value(0))
                .andExpect(jsonPath("$.data.rowErrors[0].rowNumber").value(2))
                .andExpect(jsonPath("$.data.rowErrors[0].messages", hasItem("성명이 수험번호의 지원자와 일치하지 않습니다: 다른이름")));
    }

    @Test
    void 받을_수_없는_파일은_400과_사유만_돌려준다() throws Exception {
        mockMvc.perform(multipart(BASE + "/upload", posting.getId())
                        .file(new MockMultipartFile("file", "schedule.csv", "text/csv", "a,b".getBytes()))
                        .param("stageId", String.valueOf(interviewStage.getId()))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("업로드는 .xlsx 형식만 허용합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 다운로드와_템플릿은_같은_한글_헤더의_xlsx를_내려준다() throws Exception {
        JobApplication hong = passedApplication("홍길동");
        mockMvc.perform(upload(row("10:00", hong.getId(), "홍길동"))).andExpect(status().isOk());

        MvcResult export = streamed(get(BASE + "/export", posting.getId())
                .param("stageId", String.valueOf(interviewStage.getId()))
                .with(authentication(adminAuthentication())));
        assertThat(export.getResponse().getContentType()).isEqualTo(XLSX_CONTENT_TYPE);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("일자");
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("성명");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("홍길동");
        }

        MvcResult template = streamed(get(BASE + "/upload-template", posting.getId())
                .with(authentication(adminAuthentication())));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("일자");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
    }

    @Test
    void 지원자는_면접_스케줄에_접근할_수_없다() throws Exception {
        Applicant applicant = saveApplicant("지원자");
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));

        mockMvc.perform(get(BASE, posting.getId())
                        .param("stageId", String.valueOf(interviewStage.getId()))
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()))))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.RequestBuilder upload(List<String> row) throws Exception {
        return multipart(BASE + "/upload", posting.getId())
                .file(new MockMultipartFile("file", "schedule.xlsx", XLSX_CONTENT_TYPE, xlsx(row)))
                .param("stageId", String.valueOf(interviewStage.getId()))
                .with(authentication(adminAuthentication()));
    }

    private MvcResult streamed(org.springframework.test.web.servlet.RequestBuilder builder) throws Exception {
        MvcResult started = mockMvc.perform(builder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn();
    }

    private List<String> row(String time, Long applicationId, String name) {
        return List.of("2026-07-01", "본사 3층", "09:30", time, "1", "1",
                kim.getName() + "(" + kim.getLoginId() + ")", String.valueOf(applicationId), name);
    }

    private byte[] xlsx(List<String> dataRow) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("면접스케줄");
            List<List<String>> rows = List.of(InterviewScheduleUploadParser.HEADERS, dataRow);
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    row.createCell(c, CellType.STRING).setCellValue(rows.get(r).get(c));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private JobApplication passedApplication(String name) {
        Applicant applicant = saveApplicant(name);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, applicant.getName(), posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 6, 1, 9, 0));
        jobApplicationRepository.saveAndFlush(application);
        StageResult result = StageResult.initialize(documentStage, application);
        result.updateResult(StageResultStatus.PASSED, BigDecimal.valueOf(90), null, LocalDateTime.of(2026, 6, 2, 9, 0), "admin");
        stageResultRepository.saveAndFlush(result);
        return application;
    }

    private Applicant saveApplicant(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "schedule-admin-" + UUID.randomUUID(),
                "Recruit",
                "Schedule Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
