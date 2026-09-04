package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateItemRequest;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateRequest;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.StageResultService;
import com.shinyoung.recruit.service.StageResultUploadParser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
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
class StageResultUploadControllerTest {

    private static final List<String> HEADER = StageResultUploadParser.HEADERS;

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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
    private StageResultRepository stageResultRepository;

    @Autowired
    private StageResultService stageResultService;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ---------- template ----------

    @Test
    void upload_template_returns_xlsx_with_prefilled_values_and_no_pii_columns() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");

        MvcResult result = performTemplate(fixture.stageId());

        assertThat(result.getResponse().getContentType()).isEqualTo(XLSX_CONTENT_TYPE);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).isEqualTo(HEADER);
        assertThat(sheet.get(0)).doesNotContain("ci", "ciHash", "password", "phoneNumber", "email");
        assertThat(sheet.subList(1, sheet.size())).hasSize(2);
        // 토큰 컬럼이 현재 updatedAt(ISO-8601)을 prefill한다.
        assertThat(sheet.get(1).get(3)).isNotBlank();
    }

    @Test
    void upload_template_uses_korean_headers_labels_shading_and_result_dropdown() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");

        MvcResult result = performTemplate(fixture.stageId());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("시스템ID(수정금지)");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("결과");
            // 초기화 직후라 결과는 대기(PENDING) 라벨로 prefill
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("대기");
            // 읽기전용 4열 음영, 편집 열은 음영 없음
            assertThat(sheet.getRow(1).getCell(0).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(sheet.getRow(1).getCell(3).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(sheet.getRow(1).getCell(4).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.NO_FILL);
            // 헤더 틀고정
            assertThat(sheet.getPaneInformation()).isNotNull();
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            // 결과 열 드롭다운(명시 목록, 대기 제외)
            List<XSSFDataValidation> validations = sheet.getDataValidations();
            assertThat(validations).hasSize(1);
            XSSFDataValidation validation = validations.get(0);
            assertThat(validation.getValidationConstraint().getExplicitListValues())
                    .containsExactly("합격", "불합격", "보류", "결시", "철회");
            CellRangeAddress range = validation.getRegions().getCellRangeAddress(0);
            assertThat(range.getFirstColumn()).isEqualTo(4);
            assertThat(range.getLastColumn()).isEqualTo(4);
            assertThat(range.getFirstRow()).isEqualTo(1);
            assertThat(range.getLastRow()).isEqualTo(2); // 데이터 2행
        }
    }

    @Test
    void upload_template_unknown_stage_returns_not_found() throws Exception {
        mockMvc.perform(get("/api/admin/stages/{stageId}/results/upload-template", 999999L)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());
    }

    // ---------- preview ----------

    @Test
    void preview_reports_changed_unchanged_and_error_rows() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "HOLD", "", ""));           // changed
        data.add(rowOf(rows.get(1), "PASSED", "", ""));         // unchanged (already PASSED)
        // 존재하지 않는 stageResultId → error
        data.add(List.of("999999", String.valueOf(rows.get(0).appId()), "X", rows.get(0).token(), "FAILED", "", ""));

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.changedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.committable").value(false));
    }

    @Test
    void preview_flags_blank_result_status_and_pending_revert_as_error() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "", "", ""));        // blank resultStatus → error
        data.add(rowOf(rows.get(1), "PENDING", "", "")); // 판정된 행을 대기로 → error

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(2))
                .andExpect(jsonPath("$.data.committable").value(false));
    }

    @Test
    void preview_flags_application_id_mismatch_as_error() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        // stageResultId는 맞지만 applicationId를 다른 값으로 → 교차검증 실패
        data.add(List.of(String.valueOf(rows.get(0).srId()), "111222", "A",
                rows.get(0).token(), "HOLD", "", ""));

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(1));
    }

    @Test
    void preview_flags_duplicate_stage_result_id_as_error() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "HOLD", "", ""));
        data.add(rowOf(rows.get(0), "FAILED", "", "")); // 같은 stageResultId 중복

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(2));
    }

    @Test
    void preview_rejects_formula_cell_as_error() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        byte[] bytes = buildXlsxWithFormulaComment(List.of(
                HEADER,
                rowOf(rows.get(0), "HOLD", "", "")));

        mockMvc.perform(multipart("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId())
                        .file(new MockMultipartFile("file", "upload.xlsx", XLSX_CONTENT_TYPE, bytes))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(1));
    }

    @Test
    void preview_accepts_korean_result_labels() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "보류", "", ""));   // changed (PASSED → HOLD)
        data.add(rowOf(rows.get(1), "합격", "", ""));   // unchanged (already PASSED)

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.committable").value(true));
    }

    @Test
    void preview_treats_untouched_pending_rows_as_unchanged() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B"); // 초기화 직후 = 전원 PENDING
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "대기", "", ""));   // 손대지 않은 행 → unchanged
        data.add(rowOf(rows.get(1), "합격", "", ""));   // 판정 → changed

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.committable").value(true));
    }

    @Test
    void preview_rejects_pending_with_score_or_comment_change() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "대기", "10", "")); // 대기 유지 + 점수 입력 → error

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].errors[0]").value("대기 상태에서는 점수·코멘트를 입력할 수 없습니다. 결과를 먼저 판정하세요."));
    }

    @Test
    void preview_rejects_pending_with_comment_change() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "대기", "", "메모")); // 대기 유지 + 코멘트 입력 → error

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].errors[0]").value("대기 상태에서는 점수·코멘트를 입력할 수 없습니다. 결과를 먼저 판정하세요."));
    }

    // ---------- commit ----------

    @Test
    void commit_applies_changed_rows_and_skips_unchanged() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "HOLD", "55", "재검토"));  // changed
        data.add(rowOf(rows.get(1), "PASSED", "", ""));        // unchanged

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.data.changedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1));

        StageResult changed = stageResultRepository.findById(rows.get(0).srId()).orElseThrow();
        assertThat(changed.getResultStatus()).isEqualTo(StageResultStatus.HOLD);
        assertThat(changed.getScore()).isEqualByComparingTo("55");
        assertThat(changed.getComment()).isEqualTo("재검토");

        StageResult unchanged = stageResultRepository.findById(rows.get(1).srId()).orElseThrow();
        assertThat(unchanged.getResultStatus()).isEqualTo(StageResultStatus.PASSED);
    }

    @Test
    void commit_clears_score_and_comment_when_blank() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        // 현재값: PASSED + score 10 + comment "old"
        List<Current> ignored = currentRows(fixture.stageId());
        StageResult current = stageResultRepository.findById(ignored.get(0).srId()).orElseThrow();
        stageResultService.bulkUpdateResults(
                fixture.stageId(),
                new StageResultBulkUpdateRequest(List.of(
                        new StageResultBulkUpdateItemRequest(current.getId(), StageResultStatus.PASSED,
                                new java.math.BigDecimal("10"), "old"))),
                "seed-admin");
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "PASSED", "", "")); // score/comment blank → clear

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.data.changedCount").value(1));

        StageResult after = stageResultRepository.findById(rows.get(0).srId()).orElseThrow();
        assertThat(after.getScore()).isNull();
        assertThat(after.getComment()).isNull();
    }

    @Test
    void commit_rejects_all_when_any_row_invalid() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "HOLD", "", ""));     // valid change
        data.add(rowOf(rows.get(1), "NOPE", "", ""));     // invalid status → whole file rejected

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId(), data))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.outcome").value("REJECTED_VALIDATION"));

        // all-or-nothing: 유효했던 행도 적용되지 않는다.
        StageResult notApplied = stageResultRepository.findById(rows.get(0).srId()).orElseThrow();
        assertThat(notApplied.getResultStatus()).isEqualTo(StageResultStatus.PASSED);
    }

    @Test
    void commit_rejects_stale_rows_with_conflict() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        // 잘못된(오래된) 토큰으로 변경 시도 → STALE
        data.add(List.of(String.valueOf(rows.get(0).srId()), String.valueOf(rows.get(0).appId()),
                "A", "2000-01-01T00:00:00", "HOLD", "", ""));

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId(), data))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.outcome").value("REJECTED_STALE"))
                .andExpect(jsonPath("$.data.staleCount").value(1));

        StageResult notApplied = stageResultRepository.findById(rows.get(0).srId()).orElseThrow();
        assertThat(notApplied.getResultStatus()).isEqualTo(StageResultStatus.PASSED);
    }

    @Test
    void commit_does_not_corrupt_comment_with_leading_special_char() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        // 현재 comment를 '-'로 시작하는 값으로 설정(formula-escape 위험 prefix).
        StageResult seed = stageResultRepository.findByStageIdForAdminList(fixture.stageId()).get(0);
        stageResultService.bulkUpdateResults(
                fixture.stageId(),
                new StageResultBulkUpdateRequest(List.of(
                        new StageResultBulkUpdateItemRequest(
                                seed.getId(), StageResultStatus.PASSED, null, "- 보류 사유 확인 필요"))),
                "seed-admin");

        // 템플릿을 미수정 상태로 재업로드 → round-trip이라 변경 없음(escape로 인한 오판/오염이 없어야 한다).
        List<Current> rows = currentRows(fixture.stageId());
        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), rows.get(0).status(), rows.get(0).score(), rows.get(0).comment()));

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.data.changedCount").value(0))
                .andExpect(jsonPath("$.data.unchangedCount").value(1));

        StageResult after = stageResultRepository.findById(rows.get(0).srId()).orElseThrow();
        assertThat(after.getComment()).isEqualTo("- 보류 사유 확인 필요");
    }

    @Test
    void commit_rejects_when_stage_not_in_progress_even_with_no_changes() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        Stage stage = stageRepository.findById(fixture.stageId()).orElseThrow();
        stage.close();
        stageRepository.saveAndFlush(stage);

        // 전부 unchanged(변경 0건)여도 Stage IN_PROGRESS guard로 거부되어야 한다.
        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "PASSED", "", ""));

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId(), data))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commit_applies_partial_decisions_and_leaves_pending_rows() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B"); // 전원 PENDING
        List<Current> rows = currentRows(fixture.stageId());

        List<List<String>> data = new ArrayList<>();
        data.add(HEADER);
        data.add(rowOf(rows.get(0), "대기", "", ""));
        data.add(rowOf(rows.get(1), "불합격", "40", "서류 미비"));

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId(), data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.data.changedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1));

        assertThat(stageResultRepository.findById(rows.get(0).srId()).orElseThrow().getResultStatus())
                .isEqualTo(StageResultStatus.PENDING);
        StageResult decided = stageResultRepository.findById(rows.get(1).srId()).orElseThrow();
        assertThat(decided.getResultStatus()).isEqualTo(StageResultStatus.FAILED);
        assertThat(decided.getScore()).isEqualByComparingTo("40");
        assertThat(decided.getComment()).isEqualTo("서류 미비");
    }

    @Test
    void preview_rejects_numeric_token_cell_as_error() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        preset(fixture.stageId(), StageResultStatus.PASSED);
        List<Current> rows = currentRows(fixture.stageId());

        byte[] bytes = buildXlsxWithNumericToken(rows.get(0));
        mockMvc.perform(multipart("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId())
                        .file(new MockMultipartFile("file", "upload.xlsx", XLSX_CONTENT_TYPE, bytes))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCount").value(1));
    }

    @Test
    void stale_concurrent_write_is_rejected_by_optimistic_version() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        Long id = stageResultRepository.findByStageIdForAdminList(fixture.stageId()).get(0).getId();
        entityManager.flush();
        entityManager.clear();

        // 동일 row의 stale 스냅샷(version 0)을 detach로 확보.
        StageResult stale = entityManager.find(StageResult.class, id);
        entityManager.detach(stale);

        // 다른 트랜잭션이 먼저 변경한 상황을 모사: fresh 인스턴스로 update → version 증가.
        StageResult fresh = entityManager.find(StageResult.class, id);
        fresh.updateResult(StageResultStatus.PASSED, null, "fresh", LocalDateTime.now(), "fresh-admin");
        entityManager.flush();
        entityManager.clear();

        // stale(version 0)을 repository 경로로 반영하면 @Version 충돌이 Spring 예외로 변환되어 거부된다(모든 write 경로 공통).
        stale.updateResult(StageResultStatus.FAILED, null, "stale", LocalDateTime.now(), "stale-admin");
        assertThatThrownBy(() -> stageResultRepository.saveAndFlush(stale))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    // ---------- file-level defenses ----------

    @Test
    void commit_rejects_non_xlsx_extension() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        byte[] bytes = buildXlsx(List.of(HEADER));

        mockMvc.perform(multipart("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId())
                        .file(new MockMultipartFile("file", "upload.xls", XLSX_CONTENT_TYPE, bytes))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_rejects_wrong_header() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        // 헤더 문자열이 바뀌어도 "1열만 다름"이라는 의도가 유지되도록 복사본의 첫 열만 훼손한다.
        List<String> wrongHeader = new ArrayList<>(HEADER);
        wrongHeader.set(0, "wrong");
        List<List<String>> data = List.of(wrongHeader);

        mockMvc.perform(multipartUpload("/api/admin/stages/{stageId}/results/upload/preview", fixture.stageId(), data))
                .andExpect(status().isBadRequest());
    }

    // ---------- authorization ----------

    @Test
    void upload_endpoints_block_applicant_and_anonymous() throws Exception {
        Fixture fixture = fixtureWithResults("A");
        Applicant applicant = saveApplicant("Blocked");

        mockMvc.perform(get("/api/admin/stages/{stageId}/results/upload-template", fixture.stageId())
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stages/{stageId}/results/upload-template", fixture.stageId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/admin/stages/{stageId}/results/upload/commit", fixture.stageId())
                        .file(new MockMultipartFile("file", "upload.xlsx", XLSX_CONTENT_TYPE, buildXlsx(List.of(HEADER))))
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private MvcResult performTemplate(Long stageId) throws Exception {
        MvcResult started = mockMvc.perform(get("/api/admin/stages/{stageId}/results/upload-template", stageId)
                        .with(authentication(adminAuthentication())))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn();
    }

    private org.springframework.test.web.servlet.RequestBuilder multipartUpload(
            String url, Long stageId, List<List<String>> rows) throws Exception {
        return multipart(url, stageId)
                .file(new MockMultipartFile("file", "upload.xlsx", XLSX_CONTENT_TYPE, buildXlsx(rows)))
                .with(authentication(adminAuthentication()));
    }

    private List<String> rowOf(Current current, String status, String score, String comment) {
        return List.of(
                String.valueOf(current.srId()),
                String.valueOf(current.appId()),
                current.name(),
                current.token(),
                status,
                score,
                comment);
    }

    private void preset(Long stageId, StageResultStatus status) {
        List<StageResultBulkUpdateItemRequest> items = stageResultRepository.findByStageIdForAdminList(stageId).stream()
                .map(sr -> new StageResultBulkUpdateItemRequest(sr.getId(), status, null, null))
                .toList();
        stageResultService.bulkUpdateResults(stageId, new StageResultBulkUpdateRequest(items), "seed-admin");
    }

    /**
     * 현재 행을 upload-template 다운로드에서 그대로 읽어온다. 토큰/현재값을 앱이 실제로 내보내는 문자열 그대로
     * 얻으므로 토큰 normalize 규칙·precision에 의존하지 않는다(round-trip 충실).
     */
    private List<Current> currentRows(Long stageId) throws Exception {
        // 단일 트랜잭션 테스트라 영속성 컨텍스트를 DB와 동기화한다. 그래야 템플릿이 (앱이 실제로 그러듯)
        // DB precision의 updatedAt을 읽어 토큰을 만들고, commit의 PESSIMISTIC refresh 값과 일치한다.
        entityManager.flush();
        entityManager.clear();
        MvcResult result = performTemplate(stageId);
        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        List<Current> rows = new ArrayList<>();
        for (int r = 1; r < sheet.size(); r++) {
            List<String> c = sheet.get(r);
            rows.add(new Current(
                    Long.valueOf(c.get(0)), Long.valueOf(c.get(1)), c.get(2),
                    c.get(3), c.get(4), c.get(5), c.get(6)));
        }
        return rows;
    }

    private byte[] buildXlsx(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("stage-result-upload");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    Cell cell = row.createCell(c, CellType.STRING);
                    cell.setCellValue(cells.get(c));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildXlsxWithFormulaComment(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("stage-result-upload");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    if (r > 0 && c == 6) {
                        Cell formula = row.createCell(c, CellType.FORMULA);
                        formula.setCellFormula("1+1");
                    } else {
                        Cell cell = row.createCell(c, CellType.STRING);
                        cell.setCellValue(cells.get(c));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildXlsxWithNumericToken(Current c) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("stage-result-upload");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADER.size(); i++) {
                header.createCell(i, CellType.STRING).setCellValue(HEADER.get(i));
            }
            Row row = sheet.createRow(1);
            row.createCell(0, CellType.STRING).setCellValue(String.valueOf(c.srId()));
            row.createCell(1, CellType.STRING).setCellValue(String.valueOf(c.appId()));
            row.createCell(2, CellType.STRING).setCellValue(c.name());
            row.createCell(3, CellType.NUMERIC).setCellValue(45000); // 토큰을 numeric 셀로 → row error
            row.createCell(4, CellType.STRING).setCellValue("HOLD");
            row.createCell(5, CellType.STRING).setCellValue("");
            row.createCell(6, CellType.STRING).setCellValue("");
            workbook.write(out);
            return out.toByteArray();
        }
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

    private Fixture fixtureWithResults(String... applicantNames) {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        for (String name : applicantNames) {
            saveSubmittedApplication(jobPosting, name);
        }
        stageResultService.initialize(stage.getId());
        stage.start();
        stageRepository.saveAndFlush(stage);
        return new Fixture(jobPosting.getId(), stage.getId());
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

    private Stage saveStage(JobPosting jobPosting) {
        return stageRepository.saveAndFlush(
                Stage.create(jobPosting, "First interview", StageType.FIRST_INTERVIEW, 1, null, false));
    }

    private JobApplication saveSubmittedApplication(JobPosting jobPosting, String name) {
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                saveApplicant(name),
                jobPosting,
                jobPosition,
                name,
                jobPosting.getTitle(),
                jobPosition.getPositionName());
        application.submit(start().minusHours(1));
        return jobApplicationRepository.saveAndFlush(application);
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

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "upload-admin-" + UUID.randomUUID(),
                "Recruit",
                "Upload Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }

    private record Fixture(Long jobPostingId, Long stageId) {
    }

    private record Current(Long srId, Long appId, String name, String token, String status, String score, String comment) {
    }
}
