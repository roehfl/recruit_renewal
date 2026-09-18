package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.InterviewScheduleSearchRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewScheduleInterviewerResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewScheduleRowResponse;
import com.shinyoung.recruit.dto.response.InterviewScheduleUploadResponse;
import com.shinyoung.recruit.dto.response.InterviewScheduleUploadRowError;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidInterviewScheduleUploadException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewScheduleServiceTest {

    private static final String DATE = "2026-07-01";

    @Autowired
    private InterviewScheduleService scheduleService;

    @Autowired
    private InterviewRepository interviewRepository;

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

    @Autowired
    private InterviewEvaluationAdminService interviewEvaluationAdminService;

    private JobPosting posting;
    private Stage documentStage;
    private Stage interviewStage;
    private Employee kim;
    private Employee lee;

    @BeforeEach
    void setUp() {
        posting = saveJobPosting();
        documentStage = saveStage(StageType.DOCUMENT, 0);
        interviewStage = saveStage(StageType.FIRST_INTERVIEW, 1);
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        kim = saveEmployee("김면접");
        lee = saveEmployee("이면접");
    }

    @Test
    void 업로드하면_조마다_확정_면접을_만들고_조_면접순서_순으로_조회된다() {
        JobApplication hong = passedApplication("홍길동");
        JobApplication chul = passedApplication("김철수");
        JobApplication young = passedApplication("이영희");

        // 파일 행 순서를 섞어도 조 → 면접순서로 정렬된다.
        InterviewScheduleUploadResponse response = upload(
                row("본사 3층", "09:30", "10:00", "2", "1", tokens(kim, lee), chul),
                row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim, lee), hong),
                row("본사 5층", "10:30", "11:00", "1", "2", tokens(kim), young));

        assertThat(response.hasErrors()).isFalse();
        assertThat(response.interviewCount()).isEqualTo(2);
        assertThat(response.candidateCount()).isEqualTo(3);
        assertThat(response.replacedInterviewCount()).isZero();

        List<Interview> interviews = interviewRepository.findByStageId(interviewStage.getId());
        assertThat(interviews).extracting(Interview::getStatus).containsOnly(InterviewStatus.CONFIRMED);
        assertThat(interviews).extracting(Interview::getGroupName).containsExactlyInAnyOrder("1", "2");

        List<AdminInterviewScheduleRowResponse> rows = scheduleService.getSchedules(posting.getId(), search(null, null));
        assertThat(rows).extracting(AdminInterviewScheduleRowResponse::applicationId)
                .containsExactly(hong.getId(), chul.getId(), young.getId());
        AdminInterviewScheduleRowResponse first = rows.get(0);
        assertThat(first.groupName()).isEqualTo("1");
        assertThat(first.candidateOrder()).isEqualTo(1);
        assertThat(first.interviewDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 0));
        assertThat(first.arrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 30));
        assertThat(first.locationName()).isEqualTo("본사 3층");
        assertThat(first.interviewers()).extracting(AdminInterviewScheduleInterviewerResponse::loginId)
                .containsExactly(kim.getLoginId(), lee.getLoginId());
        assertThat(first.applicantName()).isEqualTo("홍길동");
    }

    @Test
    void 다시_업로드하면_단계의_기존_면접을_파일_내용으로_교체한다() {
        JobApplication hong = passedApplication("홍길동");
        JobApplication chul = passedApplication("김철수");
        upload(
                row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong),
                row("본사 3층", "10:30", "11:00", "1", "2", tokens(kim), chul));

        InterviewScheduleUploadResponse response = upload(
                row("본사 5층", "13:30", "14:00", "1", "1", tokens(lee), chul));

        assertThat(response.hasErrors()).isFalse();
        assertThat(response.replacedInterviewCount()).isEqualTo(2);
        assertThat(interviewRepository.findByStageId(interviewStage.getId())).hasSize(1);
        assertThat(scheduleService.getSchedules(posting.getId(), search(null, null)))
                .extracting(AdminInterviewScheduleRowResponse::applicationId)
                .containsExactly(chul.getId());
    }

    @Test
    void 행_오류가_하나라도_있으면_아무것도_바꾸지_않고_행별_오류를_돌려준다() {
        JobApplication hong = passedApplication("홍길동");
        JobApplication failed = application("불합격", StageResultStatus.FAILED);
        upload(row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong));

        InterviewScheduleUploadResponse response = upload(
                row("본사 3층", "09:30", "10:00", "1", "1", "없는사람(nobody-" + UUID.randomUUID() + ")", hong),
                row("본사 3층", "09:30", "10:00", "2", "1", tokens(kim), failed),
                rowWithName("본사 5층", "11:30", "11:00", "1", "2", tokens(lee), hong.getId(), "다른이름"));

        assertThat(response.hasErrors()).isTrue();
        assertThat(response.interviewCount()).isZero();
        assertThat(messagesOf(response, 2)).anyMatch(message -> message.startsWith("면접관을 찾을 수 없습니다"));
        assertThat(messagesOf(response, 3)).contains("직전 단계 합격자가 아닙니다: " + failed.getId());
        assertThat(messagesOf(response, 4))
                .contains("도착시간은 면접시간보다 늦을 수 없습니다.", "수험번호가 파일 내에서 중복되었습니다: " + hong.getId());
        // 이전 업로드가 그대로 남는다.
        assertThat(scheduleService.getSchedules(posting.getId(), search(null, null)))
                .extracting(AdminInterviewScheduleRowResponse::applicationId)
                .containsExactly(hong.getId());
    }

    @Test
    void 성명이_수험번호의_지원자와_다르면_행_오류다() {
        JobApplication hong = passedApplication("홍길동");

        InterviewScheduleUploadResponse response = upload(
                rowWithName("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong.getId(), "홍길순"));

        assertThat(messagesOf(response, 2)).contains("성명이 수험번호의 지원자와 일치하지 않습니다: 홍길순");
    }

    @Test
    void 같은_조의_행은_일자_장소_도착시간_면접시간_면접관이_같아야_한다() {
        JobApplication hong = passedApplication("홍길동");
        JobApplication chul = passedApplication("김철수");

        InterviewScheduleUploadResponse response = upload(
                row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim, lee), hong),
                row("본사 3층", "09:30", "10:30", "2", "1", tokens(kim), chul));

        assertThat(messagesOf(response, 3)).contains("1조의 첫 행과 면접시간·면접관이(가) 다릅니다.");
    }

    @Test
    void 조와_면접순서는_1부터_빠짐없이_이어져야_한다() {
        JobApplication hong = passedApplication("홍길동");
        JobApplication chul = passedApplication("김철수");
        JobApplication young = passedApplication("이영희");

        InterviewScheduleUploadResponse response = upload(
                row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong),
                row("본사 3층", "09:30", "10:00", "3", "1", tokens(kim), chul),
                row("본사 3층", "10:30", "11:00", "1", "3", tokens(kim), young));

        assertThat(response.errors()).contains(
                "조는 1부터 빠짐없이 이어져야 합니다(누락: 2).",
                "1조의 면접순서는 1부터 빠짐없이 이어져야 합니다(누락: 2).");
    }

    @Test
    void 같은_면접관을_같은_시각의_두_조에_배정할_수_없다() {
        JobApplication hong = passedApplication("홍길동");
        JobApplication chul = passedApplication("김철수");

        InterviewScheduleUploadResponse response = upload(
                row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong),
                row("본사 5층", "09:30", "10:00", "1", "2", tokens(kim), chul));

        assertThat(response.errors()).contains("면접관 " + token(kim) + "이(가) " + DATE + " 10:00에 여러 조(1조, 2조)에 배정되었습니다.");
    }

    @Test
    void 직전_단계가_발표되지_않았으면_업로드를_거부한다() {
        JobApplication hong = passedApplication("홍길동");
        setStatus(documentStage, StageStatus.IN_PROGRESS);

        assertThatThrownBy(() -> upload(row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong)))
                .isInstanceOf(InvalidInterviewScheduleUploadException.class)
                .hasMessageContaining("결과를 발표한 뒤 업로드할 수 있습니다");
    }

    @Test
    void 면접_평가가_시작된_단계는_스케줄을_교체할_수_없다() {
        JobApplication hong = passedApplication("홍길동");
        upload(row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong));
        interviewEvaluationAdminService.initialize(interviewRepository.findByStageId(interviewStage.getId()).get(0).getId());

        assertThatThrownBy(() -> upload(row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong)))
                .isInstanceOf(InvalidInterviewScheduleUploadException.class)
                .hasMessage("면접 평가가 시작된 면접이 있어 스케줄을 교체할 수 없습니다.");
    }

    @Test
    void 헤더가_양식과_다르면_파일을_거부한다() {
        List<String> header = new ArrayList<>(InterviewScheduleUploadParser.HEADERS);
        header.set(3, "면접 시간");

        assertThatThrownBy(() -> scheduleService.upload(posting.getId(), interviewStage.getId(), xlsx(header, List.of())))
                .isInstanceOf(InvalidInterviewScheduleUploadException.class)
                .hasMessageStartingWith("엑셀 헤더가 올바르지 않습니다");
    }

    @Test
    void 엑셀이_날짜와_시각으로_바꾼_셀도_읽는다() throws IOException {
        JobApplication hong = passedApplication("홍길동");

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("면접스케줄");
            writeHeader(sheet, InterviewScheduleUploadParser.HEADERS);
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle timeStyle = workbook.createCellStyle();
            timeStyle.setDataFormat(helper.createDataFormat().getFormat("h:mm"));
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(LocalDate.of(2026, 7, 1));
            row.getCell(0).setCellStyle(dateStyle);
            row.createCell(1).setCellValue("본사 3층");
            row.createCell(2).setCellValue(9.5 / 24);
            row.getCell(2).setCellStyle(timeStyle);
            row.createCell(3).setCellValue(10.0 / 24);
            row.getCell(3).setCellStyle(timeStyle);
            row.createCell(4, CellType.NUMERIC).setCellValue(1);
            row.createCell(5, CellType.NUMERIC).setCellValue(1);
            row.createCell(6).setCellValue(token(kim));
            row.createCell(7, CellType.NUMERIC).setCellValue(hong.getId());
            row.createCell(8).setCellValue("홍길동");
            workbook.write(out);

            InterviewScheduleUploadResponse response = scheduleService.upload(
                    posting.getId(), interviewStage.getId(), file(out.toByteArray()));

            assertThat(response.hasErrors()).isFalse();
        }
        AdminInterviewScheduleRowResponse row = scheduleService.getSchedules(posting.getId(), search(null, null)).get(0);
        assertThat(row.interviewDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 0));
        assertThat(row.arrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 30));
    }

    @Test
    void 다운로드는_노란_굵은_한글_헤더와_조회_행을_담고_템플릿은_헤더만_담는다() throws IOException {
        JobApplication hong = passedApplication("홍길동");
        upload(row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim, lee), hong));

        ExcelExportFile export = scheduleService.exportSchedules(posting.getId(), search(null, null));
        try (InputStream in = Files.newInputStream(export.path()); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEmphasizedHeader(sheet);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(cells(sheet.getRow(1))).containsExactly(
                    DATE, "본사 3층", "09:30", "10:00", "1", "1", tokens(kim, lee), String.valueOf(hong.getId()), "홍길동");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        } finally {
            Files.deleteIfExists(export.path());
        }

        ExcelExportFile template = scheduleService.generateTemplate(posting.getId());
        try (InputStream in = Files.newInputStream(template.path()); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEmphasizedHeader(sheet);
            assertThat(sheet.getLastRowNum()).isZero();
        } finally {
            Files.deleteIfExists(template.path());
        }
    }

    @Test
    void 다운로드한_파일을_그대로_다시_올릴_수_있다() throws IOException {
        JobApplication hong = passedApplication("홍길동");
        JobApplication chul = passedApplication("김철수");
        upload(
                row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim, lee), hong),
                row("본사 3층", "09:30", "10:00", "2", "1", tokens(kim, lee), chul));

        ExcelExportFile export = scheduleService.exportSchedules(posting.getId(), search(null, null));
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(export.path());
        } finally {
            Files.deleteIfExists(export.path());
        }
        InterviewScheduleUploadResponse response = scheduleService.upload(posting.getId(), interviewStage.getId(), file(bytes));

        assertThat(response.hasErrors()).isFalse();
        assertThat(response.replacedInterviewCount()).isEqualTo(1);
        assertThat(response.candidateCount()).isEqualTo(2);
    }

    @Test
    void 조회는_조와_모집분야_조건으로_거른다() {
        JobApplication hong = passedApplication("홍길동");
        JobApplication chul = passedApplication("김철수");
        upload(
                row("본사 3층", "09:30", "10:00", "1", "1", tokens(kim), hong),
                row("본사 3층", "10:30", "11:00", "1", "2", tokens(kim), chul));

        assertThat(scheduleService.getSchedules(posting.getId(), search("2", null)))
                .extracting(AdminInterviewScheduleRowResponse::applicationId)
                .containsExactly(chul.getId());
        assertThat(scheduleService.getSchedules(posting.getId(), search(null, posting.getJobPositions().get(0).getId())))
                .hasSize(2);
        assertThat(scheduleService.getSchedules(posting.getId(), search(null, -1L))).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    @SafeVarargs
    private InterviewScheduleUploadResponse upload(List<String>... rows) {
        return scheduleService.upload(posting.getId(), interviewStage.getId(), xlsx(InterviewScheduleUploadParser.HEADERS, Arrays.asList(rows)));
    }

    private List<String> row(
            String location,
            String arrival,
            String time,
            String order,
            String group,
            String interviewers,
            JobApplication application
    ) {
        return rowWithName(location, arrival, time, order, group, interviewers,
                application.getId(), application.getApplicantNameSnapshot());
    }

    private List<String> rowWithName(
            String location,
            String arrival,
            String time,
            String order,
            String group,
            String interviewers,
            Long applicationId,
            String name
    ) {
        return List.of(DATE, location, arrival, time, order, group, interviewers, String.valueOf(applicationId), name);
    }

    private InterviewScheduleSearchRequest search(String groupName, Long jobPositionId) {
        return new InterviewScheduleSearchRequest(interviewStage.getId(), null, jobPositionId, null, groupName);
    }

    private List<String> messagesOf(InterviewScheduleUploadResponse response, int rowNumber) {
        return response.rowErrors().stream()
                .filter(error -> error.rowNumber() == rowNumber)
                .map(InterviewScheduleUploadRowError::messages)
                .findFirst()
                .orElse(List.of());
    }

    private MockMultipartFile xlsx(List<String> header, List<List<String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("면접스케줄");
            writeHeader(sheet, header);
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    row.createCell(c, CellType.STRING).setCellValue(rows.get(r).get(c));
                }
            }
            workbook.write(out);
            return file(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file", "schedule.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private void writeHeader(Sheet sheet, List<String> header) {
        Row row = sheet.createRow(0);
        for (int c = 0; c < header.size(); c++) {
            row.createCell(c, CellType.STRING).setCellValue(header.get(c));
        }
    }

    private void assertEmphasizedHeader(XSSFSheet sheet) {
        assertThat(cells(sheet.getRow(0))).isEqualTo(InterviewScheduleUploadParser.HEADERS);
        for (int c = 0; c < InterviewScheduleUploadParser.HEADERS.size(); c++) {
            XSSFCellStyle style = sheet.getRow(0).getCell(c).getCellStyle();
            assertThat(style.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(style.getFillForegroundColor()).isEqualTo(IndexedColors.YELLOW.getIndex());
            assertThat(style.getFont().getBold()).isTrue();
        }
    }

    private List<String> cells(Row row) {
        List<String> values = new ArrayList<>();
        for (int c = 0; c < InterviewScheduleUploadParser.HEADERS.size(); c++) {
            values.add(row.getCell(c).getStringCellValue());
        }
        return values;
    }

    private String tokens(Employee... employees) {
        return String.join(", ", Arrays.stream(employees).map(this::token).toList());
    }

    private String token(Employee employee) {
        return employee.getName() + "(" + employee.getLoginId() + ")";
    }

    private JobApplication passedApplication(String name) {
        return application(name, StageResultStatus.PASSED);
    }

    private JobApplication application(String name, StageResultStatus documentResult) {
        Applicant applicant = saveApplicant(name);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                posting,
                jobPosition,
                applicant.getName(),
                posting.getTitle(),
                jobPosition.getPositionName()
        );
        application.submit(LocalDateTime.of(2026, 6, 1, 9, 0));
        jobApplicationRepository.saveAndFlush(application);
        StageResult result = StageResult.initialize(documentStage, application);
        result.updateResult(documentResult, BigDecimal.valueOf(90), null, LocalDateTime.of(2026, 6, 2, 9, 0), "admin");
        stageResultRepository.saveAndFlush(result);
        return application;
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create(
                "Posting",
                "Content",
                LocalDateTime.of(2026, 5, 31, 10, 0),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Stage saveStage(StageType stageType, int stageOrder) {
        return stageRepository.saveAndFlush(
                Stage.create(posting, stageType.name(), stageType, stageOrder, null, false)
        );
    }

    private void setStatus(Stage stage, StageStatus status) {
        ReflectionTestUtils.setField(stage, "status", status);
        stageRepository.saveAndFlush(stage);
    }

    private Applicant saveApplicant(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Employee saveEmployee(String name) {
        Employee employee = new Employee();
        employee.setLoginId("emp-" + UUID.randomUUID());
        employee.setName(name);
        employee.setDeptName("HR");
        return employeeRepository.saveAndFlush(employee);
    }
}
