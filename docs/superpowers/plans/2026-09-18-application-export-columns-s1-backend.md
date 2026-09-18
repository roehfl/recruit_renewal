# 지원현황 엑셀 컬럼 선택 S1 (백엔드) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지원현황 엑셀을 백엔드 카탈로그(`ApplicationExportColumn`) 기반 가변 컬럼으로 바꾸고, 카탈로그 조회 API·`columns` 파라미터·감사 기록을 추가한다. 목록 응답에 `finalGraduationDate`(파생 필드)를 덧붙여 화면 "졸업년월" 결함의 백엔드 쪽을 해결한다.

**Architecture:** 신규 엔티티·테이블 없음. 카탈로그 enum이 헤더·기본 체크·필요 섹션·줄바꿈 여부를 선언하고, `ApplicationExportRowAssembler`가 1,000행 페이지마다 선택 컬럼이 요구하는 섹션만 `findByJobApplicationIdIn*`으로 배치 조회해 `Map<ApplicationExportColumn, String>`을 만든다. 공용 writer는 `wrapText` 플래그 하나만 늘린다. 컨트롤러가 `columns`를 파싱해 서비스와 감사 로그에 같은 목록을 넘긴다.

**Tech Stack:** Java 17, Spring Boot 4, JPA(H2 테스트), Apache POI(SXSSF), JUnit 5 + AssertJ + Mockito + MockMvc.

**설계서:** `docs/superpowers/specs/2026-09-18-application-export-column-selection-design.md` (§3 카탈로그, §4 API, §5 백엔드, §9.2 결함).

**작업 루트:** 모든 경로는 `recruit_back/recruit_backend/` 기준. 테스트 실행은 그 디렉터리에서:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "<패턴>" --no-daemon
```

**커밋:** 프로젝트 규칙(`recruit/CLAUDE.md` §6)상 사용자가 명시 요청할 때만 커밋한다. 각 Task는 테스트 통과로 끝내고 커밋 단계는 두지 않는다.

**문서:** 통합 화면 슬라이스이므로 백엔드 `docs/codex` 이중 문서화는 생략(`recruit/CLAUDE.md` §7). `api-contract.md` 🟢 확정은 S2 마지막에 한다.

---

## 파일 구조

| 파일 | 역할 | 변경 |
| --- | --- | --- |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationSummaryResponse.java` | `finalGraduationDate` 파생 필드 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java` | Enrichment에 최종학력 졸업일 채움 | 수정 |
| `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java` | 졸업일 단언 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/ExportColumn.java` | `wrapText` 플래그 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/ExcelExportWriter.java` | 줄바꿈 셀 스타일 | 수정 |
| `src/test/java/com/shinyoung/recruit/service/ExcelExportWriterTest.java` | 줄바꿈 스타일 테스트 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationExportSection.java` | 배치 로딩 단위 enum | 신규 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationExportColumn.java` | 컬럼 카탈로그 enum(단일 출처) | 신규 |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationExportColumnGroupResponse.java` | 카탈로그 응답 DTO | 신규 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationExportColumnTest.java` | parse/기본값/카탈로그 | 신규 |
| `src/main/java/com/shinyoung/recruit/service/CommonCodeNames.java` | export 1회용 공통코드 캐시 | 신규 |
| `src/test/java/com/shinyoung/recruit/service/CommonCodeNamesTest.java` | 캐시·fallback | 신규 |
| `src/main/java/com/shinyoung/recruit/domain/repository/Application{Military,Education,Career,Certificate,Language,Award,GapPeriod}Repository.java` | 지원서 id 배치 finder | 수정 |
| `src/main/java/com/shinyoung/recruit/dto/response/ApplicationExportRow.java` | base projection 확장 | 수정 |
| `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java` | export projection 쿼리 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationExportRowAssembler.java` | 페이지 배치 조회 + 셀 값 조립 | 신규 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationExportRowAssemblerSectionLoadingTest.java` | 섹션 선택 조회(Mockito) | 신규 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationExportRowAssemblerTest.java` | 값 표기(SpringBootTest) | 신규 |
| `src/main/java/com/shinyoung/recruit/service/ApplicationExportService.java` | 동적 spec + assembler 연결 | 수정 |
| `src/test/java/com/shinyoung/recruit/service/ApplicationExportServiceTest.java` | 시그니처·spec 검증 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/ExportAuditLogger.java` | `columns` 기록 | 수정 |
| `src/test/java/com/shinyoung/recruit/service/ExportAuditLoggerTest.java` | filters에 columns | 신규 |
| `src/main/java/com/shinyoung/recruit/controller/AdminExportController.java` | 카탈로그 API + `columns` 파라미터 | 수정 |
| `src/test/java/com/shinyoung/recruit/controller/AdminExportControllerTest.java` | 한글 헤더·신규 케이스 | 수정 |

---

### Task 1: 목록 응답 `finalGraduationDate` (결함 §9.2 백엔드)

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/dto/response/AdminApplicationSummaryResponse.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/JobApplicationService.java:303-312`
- Test: `src/test/java/com/shinyoung/recruit/service/JobApplicationServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`JobApplicationServiceTest`의 `admin_search_response_enriched_fields_are_null_when_no_related_data` 끝에 한 줄 추가:

```java
        assertThat(row.finalGraduationDate()).isNull();
```

그 테스트 바로 아래에 새 테스트 추가(헬퍼 `createApplicant`/`createPublishedJobPosting`/`createApplication`/`emptySearchRequest`, 필드 `educationRepository`·`jobApplicationRepository`는 기존 것):

```java
    @Test
    void admin_search_response_includes_graduation_date_of_final_education() {
        Applicant applicant = createApplicant("search-grad", "Grad Target");
        Long jobPostingId = createPublishedJobPosting("Search Grad Posting");
        Long applicationId = createApplication(applicant, jobPostingId);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.HIGH_SCHOOL, "Grad High", null, null, null, null,
                null, LocalDate.of(2014, 2, 10), GraduationStatus.GRADUATED, DayNightType.DAY, null, false, null, 0));
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.UNIVERSITY, "Grad Univ", null, null, null, null,
                null, LocalDate.of(2020, 2, 20), GraduationStatus.GRADUATED, DayNightType.DAY, null, false, null, 1));

        AdminApplicationSummaryResponse row = jobApplicationService
                .getApplicationsForAdmin(jobPostingId, emptySearchRequest(), 0, 20)
                .content().get(0);

        // 최종학력 행(최고 EducationLevel)의 졸업일 — finalSchoolName 과 같은 행이어야 한다.
        assertThat(row.finalSchoolName()).isEqualTo("Grad Univ");
        assertThat(row.finalGraduationDate()).isEqualTo(LocalDate.of(2020, 2, 20));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobApplicationServiceTest" --no-daemon`
Expected: 컴파일 실패 — `cannot find symbol: method finalGraduationDate()`

- [ ] **Step 3: 구현**

`AdminApplicationSummaryResponse` — 레코드 컴포넌트 `String finalSchoolName,` 바로 뒤에 추가:

```java
        String finalSchoolName,
        LocalDate finalGraduationDate,
        StageType stageType,
```

`Enrichment`도 같은 위치에 추가하고 javadoc 한 줄 보강, `empty()` 인자 수를 8개로:

```java
    /**
     * 목록 화면 파생 필드 묶음(서비스가 배치 조회로 채운다). {@code age} 는 조회 시점(오늘) 기준 만 나이,
     * {@code stageType}/{@code stageResultStatus} 는 검색 조건과 동일한 값 체계로 최신(stageOrder 최대) 전형 결과다.
     * {@code finalGraduationDate} 는 최종학력 행의 졸업일(응답 전용 파생 값 — 엔티티 필드 아님).
     */
    public record Enrichment(
            LocalDate birthDate,
            Integer age,
            EducationLevel finalEducationLevel,
            String finalSchoolName,
            LocalDate finalGraduationDate,
            StageType stageType,
            StageResultStatus stageResultStatus,
            String careerDescriptionDownloadUrl
    ) {
        public static Enrichment empty() {
            return new Enrichment(null, null, null, null, null, null, null, null);
        }
    }
```

`from(...)`의 `enrichment.finalSchoolName(),` 뒤에 `enrichment.finalGraduationDate(),` 추가.

`JobApplicationService` 303행 `new AdminApplicationSummaryResponse.Enrichment(` 인자에서 `finalSchoolName` 다음 줄에 추가:

```java
                    finalEducation == null ? null : finalEducation.getSchoolName(),
                    finalEducation == null ? null : finalEducation.getGraduationDate(),
```

- [ ] **Step 4: 통과 확인**

Run: 위 Step 2 명령
Expected: `BUILD SUCCESSFUL`

---

### Task 2: `ExportColumn.wrapText` + writer 줄바꿈 스타일

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/ExportColumn.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/ExcelExportWriter.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ExcelExportWriterTest.java`

- [ ] **Step 1: 실패 테스트 작성** — `ExcelExportWriterTest`에 추가

```java
    @Test
    void wrap_text_columns_get_wrap_style_on_data_cells_only() throws Exception {
        ExcelExportSpec<String> spec = new ExcelExportSpec<>(
                "sheet",
                List.of(
                        new ExportColumn<>("plain", value -> value),
                        new ExportColumn<>("multi", value -> value + "\n" + value, false, true)));

        Path file = writer.writeToTempFile(spec, ExportRowSource.ofList(List.of("a")), true);
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(1).getCellStyle().getWrapText()).isFalse();
            assertThat(sheet.getRow(1).getCell(0).getCellStyle().getWrapText()).isFalse();
            assertThat(sheet.getRow(1).getCell(1).getCellStyle().getWrapText()).isTrue();
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("a\na");
        } finally {
            Files.deleteIfExists(file);
        }
    }
```

- [ ] **Step 2: 실패 확인**

Run: `... --tests "com.shinyoung.recruit.service.ExcelExportWriterTest" ...`
Expected: 컴파일 실패 — `ExportColumn` 4-인자 생성자 없음

- [ ] **Step 3: 구현**

`ExportColumn.java` 전체 교체:

```java
package com.shinyoung.recruit.service;

import java.util.function.Function;

/**
 * Excel export 시트의 단일 컬럼 정의: header 텍스트 + row → 셀 문자열 추출기.
 *
 * <p>{@code readOnly}가 true면 writer가 header·data 셀에 회색 음영을 넣는다(사용자에게 "수정 금지" 열임을
 * 시각적으로 알리는 용도 — 값 보호는 upload parser의 교차검증이 담당한다).
 * {@code wrapText}가 true면 data 셀에 줄바꿈 스타일을 넣어 셀 안의 개행이 줄로 보이게 한다(1:N 요약 셀 용도).
 *
 * @param <T> export row 타입(projection DTO)
 */
public record ExportColumn<T>(String header, Function<T, String> extractor, boolean readOnly, boolean wrapText) {

    public ExportColumn(String header, Function<T, String> extractor, boolean readOnly) {
        this(header, extractor, readOnly, false);
    }

    public ExportColumn(String header, Function<T, String> extractor) {
        this(header, extractor, false, false);
    }

    public String value(T row) {
        String extracted = extractor.apply(row);
        return extracted == null ? "" : extracted;
    }
}
```

`ExcelExportWriter.java`:
- import 추가: `import org.apache.poi.ss.usermodel.VerticalAlignment;`
- `writeToTempFile` 안 `CellStyle readOnlyStyle = createReadOnlyStyle(workbook);` 다음 줄에 추가:

```java
            CellStyle wrapTextStyle = createWrapTextStyle(workbook);
```

- data 셀 기록부를 교체:

```java
                        writeStringCell(excelRow, columnIndex, column.value(row), escapeFormulaPrefix,
                                dataCellStyle(column, readOnlyStyle, wrapTextStyle));
```

- `createEmphasizedHeaderStyle` 아래에 추가:

```java
    /** 줄바꿈 셀(1:N 요약). 여러 줄이 위부터 보이도록 상단 정렬한다. workbook당 한 번만 만든다. */
    private CellStyle createWrapTextStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    /** data 셀 스타일. 읽기전용 음영이 우선이다(두 플래그를 함께 쓰는 dataset 은 없다). */
    private <T> CellStyle dataCellStyle(ExportColumn<T> column, CellStyle readOnlyStyle, CellStyle wrapTextStyle) {
        if (column.readOnly()) {
            return readOnlyStyle;
        }
        return column.wrapText() ? wrapTextStyle : null;
    }
```

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `BUILD SUCCESSFUL` (기존 3개 + 신규 1개)

---

### Task 3: 카탈로그 enum + 섹션 enum + 응답 DTO

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/ApplicationExportSection.java`
- Create: `src/main/java/com/shinyoung/recruit/service/ApplicationExportColumn.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/response/ApplicationExportColumnGroupResponse.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationExportColumnTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.ApplicationExportColumnGroupResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.shinyoung.recruit.service.ApplicationExportColumn.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationExportColumnTest {

    private static final List<ApplicationExportColumn> DEFAULTS = List.of(
            APPLICATION_ID, JOB_POSITION_NAME, WORK_LOCATION, STATUS, SUBMITTED_AT,
            LATEST_STAGE_RESULT,
            NAME, BIRTH_DATE, AGE, MOBILE_PHONE, EMAIL,
            FINAL_EDUCATION_LEVEL, FINAL_SCHOOL_NAME, FINAL_GRADUATION_DATE);

    @Test
    void blank_or_missing_keys_fall_back_to_default_columns() {
        assertThat(ApplicationExportColumn.defaults()).isEqualTo(DEFAULTS);
        assertThat(ApplicationExportColumn.parse(null)).isEqualTo(DEFAULTS);
        assertThat(ApplicationExportColumn.parse(List.of())).isEqualTo(DEFAULTS);
        assertThat(ApplicationExportColumn.parse(List.of(" ", ""))).isEqualTo(DEFAULTS);
    }

    @Test
    void parse_dedupes_and_orders_by_catalog_regardless_of_request_order() {
        assertThat(ApplicationExportColumn.parse(List.of("email", "APPLICATION_ID", " NAME ", "EMAIL")))
                .containsExactly(APPLICATION_ID, NAME, EMAIL);
    }

    @Test
    void parse_rejects_unknown_key() {
        assertThatThrownBy(() -> ApplicationExportColumn.parse(List.of("APPLICATION_ID", "NOT_A_COLUMN")))
                .isInstanceOf(InvalidJobApplicationException.class)
                .hasMessageContaining("NOT_A_COLUMN");
    }

    @Test
    void required_sections_skip_application_columns() {
        assertThat(ApplicationExportColumn.requiredSections(List.of(APPLICATION_ID, STATUS))).isEmpty();
        assertThat(ApplicationExportColumn.requiredSections(List.of(NAME, AGE, FINAL_SCHOOL_NAME, CURRENT_SALARY, CAREERS)))
                .containsExactlyInAnyOrder(
                        ApplicationExportSection.BASIC_INFO,
                        ApplicationExportSection.EDUCATION,
                        ApplicationExportSection.CAREER);
    }

    @Test
    void catalog_groups_columns_in_declaration_order() {
        List<ApplicationExportColumnGroupResponse> catalog = ApplicationExportColumn.catalog();

        assertThat(catalog).extracting(ApplicationExportColumnGroupResponse::group)
                .containsExactly("지원사항", "전형결과", "기본정보", "병역", "최종학력", "다건 요약");
        assertThat(catalog.get(0).columns().get(0))
                .isEqualTo(new ApplicationExportColumnGroupResponse.Column("APPLICATION_ID", "수험번호", true));
        assertThat(catalog.stream().mapToInt(group -> group.columns().size()).sum())
                .isEqualTo(ApplicationExportColumn.values().length);
    }

    @Test
    void only_one_to_many_summary_columns_wrap_text() {
        assertThat(Arrays.stream(ApplicationExportColumn.values()).filter(ApplicationExportColumn::wrapText).toList())
                .containsExactly(STAGE_RESULTS, EDUCATIONS, CAREERS, CURRENT_SALARY,
                        CERTIFICATES, LANGUAGES, AWARDS, GAP_PERIODS);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `... --tests "com.shinyoung.recruit.service.ApplicationExportColumnTest" ...`
Expected: 컴파일 실패 — `ApplicationExportColumn` 없음

- [ ] **Step 3: 구현**

`ApplicationExportSection.java`:

```java
package com.shinyoung.recruit.service;

/**
 * 지원현황 엑셀에서 페이지 단위로 배치 조회하는 부가 데이터 단위. 선택 컬럼이 요구하는 섹션만 조회한다
 * (예: 경력 컬럼을 고르지 않으면 경력 조회를 하지 않는다). 지원사항 컬럼은 base projection 으로 충족하므로 섹션이 없다.
 */
public enum ApplicationExportSection {
    BASIC_INFO,
    MILITARY,
    EDUCATION,
    CAREER,
    CERTIFICATE,
    LANGUAGE,
    AWARD,
    GAP_PERIOD,
    STAGE_RESULT
}
```

`ApplicationExportColumnGroupResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import java.util.List;

/** 지원현황 엑셀 컬럼 카탈로그의 그룹 1개(모달의 체크박스 묶음). 순서 = 카탈로그 선언 순서. */
public record ApplicationExportColumnGroupResponse(String group, List<Column> columns) {

    /** @param key 요청 {@code columns} 에 그대로 쓰는 값({@code ApplicationExportColumn} 이름) */
    public record Column(String key, String label, boolean defaultSelected) {
    }
}
```

`ApplicationExportColumn.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.ApplicationExportColumnGroupResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 지원현황 엑셀 컬럼 카탈로그 — 모달 체크박스와 엑셀 헤더의 단일 출처. 엑셀 컬럼 순서 = 선언 순서.
 *
 * <p>항목을 추가·삭제하면 {@link ApplicationExportRowAssembler} 의 값 계산 switch 가 컴파일 오류로 알려 준다
 * (switch 가 모든 상수를 다뤄야 한다). 프론트는 카탈로그 API 응답으로 그리므로 고칠 필요가 없다.
 *
 * <p>{@code section} 이 null 인 컬럼은 base projection({@code ApplicationExportRow})만으로 값을 만든다.
 */
public enum ApplicationExportColumn {

    APPLICATION_ID(Group.APPLICATION, "수험번호", true, null, false),
    JOB_POSTING_TITLE(Group.APPLICATION, "공고명", false, null, false),
    APPLICATION_TYPE(Group.APPLICATION, "지원구분", false, null, false),
    JOB_POSITION_NAME(Group.APPLICATION, "지원분야", true, null, false),
    JOB_TITLE(Group.APPLICATION, "직무", false, null, false),
    WORK_LOCATION(Group.APPLICATION, "근무지", true, null, false),
    STATUS(Group.APPLICATION, "지원상태", true, null, false),
    SUBMITTED_AT(Group.APPLICATION, "최종제출일시", true, null, false),
    CREATED_AT(Group.APPLICATION, "작성시작일시", false, null, false),
    UPDATED_AT(Group.APPLICATION, "최종수정일시", false, null, false),
    WITHDRAWN_AT(Group.APPLICATION, "철회일시", false, null, false),

    LATEST_STAGE_RESULT(Group.STAGE_RESULT, "최신 전형결과", true, ApplicationExportSection.STAGE_RESULT, false),
    STAGE_RESULTS(Group.STAGE_RESULT, "전형별 결과", false, ApplicationExportSection.STAGE_RESULT, true),

    NAME(Group.BASIC_INFO, "이름", true, ApplicationExportSection.BASIC_INFO, false),
    NAME_ENGLISH(Group.BASIC_INFO, "영문이름", false, ApplicationExportSection.BASIC_INFO, false),
    NATIONALITY(Group.BASIC_INFO, "국적", false, ApplicationExportSection.BASIC_INFO, false),
    BIRTH_DATE(Group.BASIC_INFO, "생년월일", true, ApplicationExportSection.BASIC_INFO, false),
    AGE(Group.BASIC_INFO, "나이", true, ApplicationExportSection.BASIC_INFO, false),
    MOBILE_PHONE(Group.BASIC_INFO, "휴대폰", true, ApplicationExportSection.BASIC_INFO, false),
    EMAIL(Group.BASIC_INFO, "이메일", true, ApplicationExportSection.BASIC_INFO, false),
    EMERGENCY_PHONE(Group.BASIC_INFO, "비상연락처", false, ApplicationExportSection.BASIC_INFO, false),
    ADDRESS(Group.BASIC_INFO, "주소", false, ApplicationExportSection.BASIC_INFO, false),
    VETERAN(Group.BASIC_INFO, "보훈", false, ApplicationExportSection.BASIC_INFO, false),
    DISABILITY(Group.BASIC_INFO, "장애", false, ApplicationExportSection.BASIC_INFO, false),
    APPLICATION_ROUTE(Group.BASIC_INFO, "지원경로", false, ApplicationExportSection.BASIC_INFO, false),

    MILITARY(Group.MILITARY, "병역", false, ApplicationExportSection.MILITARY, false),

    FINAL_EDUCATION_LEVEL(Group.FINAL_EDUCATION, "최종학력", true, ApplicationExportSection.EDUCATION, false),
    FINAL_SCHOOL_NAME(Group.FINAL_EDUCATION, "최종학교", true, ApplicationExportSection.EDUCATION, false),
    FINAL_MAJOR(Group.FINAL_EDUCATION, "전공", false, ApplicationExportSection.EDUCATION, false),
    FINAL_ADDITIONAL_MAJOR(Group.FINAL_EDUCATION, "부·복수전공", false, ApplicationExportSection.EDUCATION, false),
    FINAL_GRADUATION_STATUS(Group.FINAL_EDUCATION, "졸업구분", false, ApplicationExportSection.EDUCATION, false),
    FINAL_ADMISSION_DATE(Group.FINAL_EDUCATION, "입학년월", false, ApplicationExportSection.EDUCATION, false),
    FINAL_GRADUATION_DATE(Group.FINAL_EDUCATION, "졸업년월", true, ApplicationExportSection.EDUCATION, false),
    FINAL_GPA(Group.FINAL_EDUCATION, "평점", false, ApplicationExportSection.EDUCATION, false),
    FINAL_MAJOR_GPA(Group.FINAL_EDUCATION, "전공평점", false, ApplicationExportSection.EDUCATION, false),
    FINAL_SCHOOL_LOCATION(Group.FINAL_EDUCATION, "국내/해외", false, ApplicationExportSection.EDUCATION, false),
    FINAL_SCHOOL_TYPE(Group.FINAL_EDUCATION, "편입·분교·야간", false, ApplicationExportSection.EDUCATION, false),

    EDUCATIONS(Group.SUMMARY, "학력", false, ApplicationExportSection.EDUCATION, true),
    CAREERS(Group.SUMMARY, "경력", false, ApplicationExportSection.CAREER, true),
    CURRENT_SALARY(Group.SUMMARY, "현재연봉(만원)", false, ApplicationExportSection.CAREER, true),
    CERTIFICATES(Group.SUMMARY, "자격증", false, ApplicationExportSection.CERTIFICATE, true),
    LANGUAGES(Group.SUMMARY, "어학", false, ApplicationExportSection.LANGUAGE, true),
    AWARDS(Group.SUMMARY, "수상", false, ApplicationExportSection.AWARD, true),
    GAP_PERIODS(Group.SUMMARY, "공백기간", false, ApplicationExportSection.GAP_PERIOD, true);

    /** 모달 체크박스 묶음. 순서 = 선언 순서. */
    public enum Group {
        APPLICATION("지원사항"),
        STAGE_RESULT("전형결과"),
        BASIC_INFO("기본정보"),
        MILITARY("병역"),
        FINAL_EDUCATION("최종학력"),
        SUMMARY("다건 요약");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Group group;
    private final String label;
    private final boolean defaultSelected;
    private final ApplicationExportSection section;
    private final boolean wrapText;

    ApplicationExportColumn(
            Group group,
            String label,
            boolean defaultSelected,
            ApplicationExportSection section,
            boolean wrapText
    ) {
        this.group = group;
        this.label = label;
        this.defaultSelected = defaultSelected;
        this.section = section;
        this.wrapText = wrapText;
    }

    public String label() {
        return label;
    }

    public boolean wrapText() {
        return wrapText;
    }

    /** 모달을 열 때 체크되는 컬럼(= {@code columns} 미지정 요청의 컬럼). */
    public static List<ApplicationExportColumn> defaults() {
        return Arrays.stream(values()).filter(column -> column.defaultSelected).toList();
    }

    /**
     * 요청 {@code columns} 를 카탈로그 순서의 컬럼 목록으로 바꾼다. 빈 요청은 기본 컬럼(기존 호출 하위호환),
     * 대소문자·앞뒤 공백은 무시, 중복은 제거, 정의 밖 key 는 400.
     */
    public static List<ApplicationExportColumn> parse(List<String> keys) {
        List<String> requested = keys == null ? List.of() : keys.stream()
                .filter(key -> key != null && !key.isBlank())
                .map(key -> key.trim().toUpperCase(Locale.ROOT))
                .toList();
        if (requested.isEmpty()) {
            return defaults();
        }
        Set<ApplicationExportColumn> selected = EnumSet.noneOf(ApplicationExportColumn.class);
        for (String key : requested) {
            try {
                selected.add(valueOf(key));
            } catch (IllegalArgumentException e) {
                throw new InvalidJobApplicationException("엑셀 컬럼 값이 올바르지 않습니다. columns=" + key);
            }
        }
        // EnumSet 은 선언 순서로 순회한다 — 출력 순서가 체크 순서와 무관하게 고정된다.
        return List.copyOf(selected);
    }

    /** 선택 컬럼이 값을 만들기 위해 배치 조회해야 하는 섹션. */
    public static Set<ApplicationExportSection> requiredSections(Collection<ApplicationExportColumn> columns) {
        Set<ApplicationExportSection> sections = EnumSet.noneOf(ApplicationExportSection.class);
        for (ApplicationExportColumn column : columns) {
            if (column.section != null) {
                sections.add(column.section);
            }
        }
        return sections;
    }

    /** 카탈로그 조회 API 응답. 그룹·컬럼 모두 선언 순서. */
    public static List<ApplicationExportColumnGroupResponse> catalog() {
        Map<Group, List<ApplicationExportColumnGroupResponse.Column>> grouped = new LinkedHashMap<>();
        for (ApplicationExportColumn column : values()) {
            grouped.computeIfAbsent(column.group, group -> new ArrayList<>())
                    .add(new ApplicationExportColumnGroupResponse.Column(
                            column.name(), column.label, column.defaultSelected));
        }
        return grouped.entrySet().stream()
                .map(entry -> new ApplicationExportColumnGroupResponse(entry.getKey().label(), entry.getValue()))
                .toList();
    }
}
```

주의: `MILITARY` 는 컬럼 상수와 섹션 상수 이름이 같다. 섹션은 반드시 `ApplicationExportSection.MILITARY` 로 한정해 쓴다(정적 import 금지).

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `BUILD SUCCESSFUL` (6 tests)

---

### Task 4: `CommonCodeNames` (export 1회용 공통코드 캐시)

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/CommonCodeNames.java`
- Test: `src/test/java/com/shinyoung/recruit/service/CommonCodeNamesTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.CommonCodeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CommonCodeNamesTest {

    @Mock
    private CommonCodeService commonCodeService;

    @Test
    void looks_up_each_group_once_and_falls_back_to_the_raw_code() {
        given(commonCodeService.getActiveCodes("APPLICATION_ROUTE")).willReturn(List.of(
                new CommonCodeResponse(1L, "APPLICATION_ROUTE", "WEB", "홈페이지", 1, true, null)));
        CommonCodeNames names = new CommonCodeNames(commonCodeService);

        assertThat(names.name("APPLICATION_ROUTE", "WEB")).isEqualTo("홈페이지");
        assertThat(names.name("APPLICATION_ROUTE", "WEB")).isEqualTo("홈페이지");
        // 미등록 코드는 누락을 감추지 않고 코드값 그대로(PDF 와 같은 규칙)
        assertThat(names.name("APPLICATION_ROUTE", "UNKNOWN")).isEqualTo("UNKNOWN");

        verify(commonCodeService, times(1)).getActiveCodes("APPLICATION_ROUTE");
    }

    @Test
    void blank_code_returns_empty_without_lookup() {
        CommonCodeNames names = new CommonCodeNames(commonCodeService);

        assertThat(names.name("NATIONALITY", null)).isEmpty();
        assertThat(names.name("NATIONALITY", " ")).isEmpty();
        verifyNoInteractions(commonCodeService);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `... --tests "com.shinyoung.recruit.service.CommonCodeNamesTest" ...`
Expected: 컴파일 실패 — `CommonCodeNames` 없음

- [ ] **Step 3: 구현**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.CommonCodeResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * export 1회 동안 쓰는 공통코드 표시명 캐시. 그룹마다 한 번만 조회한다(5만 행 export 에서 행마다 조회하지 않도록).
 * 미등록·비활성 코드는 코드값을 그대로 돌려준다 — PDF({@code ApplicationPdfService.codeName})와 같은 규칙(누락을 감추지 않는다).
 * 요청 스레드 하나에서만 쓰므로 동기화하지 않는다.
 */
final class CommonCodeNames {

    private final CommonCodeService commonCodeService;
    private final Map<String, Map<String, String>> cache = new HashMap<>();

    CommonCodeNames(CommonCodeService commonCodeService) {
        this.commonCodeService = commonCodeService;
    }

    String name(String groupCode, String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        Map<String, String> codes = cache.computeIfAbsent(groupCode, group -> commonCodeService.getActiveCodes(group)
                .stream()
                .collect(Collectors.toMap(CommonCodeResponse::code, CommonCodeResponse::displayName, (a, b) -> a)));
        return codes.getOrDefault(code, code);
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `BUILD SUCCESSFUL` (2 tests)

---

### Task 5: 섹션 repository 배치 finder

**Files:** (모두 `src/main/java/com/shinyoung/recruit/domain/repository/`)
- Modify: `ApplicationMilitaryRepository.java`, `ApplicationEducationRepository.java`, `ApplicationCareerRepository.java`, `ApplicationCertificateRepository.java`, `ApplicationLanguageRepository.java`, `ApplicationAwardRepository.java`, `ApplicationGapPeriodRepository.java`

엑셀 요약 셀은 입력 순서(`sortOrder`, 동률 id)로 나열하므로 1:N 섹션은 정렬된 finder를 쓴다. 병역은 1:1이라 정렬 불필요. 검증은 Task 7 테스트(파생 쿼리는 컨텍스트 기동 시 검증된다)가 맡는다.

- [ ] **Step 1: finder 추가**

`ApplicationMilitaryRepository` — import `java.util.Collection`, `java.util.List` 추가 후:

```java
    List<ApplicationMilitary> findByJobApplicationIdIn(Collection<Long> applicationIds);
```

`ApplicationEducationRepository` — 기존 `findByJobApplicationIdIn` 아래(목록용 무정렬 finder는 그대로 둔다):

```java
    List<ApplicationEducation> findByJobApplicationIdInOrderBySortOrderAscIdAsc(Collection<Long> applicationIds);
```

`ApplicationCareerRepository`, `ApplicationCertificateRepository`, `ApplicationLanguageRepository`, `ApplicationAwardRepository`, `ApplicationGapPeriodRepository` — 각각 import `java.util.Collection` 추가 후 엔티티 타입만 바꿔:

```java
    List<ApplicationCareer> findByJobApplicationIdInOrderBySortOrderAscIdAsc(Collection<Long> applicationIds);
```

```java
    List<ApplicationCertificate> findByJobApplicationIdInOrderBySortOrderAscIdAsc(Collection<Long> applicationIds);
```

```java
    List<ApplicationLanguage> findByJobApplicationIdInOrderBySortOrderAscIdAsc(Collection<Long> applicationIds);
```

```java
    List<ApplicationAward> findByJobApplicationIdInOrderBySortOrderAscIdAsc(Collection<Long> applicationIds);
```

```java
    List<ApplicationGapPeriod> findByJobApplicationIdInOrderBySortOrderAscIdAsc(Collection<Long> applicationIds);
```

- [ ] **Step 2: 컴파일 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

---

### Task 6: base projection 확장 (`ApplicationExportRow`)

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/dto/response/ApplicationExportRow.java`
- Modify: `src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java:254-271`

지원사항 컬럼(지원구분·직무·근무지)의 원천을 projection에 넣는다. 이 Task는 기존 `ApplicationExportService.APPLICATIONS_SPEC`(Task 8에서 제거)을 깨지 않도록 컴포넌트를 **추가만** 한다.

- [ ] **Step 1: 레코드 교체**

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;

import java.time.LocalDateTime;

/**
 * Applications export 전용 projection row. JPA 생성자 표현식으로 직접 조회되며, JPA entity/lazy
 * association을 export writer에 넘기지 않기 위한 평탄한 DTO다.
 *
 * <p>지원사항 컬럼의 원천이다. {@code applicantName}/{@code phoneNumber}/{@code email} 은 기본정보가 없는
 * 지원서의 fallback(지원 당시 이름 snapshot·계정 연락처)이다 — 기본정보가 있으면 assembler 가 그 값을 쓴다.
 * {@code ci}/{@code ciHash}/{@code password}는 절대 포함하지 않는다.
 */
public record ApplicationExportRow(
        Long applicationId,
        String applicantName,
        String phoneNumber,
        String email,
        String jobPostingTitle,
        JobPositionApplicationType applicationType,
        String jobPositionName,
        String jobTitle,
        String workLocationName,
        JobApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

- [ ] **Step 2: 쿼리 교체** — `findExportApplications` 의 `@Query` 문자열

```java
    @Query("""
            select new com.shinyoung.recruit.dto.response.ApplicationExportRow(
                application.id,
                application.applicantNameSnapshot,
                applicant.phoneNumber,
                applicant.email,
                application.jobPostingTitleSnapshot,
                exportPosition.applicationType,
                application.jobPositionNameSnapshot,
                exportPosition.jobTitle,
                application.workLocationNameSnapshot,
                application.status,
                application.submittedAt,
                application.withdrawnAt,
                application.createdAt,
                application.updatedAt)
            from JobApplication application
            join application.applicant applicant
            join application.jobPosition exportPosition
            """ + ADMIN_SEARCH_WHERE + """
            order by application.createdAt desc, application.id desc
            """)
```

(`exportPosition` 별칭은 `ADMIN_SEARCH_WHERE` 안의 경로식과 겹치지 않게 고른 이름이다.)

- [ ] **Step 3: 컴파일 확인**

기존 `ApplicationExportService.APPLICATIONS_SPEC` 은 접근자 이름(`applicationId()`, `applicantName()` …)만 쓰므로 그대로 컴파일된다.

Run: `... .\gradlew.bat compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

---

### Task 7: `ApplicationExportRowAssembler`

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/ApplicationExportRowAssembler.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationExportRowAssemblerSectionLoadingTest.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationExportRowAssemblerTest.java`

- [ ] **Step 1: 섹션 선택 조회 실패 테스트 (Mockito)**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 선택 컬럼이 요구하는 섹션만 조회하는지(불필요한 1:N 조회 없음)를 DB 없이 고정한다. */
@ExtendWith(MockitoExtension.class)
class ApplicationExportRowAssemblerSectionLoadingTest {

    @Mock private ApplicationBasicInfoRepository basicInfoRepository;
    @Mock private ApplicationMilitaryRepository militaryRepository;
    @Mock private ApplicationEducationRepository educationRepository;
    @Mock private ApplicationCareerRepository careerRepository;
    @Mock private ApplicationCertificateRepository certificateRepository;
    @Mock private ApplicationLanguageRepository languageRepository;
    @Mock private ApplicationAwardRepository awardRepository;
    @Mock private ApplicationGapPeriodRepository gapPeriodRepository;
    @Mock private StageResultRepository stageResultRepository;
    @Mock private CommonCodeService commonCodeService;
    @Mock private EntityManager entityManager;

    private ApplicationExportRowAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ApplicationExportRowAssembler(
                basicInfoRepository, militaryRepository, educationRepository, careerRepository,
                certificateRepository, languageRepository, awardRepository, gapPeriodRepository,
                stageResultRepository, commonCodeService, Clock.systemDefaultZone());
        ReflectionTestUtils.setField(assembler, "entityManager", entityManager);
    }

    @Test
    void application_columns_use_base_row_only_and_clear_persistence_context() {
        List<Map<ApplicationExportColumn, String>> values = assembler.assemble(
                List.of(row(1L)),
                List.of(ApplicationExportColumn.APPLICATION_ID, ApplicationExportColumn.STATUS,
                        ApplicationExportColumn.APPLICATION_TYPE, ApplicationExportColumn.SUBMITTED_AT),
                assembler.newCodeNames());

        assertThat(values).containsExactly(Map.of(
                ApplicationExportColumn.APPLICATION_ID, "1",
                ApplicationExportColumn.STATUS, "제출 완료",
                ApplicationExportColumn.APPLICATION_TYPE, "신입",
                ApplicationExportColumn.SUBMITTED_AT, "2026-05-10 10:00"));
        verifyNoInteractions(basicInfoRepository, militaryRepository, educationRepository, careerRepository,
                certificateRepository, languageRepository, awardRepository, gapPeriodRepository,
                stageResultRepository, commonCodeService);
        verify(entityManager).clear();
    }

    @Test
    void career_columns_query_only_careers() {
        assembler.assemble(
                List.of(row(1L)),
                List.of(ApplicationExportColumn.CAREERS, ApplicationExportColumn.CURRENT_SALARY),
                assembler.newCodeNames());

        verify(careerRepository).findByJobApplicationIdInOrderBySortOrderAscIdAsc(List.of(1L));
        verifyNoInteractions(basicInfoRepository, militaryRepository, educationRepository,
                certificateRepository, languageRepository, awardRepository, gapPeriodRepository, stageResultRepository);
    }

    @Test
    void empty_page_returns_empty_without_queries() {
        assertThat(assembler.assemble(List.of(), ApplicationExportColumn.defaults(), assembler.newCodeNames())).isEmpty();

        verifyNoInteractions(basicInfoRepository, educationRepository, stageResultRepository, entityManager);
    }

    @Test
    void truncate_keeps_cells_within_excel_limit_with_room_for_formula_escape() {
        String truncated = ApplicationExportRowAssembler.truncate("가".repeat(40_000));

        assertThat(truncated).hasSize(ApplicationExportRowAssembler.MAX_CELL_LENGTH)
                .endsWith(ApplicationExportRowAssembler.TRUNCATED_SUFFIX);
        assertThat(ApplicationExportRowAssembler.truncate("짧은 값")).isEqualTo("짧은 값");
    }

    private static ApplicationExportRow row(Long applicationId) {
        return new ApplicationExportRow(
                applicationId, "스냅샷", "01000000000", "acct@example.com", "공고",
                JobPositionApplicationType.NEW_GRADUATE, "Backend", null, null, JobApplicationStatus.SUBMITTED,
                LocalDateTime.of(2026, 5, 10, 10, 0), null,
                LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 10, 10, 0));
    }
}
```

- [ ] **Step 2: 값 표기 실패 테스트 (SpringBootTest)**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.GapType;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.shinyoung.recruit.service.ApplicationExportColumn.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 셀 값 표기를 실제 JPA 조회로 고정한다. 표기 규칙은 PDF(ApplicationPdfService)와 같아야 한다.
 * 공통코드는 테스트 DB에 등록돼 있지 않으므로 코드값 fallback 으로 검증한다(표시명 변환은 CommonCodeNamesTest).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationExportRowAssemblerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired private ApplicationExportRowAssembler assembler;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private ApplicationMilitaryRepository militaryRepository;
    @Autowired private ApplicationEducationRepository educationRepository;
    @Autowired private ApplicationCareerRepository careerRepository;
    @Autowired private ApplicationCertificateRepository certificateRepository;
    @Autowired private ApplicationLanguageRepository languageRepository;
    @Autowired private ApplicationAwardRepository awardRepository;
    @Autowired private ApplicationGapPeriodRepository gapPeriodRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private StageResultRepository stageResultRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void default_columns_use_basic_info_final_education_and_latest_stage_result() {
        JobApplication application = persistApplication("asm-default");
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "기본이름", null, NationalityType.DOMESTIC, null, LocalDate.of(1995, 1, 1),
                "01099990000", null, "basic@example.com", VeteranStatus.NOT_SUBJECT, null,
                DisabilityStatus.NOT_SUBJECT, null, null, null, null, null, null));
        educationRepository.save(education(application, EducationLevel.HIGH_SCHOOL, "한국고", LocalDate.of(2014, 2, 10), 0));
        educationRepository.save(education(application, EducationLevel.UNIVERSITY, "한국대", LocalDate.of(2020, 2, 20), 1));
        decide(application, "서류전형", StageType.DOCUMENT, 1, StageResultStatus.PASSED);
        decide(application, "1차면접", StageType.FIRST_INTERVIEW, 2, StageResultStatus.FAILED);

        Map<ApplicationExportColumn, String> values = assembleOne(application, ApplicationExportColumn.defaults());

        assertThat(values).hasSize(14)
                .containsEntry(APPLICATION_ID, String.valueOf(application.getId()))
                .containsEntry(JOB_POSITION_NAME, "Backend")
                .containsEntry(WORK_LOCATION, "")
                .containsEntry(STATUS, "제출 완료")
                .containsEntry(SUBMITTED_AT, "2026-05-10 10:00")
                .containsEntry(LATEST_STAGE_RESULT, "1차면접 불합격")
                .containsEntry(NAME, "기본이름")
                .containsEntry(BIRTH_DATE, "1995-01-01")
                // FIXED_CLOCK = 2026-06-15 기준 만 나이(목록과 같은 계산)
                .containsEntry(AGE, "31")
                .containsEntry(MOBILE_PHONE, "01099990000")
                .containsEntry(EMAIL, "basic@example.com")
                .containsEntry(FINAL_EDUCATION_LEVEL, "대학교")
                .containsEntry(FINAL_SCHOOL_NAME, "한국대")
                .containsEntry(FINAL_GRADUATION_DATE, "2020-02");
    }

    @Test
    void name_and_contact_fall_back_to_snapshot_and_account_without_basic_info() {
        JobApplication application = persistApplication("asm-fallback");

        Map<ApplicationExportColumn, String> values = assembleOne(application,
                List.of(NAME, MOBILE_PHONE, EMAIL, BIRTH_DATE, AGE, LATEST_STAGE_RESULT, FINAL_SCHOOL_NAME));

        assertThat(values)
                .containsEntry(NAME, "스냅샷")
                .containsEntry(MOBILE_PHONE, "01000000000")
                .containsEntry(EMAIL, "acct@example.com")
                .containsEntry(BIRTH_DATE, "")
                .containsEntry(AGE, "")
                .containsEntry(LATEST_STAGE_RESULT, "")
                .containsEntry(FINAL_SCHOOL_NAME, "");
    }

    @Test
    void basic_info_detail_columns_follow_pdf_notation() {
        JobApplication application = persistApplication("asm-basic");
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "HONG GILDONG", NationalityType.FOREIGN, "ZZ", LocalDate.of(1990, 3, 3),
                "01011112222", "01033334444", "hong@example.com", VeteranStatus.SUBJECT, "국가유공자",
                DisabilityStatus.SUBJECT, "G9", "T9", "04524", "서울시 중구 세종대로 1", "101호", "ROUTE_X"));

        Map<ApplicationExportColumn, String> values = assembleOne(application,
                List.of(NAME_ENGLISH, NATIONALITY, EMERGENCY_PHONE, ADDRESS, VETERAN, DISABILITY, APPLICATION_ROUTE));

        assertThat(values)
                .containsEntry(NAME_ENGLISH, "HONG GILDONG")
                .containsEntry(NATIONALITY, "외국인 (ZZ)")
                .containsEntry(EMERGENCY_PHONE, "01033334444")
                .containsEntry(ADDRESS, "(04524) 서울시 중구 세종대로 1, 101호")
                .containsEntry(VETERAN, "대상 (국가유공자)")
                .containsEntry(DISABILITY, "대상 (등급: G9 / 유형: T9)")
                .containsEntry(APPLICATION_ROUTE, "ROUTE_X");
    }

    @Test
    void military_shows_service_detail_only_when_completed_and_never_the_exemption_reason() {
        JobApplication completed = persistApplication("asm-mil-done");
        militaryRepository.save(ApplicationMilitary.create(
                completed, MilitarySubjectType.COMPLETED, MilitaryServiceType.ACTIVE_DUTY, MilitaryBranch.ARMY,
                MilitaryRank.SERGEANT, LocalDate.of(2015, 3, 2), LocalDate.of(2016, 12, 1), null));
        JobApplication exempted = persistApplication("asm-mil-exempt");
        militaryRepository.save(ApplicationMilitary.create(
                exempted, MilitarySubjectType.EXEMPTED, null, null, null, null, null, "건강 사유"));
        entityManager.flush();

        List<Map<ApplicationExportColumn, String>> values = assembler.assemble(
                List.of(row(completed.getId()), row(exempted.getId())), List.of(MILITARY), assembler.newCodeNames());

        assertThat(values.get(0)).containsEntry(MILITARY, "필 / 육군 현역복무 / 병장 / 2015-03-02 ~ 2016-12-01");
        // 미필·면제 사유는 화면·PDF 에서도 마스킹되는 민감정보 — 엑셀에 넣지 않는다.
        assertThat(values.get(1)).containsEntry(MILITARY, "면제");
    }

    @Test
    void final_education_detail_columns_and_education_summary() {
        JobApplication application = persistApplication("asm-edu");
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.HIGH_SCHOOL, "한국고", null, null, null, null,
                LocalDate.of(2011, 3, 2), LocalDate.of(2014, 2, 10), GraduationStatus.GRADUATED,
                DayNightType.DAY, null, false, null, 0));
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.UNIVERSITY, "한국대", "경영학", "MT_X", "경제학", null,
                LocalDate.of(2014, 3, 2), LocalDate.of(2020, 2, 20), GraduationStatus.GRADUATED,
                DayNightType.NIGHT, CampusType.BRANCH, true, "ZZ", null, null,
                new BigDecimal("3.80"), new BigDecimal("4.50"), new BigDecimal("4.00"), new BigDecimal("4.50"), 1));

        Map<ApplicationExportColumn, String> values = assembleOne(application, List.of(
                FINAL_EDUCATION_LEVEL, FINAL_MAJOR, FINAL_ADDITIONAL_MAJOR, FINAL_GRADUATION_STATUS,
                FINAL_ADMISSION_DATE, FINAL_GRADUATION_DATE, FINAL_GPA, FINAL_MAJOR_GPA,
                FINAL_SCHOOL_LOCATION, FINAL_SCHOOL_TYPE, EDUCATIONS));

        assertThat(values)
                .containsEntry(FINAL_EDUCATION_LEVEL, "대학교")
                .containsEntry(FINAL_MAJOR, "경영학")
                .containsEntry(FINAL_ADDITIONAL_MAJOR, "MT_X: 경제학")
                .containsEntry(FINAL_GRADUATION_STATUS, "졸업")
                .containsEntry(FINAL_ADMISSION_DATE, "2014-03")
                .containsEntry(FINAL_GRADUATION_DATE, "2020-02")
                .containsEntry(FINAL_GPA, "3.80 / 4.50")
                .containsEntry(FINAL_MAJOR_GPA, "4.00 / 4.50")
                .containsEntry(FINAL_SCHOOL_LOCATION, "해외 (ZZ)")
                .containsEntry(FINAL_SCHOOL_TYPE, "편입, 분교, 야간")
                .containsEntry(EDUCATIONS,
                        "고등학교 / 한국고 / 2011-03-02 ~ 2014-02-10 / 졸업\n"
                                + "대학교 / 한국대 / 경영학 / 2014-03-02 ~ 2020-02-20 / 졸업 / 3.80 / 4.50");
    }

    @Test
    void one_to_many_sections_are_summarized_line_by_line_in_sort_order() {
        JobApplication application = persistApplication("asm-summary");
        careerRepository.save(ApplicationCareer.create(application, "B사", "영업팀", "대리", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 3), null, null, true, 5000, null, 1));
        careerRepository.save(ApplicationCareer.create(application, "A사", "개발팀", "사원", EmploymentType.CONTRACT,
                LocalDate.of(2019, 1, 2), LocalDate.of(2021, 12, 31), null, false, 3000, "계약만료", 0));
        certificateRepository.save(ApplicationCertificate.create(application, "정보처리기사", "한국산업인력공단",
                LocalDate.of(2019, 5, 10), "12345678", null, null, 0));
        languageRepository.save(ApplicationLanguage.create(application, "EN", "영어", "TOEIC", "TOEIC", "900", "상",
                LocalDate.of(2023, 1, 15), null, null, null, 0));
        awardRepository.save(ApplicationAward.create(application, "우수상", "한국대", LocalDate.of(2019, 11, 20), null, 0));
        gapPeriodRepository.save(ApplicationGapPeriod.create(application, LocalDate.of(2020, 3, 1),
                LocalDate.of(2020, 12, 31), GapType.OTHER, "어학연수", null, 0));
        decide(application, "서류전형", StageType.DOCUMENT, 1, StageResultStatus.PASSED);
        decide(application, "1차면접", StageType.FIRST_INTERVIEW, 2, StageResultStatus.HOLD);

        Map<ApplicationExportColumn, String> values = assembleOne(application,
                List.of(STAGE_RESULTS, CAREERS, CURRENT_SALARY, CERTIFICATES, LANGUAGES, AWARDS, GAP_PERIODS));

        assertThat(values)
                .containsEntry(STAGE_RESULTS, "서류전형: 합격\n1차면접: 보류")
                .containsEntry(CAREERS,
                        "A사 / 개발팀 / 사원 / 계약 / 2019-01-02 ~ 2021-12-31 / 계약만료\n"
                                + "B사 / 영업팀 / 대리 / 정규직 / 2022-01-03 ~ 재직중")
                .containsEntry(CURRENT_SALARY, "B사: 5,000")
                // 자격증번호는 화면·PDF 에서 마스킹되는 값이라 넣지 않는다.
                .containsEntry(CERTIFICATES, "정보처리기사 / 한국산업인력공단 / 2019-05-10")
                .containsEntry(LANGUAGES, "영어 / TOEIC / 900 / 상 / 2023-01-15")
                .containsEntry(AWARDS, "우수상 / 한국대 / 2019-11")
                .containsEntry(GAP_PERIODS, "2020-03 ~ 2020-12 / 기타 / 어학연수");
    }

    private Map<ApplicationExportColumn, String> assembleOne(JobApplication application, List<ApplicationExportColumn> columns) {
        // assembler 는 조립 후 영속성 컨텍스트를 비운다 — 그 전에 시드를 DB 로 내보낸다.
        entityManager.flush();
        return assembler.assemble(List.of(row(application.getId())), columns, assembler.newCodeNames()).get(0);
    }

    /** base projection 값은 테스트 상수로 고정한다(쿼리는 Task 6/10 이 검증). */
    private static ApplicationExportRow row(Long applicationId) {
        return new ApplicationExportRow(
                applicationId, "스냅샷", "01000000000", "acct@example.com", "공고",
                JobPositionApplicationType.NEW_GRADUATE, "Backend", null, null, JobApplicationStatus.SUBMITTED,
                LocalDateTime.of(2026, 5, 10, 10, 0), null,
                LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 10, 10, 0));
    }

    private JobApplication persistApplication(String loginId) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                loginId + " 공고",
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        JobPosition jobPosition = jobPosting.getJobPositions().stream()
                .min(Comparator.comparing(JobPosition::getSortOrder))
                .orElseThrow();

        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("스냅샷");
        applicant.setUserName("스냅샷");
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        applicant.setEmail(loginId + "@example.com");
        applicantRepository.save(applicant);

        JobApplication application = JobApplication.create(
                applicant, jobPosting, jobPosition, "스냅샷", jobPosting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 5, 10, 10, 0));
        return jobApplicationRepository.save(application);
    }

    private static ApplicationEducation education(
            JobApplication application, EducationLevel level, String schoolName, LocalDate graduationDate, int sortOrder) {
        return ApplicationEducation.create(application, level, schoolName, null, null, null, null,
                null, graduationDate, GraduationStatus.GRADUATED, DayNightType.DAY, null, false, null, sortOrder);
    }

    private void decide(JobApplication application, String stageName, StageType type, int order, StageResultStatus status) {
        Stage stage = stageRepository.save(Stage.create(
                application.getJobPosting(), stageName, type, order, LocalDateTime.of(2026, 7, 1, 10, 0), false));
        StageResult result = StageResult.initialize(stage, application);
        result.updateResult(status, null, null, LocalDateTime.of(2026, 6, 1, 10, 0), "tester");
        stageResultRepository.save(result);
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
```

참고: 엔티티 팩토리가 시드 값을 거부하면(도메인 검증) 해당 값만 도메인 규칙에 맞게 바꾸고 기대값을 함께 맞춘다. 기대 **표기 형식**은 바꾸지 않는다.

- [ ] **Step 3: 실패 확인**

Run: `... --tests "com.shinyoung.recruit.service.ApplicationExportRowAssembler*" ...`
Expected: 컴파일 실패 — `ApplicationExportRowAssembler` 없음

- [ ] **Step 4: 구현**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지원현황 엑셀 한 페이지의 셀 값을 만든다. base projection 행 + 선택 컬럼이 요구하는 섹션을 지원서 id 로
 * 배치 조회(N+1 없음)해 {@code Map<ApplicationExportColumn, String>} 으로 조립한다.
 *
 * <p>표기는 PDF({@link ApplicationPdfService})와 같다 — enum 은 {@link ApplicationPdfLabels}, 공통코드는
 * {@link CommonCodeNames}, 날짜는 같은 포맷. 연락처는 기존 엑셀처럼 저장값 그대로 둔다.
 * 화면·PDF 에서 마스킹되는 값(면제 사유, 자격증번호)은 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationExportRowAssembler {

    /**
     * 셀 최대 글자 수. Excel 한도(32,767)보다 1 작다 — writer 의 formula-injection escape 가 앞에 {@code '} 를
     * 붙여도 한도를 넘지 않게 한다.
     */
    static final int MAX_CELL_LENGTH = 32_766;
    static final String TRUNCATED_SUFFIX = "…(이하 생략)";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FIELD_SEPARATOR = " / ";
    private static final String LINE_SEPARATOR = "\n";

    /** 화면(ApplicationStatus.vue statusLabelMap)과 같은 표기. */
    private static final Map<JobApplicationStatus, String> STATUS_LABELS = new EnumMap<>(Map.of(
            JobApplicationStatus.DRAFT, "임시저장",
            JobApplicationStatus.SUBMITTED, "제출 완료",
            JobApplicationStatus.WITHDRAWN, "지원 철회"));

    // 최종학력 행 = 최고 EducationLevel(선언 순서 = 서열), 동률이면 id 가 큰 행 — 목록(JobApplicationService)·
    // 전형결과 그리드(AdminStageResultEnricher)와 같은 규칙. 한쪽만 바꾸면 화면과 엑셀의 최종학력이 어긋난다.
    private static final Comparator<ApplicationEducation> FINAL_EDUCATION = Comparator
            .comparingInt((ApplicationEducation education) -> education.getEducationLevel().ordinal())
            .thenComparing(ApplicationEducation::getId);

    // 전형 진행 순서 = stageOrder, 동률이면 stage id — 목록의 "최신 전형 결과" 판정과 같은 규칙(마지막 원소가 최신).
    private static final Comparator<StageResult> STAGE_ORDER = Comparator
            .comparing((StageResult result) -> result.getStage().getStageOrder())
            .thenComparing(result -> result.getStage().getId());

    private final ApplicationBasicInfoRepository basicInfoRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final ApplicationEducationRepository educationRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;
    private final StageResultRepository stageResultRepository;
    private final CommonCodeService commonCodeService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /** export 1회용 공통코드 캐시. 서비스가 export 시작 시 한 번 만들어 모든 페이지에 넘긴다. */
    public CommonCodeNames newCodeNames() {
        return new CommonCodeNames(commonCodeService);
    }

    /**
     * 한 페이지 base 행에 선택 컬럼 값을 채운다. 조립이 끝나면 영속성 컨텍스트를 비운다 — 값은 이미 문자열이고,
     * 비우지 않으면 5만 행 export 동안 섹션 entity 가 한 트랜잭션에 계속 쌓인다(export 트랜잭션은 read-only 라
     * 비워도 잃는 변경이 없다).
     */
    public List<Map<ApplicationExportColumn, String>> assemble(
            List<ApplicationExportRow> rows,
            List<ApplicationExportColumn> columns,
            CommonCodeNames codeNames
    ) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = rows.stream().map(ApplicationExportRow::applicationId).toList();
        Sections sections = load(applicationIds, ApplicationExportColumn.requiredSections(columns));
        LocalDate today = LocalDate.now(clock);

        List<Map<ApplicationExportColumn, String>> page = new ArrayList<>(rows.size());
        for (ApplicationExportRow row : rows) {
            Source source = source(row, sections);
            Map<ApplicationExportColumn, String> values = new EnumMap<>(ApplicationExportColumn.class);
            for (ApplicationExportColumn column : columns) {
                values.put(column, truncate(value(column, source, codeNames, today)));
            }
            page.add(values);
        }
        entityManager.clear();
        return page;
    }

    private Sections load(List<Long> ids, Set<ApplicationExportSection> required) {
        return new Sections(
                required.contains(ApplicationExportSection.BASIC_INFO)
                        ? single(basicInfoRepository.findByJobApplicationIdIn(ids), ApplicationBasicInfo::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.MILITARY)
                        ? single(militaryRepository.findByJobApplicationIdIn(ids), ApplicationMilitary::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.EDUCATION)
                        ? grouped(educationRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationEducation::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.CAREER)
                        ? grouped(careerRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationCareer::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.CERTIFICATE)
                        ? grouped(certificateRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationCertificate::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.LANGUAGE)
                        ? grouped(languageRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationLanguage::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.AWARD)
                        ? grouped(awardRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationAward::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.GAP_PERIOD)
                        ? grouped(gapPeriodRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationGapPeriod::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.STAGE_RESULT)
                        ? grouped(stageResultRepository.findWithStageByJobApplicationIdIn(ids),
                                StageResult::getJobApplication)
                        : Map.of());
    }

    private Source source(ApplicationExportRow row, Sections sections) {
        Long id = row.applicationId();
        List<ApplicationEducation> educations = sections.educations().getOrDefault(id, List.of());
        return new Source(
                row,
                sections.basicInfos().get(id),
                sections.militaries().get(id),
                educations,
                educations.stream().max(FINAL_EDUCATION).orElse(null),
                sections.careers().getOrDefault(id, List.of()),
                sections.certificates().getOrDefault(id, List.of()),
                sections.languages().getOrDefault(id, List.of()),
                sections.awards().getOrDefault(id, List.of()),
                sections.gapPeriods().getOrDefault(id, List.of()),
                sections.stageResults().getOrDefault(id, List.of()).stream().sorted(STAGE_ORDER).toList());
    }

    private String value(ApplicationExportColumn column, Source s, CommonCodeNames codes, LocalDate today) {
        ApplicationExportRow row = s.row();
        ApplicationBasicInfo info = s.basicInfo();
        ApplicationEducation fin = s.finalEducation();
        return switch (column) {
            case APPLICATION_ID -> String.valueOf(row.applicationId());
            case JOB_POSTING_TITLE -> text(row.jobPostingTitle());
            case APPLICATION_TYPE -> ApplicationPdfLabels.label(row.applicationType());
            case JOB_POSITION_NAME -> text(row.jobPositionName());
            case JOB_TITLE -> text(row.jobTitle());
            case WORK_LOCATION -> text(row.workLocationName());
            case STATUS -> row.status() == null ? "" : STATUS_LABELS.get(row.status());
            case SUBMITTED_AT -> dateTime(row.submittedAt());
            case CREATED_AT -> dateTime(row.createdAt());
            case UPDATED_AT -> dateTime(row.updatedAt());
            case WITHDRAWN_AT -> dateTime(row.withdrawnAt());

            case LATEST_STAGE_RESULT -> s.stageResults().isEmpty()
                    ? ""
                    : stageResult(s.stageResults().get(s.stageResults().size() - 1), " ");
            case STAGE_RESULTS -> lines(s.stageResults(), result -> stageResult(result, ": "));

            // 이름·연락처: 기본정보 행이 있으면 그 값(파기로 null 이어도 fallback 없음), 없으면 snapshot/계정 값 — PDF 와 같은 규칙.
            case NAME -> info != null ? text(info.getNameKorean()) : text(row.applicantName());
            case MOBILE_PHONE -> info != null ? text(info.getMobilePhone()) : text(row.phoneNumber());
            case EMAIL -> info != null ? text(info.getEmail()) : text(row.email());
            case NAME_ENGLISH -> info == null ? "" : text(info.getNameEnglish());
            case NATIONALITY -> info == null ? "" : nationality(info, codes);
            case BIRTH_DATE -> info == null ? "" : date(info.getBirthDate());
            case AGE -> info == null || info.getBirthDate() == null
                    ? ""
                    : String.valueOf(Period.between(info.getBirthDate(), today).getYears());
            case EMERGENCY_PHONE -> info == null ? "" : text(info.getEmergencyPhone());
            case ADDRESS -> info == null ? "" : address(info);
            case VETERAN -> info == null ? "" : veteran(info);
            case DISABILITY -> info == null ? "" : disability(info, codes);
            case APPLICATION_ROUTE -> info == null ? "" : codes.name("APPLICATION_ROUTE", info.getApplicationRouteCode());

            case MILITARY -> s.military() == null ? "" : military(s.military());

            case FINAL_EDUCATION_LEVEL -> fin == null ? "" : ApplicationPdfLabels.label(fin.getEducationLevel());
            case FINAL_SCHOOL_NAME -> fin == null ? "" : text(fin.getSchoolName());
            case FINAL_MAJOR -> fin == null ? "" : text(fin.getMajorName());
            case FINAL_ADDITIONAL_MAJOR -> fin == null ? "" : additionalMajor(fin, codes);
            case FINAL_GRADUATION_STATUS -> fin == null ? "" : ApplicationPdfLabels.label(fin.getGraduationStatus());
            case FINAL_ADMISSION_DATE -> fin == null ? "" : yearMonth(fin.getAdmissionDate());
            case FINAL_GRADUATION_DATE -> fin == null ? "" : yearMonth(fin.getGraduationDate());
            case FINAL_GPA -> fin == null ? "" : gradePoint(fin.getOverallGradePoint(), fin.getOverallMaxGradePoint());
            case FINAL_MAJOR_GPA -> fin == null
                    ? ""
                    : gradePoint(fin.getOverallMajorGradePoint(), fin.getOverallMajorMaxGradePoint());
            case FINAL_SCHOOL_LOCATION -> fin == null ? "" : schoolLocation(fin, codes);
            case FINAL_SCHOOL_TYPE -> fin == null ? "" : schoolType(fin);

            case EDUCATIONS -> lines(s.educations(), this::education);
            case CAREERS -> lines(s.careers(), this::career);
            case CURRENT_SALARY -> lines(
                    s.careers().stream()
                            .filter(c -> Boolean.TRUE.equals(c.getCurrentlyEmployed()) && c.getCurrentSalary() != null)
                            .toList(),
                    c -> text(c.getCompanyName()) + ": " + String.format(Locale.ROOT, "%,d", c.getCurrentSalary()));
            case CERTIFICATES -> lines(s.certificates(), this::certificate);
            case LANGUAGES -> lines(s.languages(), this::language);
            case AWARDS -> lines(s.awards(), this::award);
            case GAP_PERIODS -> lines(s.gapPeriods(), this::gapPeriod);
        };
    }

    private String stageResult(StageResult result, String separator) {
        return text(result.getStage().getStageName()) + separator + StageResultStatusLabels.label(result.getResultStatus());
    }

    /** PDF 와 같이 "외국인 (국가명)". */
    private String nationality(ApplicationBasicInfo info, CommonCodeNames codes) {
        NationalityType type = info.getNationalityType();
        if (type == null) {
            return "";
        }
        String label = ApplicationPdfLabels.label(type);
        if (type == NationalityType.DOMESTIC) {
            return label;
        }
        String country = codes.name("NATIONALITY", info.getCountryCode());
        return country.isBlank() ? label : label + " (" + country + ")";
    }

    private String address(ApplicationBasicInfo info) {
        StringBuilder builder = new StringBuilder();
        if (hasText(info.getZipCode())) {
            builder.append('(').append(info.getZipCode()).append(") ");
        }
        if (hasText(info.getAddressBasic())) {
            builder.append(info.getAddressBasic());
        }
        if (hasText(info.getAddressDetail())) {
            builder.append(", ").append(info.getAddressDetail());
        }
        return builder.toString().trim();
    }

    private String veteran(ApplicationBasicInfo info) {
        VeteranStatus status = info.getVeteranStatus();
        if (status == null) {
            return "";
        }
        String label = ApplicationPdfLabels.label(status);
        if (status == VeteranStatus.NOT_SUBJECT || !hasText(info.getVeteranType())) {
            return label;
        }
        return label + " (" + info.getVeteranType() + ")";
    }

    private String disability(ApplicationBasicInfo info, CommonCodeNames codes) {
        DisabilityStatus status = info.getDisabilityStatus();
        if (status == null) {
            return "";
        }
        String label = ApplicationPdfLabels.label(status);
        if (status == DisabilityStatus.NOT_SUBJECT) {
            return label;
        }
        return label + " (등급: " + codes.name("DISABILITY_GRADE", info.getDisabilityGradeCode())
                + " / 유형: " + codes.name("DISABILITY_TYPE", info.getDisabilityTypeCode()) + ")";
    }

    /** 군필만 군별·계급·기간을 쓴다. 미필·면제 사유는 화면·PDF 에서도 마스킹되는 민감정보라 넣지 않는다. */
    private String military(ApplicationMilitary military) {
        MilitarySubjectType type = military.getMilitarySubjectType();
        String status = ApplicationPdfLabels.label(type);
        if (type != MilitarySubjectType.COMPLETED) {
            return status;
        }
        return fields(
                status,
                (ApplicationPdfLabels.label(military.getMilitaryBranch()) + " "
                        + ApplicationPdfLabels.label(military.getServiceType())).trim(),
                ApplicationPdfLabels.label(military.getRank()),
                range(military.getServiceStartDate(), military.getServiceEndDate(), DATE));
    }

    private String additionalMajor(ApplicationEducation education, CommonCodeNames codes) {
        String type = codes.name("MAJOR_TYPE", education.getAdditionalMajorType());
        String name = text(education.getAdditionalMajorName());
        if (type.isBlank()) {
            return name;
        }
        return name.isBlank() ? type : type + ": " + name;
    }

    private String schoolLocation(ApplicationEducation education, CommonCodeNames codes) {
        if (!hasText(education.getCountryCode())) {
            return "국내";
        }
        return "해외 (" + codes.name("NATIONALITY", education.getCountryCode()) + ")";
    }

    private String schoolType(ApplicationEducation education) {
        List<String> types = new ArrayList<>();
        if (Boolean.TRUE.equals(education.getTransfer())) {
            types.add("편입");
        }
        if (education.getCampusType() == CampusType.BRANCH) {
            types.add("분교");
        }
        if (education.getDayNightType() == DayNightType.NIGHT) {
            types.add("야간");
        }
        return String.join(", ", types);
    }

    private String education(ApplicationEducation e) {
        return fields(
                ApplicationPdfLabels.label(e.getEducationLevel()),
                text(e.getSchoolName()),
                text(e.getMajorName()),
                range(e.getAdmissionDate(), e.getGraduationDate(), DATE),
                ApplicationPdfLabels.label(e.getGraduationStatus()),
                gradePoint(e.getOverallGradePoint(), e.getOverallMaxGradePoint()));
    }

    private String career(ApplicationCareer c) {
        String period = c.getEndDate() == null
                ? (c.getStartDate() == null ? "" : date(c.getStartDate()) + " ~ 재직중")
                : range(c.getStartDate(), c.getEndDate(), DATE);
        return fields(
                text(c.getCompanyName()),
                text(c.getDepartmentName()),
                text(c.getPositionTitle()),
                ApplicationPdfLabels.label(c.getEmploymentType()),
                period,
                text(c.getResignationReason()));
    }

    private String certificate(ApplicationCertificate c) {
        return fields(text(c.getCertificateName()), text(c.getIssuingOrganization()),
                date(c.getAcquiredDate()), text(c.getScoreOrGrade()));
    }

    private String language(ApplicationLanguage l) {
        return fields(text(l.getLanguageName()), text(l.getTestName()), text(l.getScoreOrGrade()),
                text(l.getConversationalAbility()), date(l.getExamDate()));
    }

    private String award(ApplicationAward a) {
        return fields(text(a.getAwardName()), text(a.getAwardingOrganization()), yearMonth(a.getAwardDate()));
    }

    private String gapPeriod(ApplicationGapPeriod g) {
        return fields(range(g.getStartDate(), g.getEndDate(), YEAR_MONTH),
                ApplicationPdfLabels.label(g.getGapType()), text(g.getReason()));
    }

    static String truncate(String value) {
        if (value.length() <= MAX_CELL_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CELL_LENGTH - TRUNCATED_SUFFIX.length()) + TRUNCATED_SUFFIX;
    }

    /** 빈 값은 빼고 " / " 로 잇는다. */
    private static String fields(String... values) {
        return Arrays.stream(values)
                .filter(ApplicationExportRowAssembler::hasText)
                .collect(Collectors.joining(FIELD_SEPARATOR));
    }

    /** 1건 = 1줄. 빈 줄은 뺀다. */
    private static <E> String lines(List<E> items, Function<E, String> line) {
        return items.stream()
                .map(line)
                .filter(ApplicationExportRowAssembler::hasText)
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private static String range(LocalDate from, LocalDate to, DateTimeFormatter formatter) {
        if (from == null && to == null) {
            return "";
        }
        return format(from, formatter) + " ~ " + format(to, formatter);
    }

    private static String gradePoint(BigDecimal point, BigDecimal max) {
        if (point == null && max == null) {
            return "";
        }
        return plain(point) + " / " + plain(max);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String date(LocalDate value) {
        return format(value, DATE);
    }

    private static String yearMonth(LocalDate value) {
        return format(value, YEAR_MONTH);
    }

    private static String format(LocalDate value, DateTimeFormatter formatter) {
        return value == null ? "" : value.format(formatter);
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <E> Map<Long, E> single(List<E> entities, Function<E, JobApplication> owner) {
        return entities.stream().collect(Collectors.toMap(e -> owner.apply(e).getId(), e -> e, (a, b) -> a));
    }

    /** repository 정렬 순서를 그대로 유지한 채 지원서별로 묶는다. */
    private static <E> Map<Long, List<E>> grouped(List<E> entities, Function<E, JobApplication> owner) {
        return entities.stream().collect(Collectors.groupingBy(
                e -> owner.apply(e).getId(), LinkedHashMap::new, Collectors.toList()));
    }

    /** 한 페이지 분량의 섹션 조회 결과(지원서 id → 값). 선택되지 않은 섹션은 빈 map. */
    private record Sections(
            Map<Long, ApplicationBasicInfo> basicInfos,
            Map<Long, ApplicationMilitary> militaries,
            Map<Long, List<ApplicationEducation>> educations,
            Map<Long, List<ApplicationCareer>> careers,
            Map<Long, List<ApplicationCertificate>> certificates,
            Map<Long, List<ApplicationLanguage>> languages,
            Map<Long, List<ApplicationAward>> awards,
            Map<Long, List<ApplicationGapPeriod>> gapPeriods,
            Map<Long, List<StageResult>> stageResults
    ) {
    }

    /** 지원서 1건의 값 원천. {@code stageResults} 는 진행 순서(마지막 = 최신). */
    private record Source(
            ApplicationExportRow row,
            ApplicationBasicInfo basicInfo,
            ApplicationMilitary military,
            List<ApplicationEducation> educations,
            ApplicationEducation finalEducation,
            List<ApplicationCareer> careers,
            List<ApplicationCertificate> certificates,
            List<ApplicationLanguage> languages,
            List<ApplicationAward> awards,
            List<ApplicationGapPeriod> gapPeriods,
            List<StageResult> stageResults
    ) {
    }
}
```

주의:
- 엔티티 getter 이름(Lombok `@Getter`)이 다르면 엔티티 쪽 이름에 맞춘다(예: `Boolean transfer` → `getTransfer()`).
- `JobApplicationStatus`/`StageResultStatus` 등에 새 상수가 생기면 이 클래스의 매핑도 함께 갱신한다(`STATUS_LABELS.get` 이 null 을 돌려주면 셀이 빈칸이 된다).

- [ ] **Step 5: 통과 확인**

Run: `... --tests "com.shinyoung.recruit.service.ApplicationExportRowAssembler*" ...`
Expected: `BUILD SUCCESSFUL` (Mockito 4 + SpringBootTest 6)

---

### Task 8: `ApplicationExportService` — 동적 spec + assembler 연결

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/ApplicationExportService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ApplicationExportServiceTest.java`

- [ ] **Step 1: 테스트 갱신 (실패 상태로)**

`ApplicationExportServiceTest`:
- import 추가: `java.util.List`, `java.util.Map`, `org.mockito.ArgumentCaptor`, `org.mockito.Captor`
- 필드 추가:

```java
    @Mock
    private ApplicationExportRowAssembler rowAssembler;

    @Captor
    private ArgumentCaptor<ExcelExportSpec<Map<ApplicationExportColumn, String>>> specCaptor;

    @Captor
    private ArgumentCaptor<ExportRowSource<Map<ApplicationExportColumn, String>>> sourceCaptor;
```

- `setUp()` 생성자 인자 마지막에 `rowAssembler` 추가:

```java
        applicationExportService = new ApplicationExportService(
                jobApplicationRepository,
                jobPostingRepository,
                excelExportWriter,
                exportProperties,
                new AdminApplicationSearchConditionFactory(),
                rowAssembler
        );
```

- 기존 두 테스트의 호출 `applicationExportService.exportApplications(null, null)` 을 모두 `applicationExportService.exportApplications(null, null, ApplicationExportColumn.defaults())` 로 바꾼다.
- 새 테스트 추가:

```java
    @Test
    void sheet_follows_selected_columns_and_rows_go_through_assembler() throws IOException {
        Path tempFile = Files.createTempFile("columns-", ".xlsx");
        try {
            List<ApplicationExportColumn> columns = List.of(ApplicationExportColumn.APPLICATION_ID, ApplicationExportColumn.CAREERS);
            CommonCodeNames codeNames = new CommonCodeNames(null);
            given(exportProperties.getMaxRows()).willReturn(50_000L);
            given(jobApplicationRepository.countExportApplications(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).willReturn(1L);
            given(rowAssembler.newCodeNames()).willReturn(codeNames);
            given(excelExportWriter.writeToTempFile(any(), any())).willReturn(tempFile);

            applicationExportService.exportApplications(null, null, columns);

            verify(excelExportWriter).writeToTempFile(specCaptor.capture(), sourceCaptor.capture());
            ExcelExportSpec<Map<ApplicationExportColumn, String>> spec = specCaptor.getValue();
            assertThat(spec.columns()).extracting(ExportColumn::header).containsExactly("수험번호", "경력");
            assertThat(spec.columns()).extracting(ExportColumn::wrapText).containsExactly(false, true);
            assertThat(spec.columns().get(1).value(Map.of(ApplicationExportColumn.CAREERS, "A사"))).isEqualTo("A사");

            // 페이지 소스는 base 조회 결과를 assembler 로 넘긴다(repository mock 은 빈 목록을 돌려준다).
            sourceCaptor.getValue().fetch(0, 1_000);
            verify(rowAssembler).assemble(List.of(), columns, codeNames);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
```

- [ ] **Step 2: 실패 확인**

Run: `... --tests "com.shinyoung.recruit.service.ApplicationExportServiceTest" ...`
Expected: 컴파일 실패 — 생성자 인자 수 / `exportApplications(Long, AdminApplicationSearchRequest, List)` 없음

- [ ] **Step 3: 구현**

`ApplicationExportService` 변경:
- `TIMESTAMP`, `APPLICATIONS_SPEC`, `toText(...)` 삭제, 관련 import(`DateTimeFormatter`, `LocalDateTime`) 삭제, `java.util.Map` import 추가.
- 필드 마지막에 `private final ApplicationExportRowAssembler rowAssembler;` 추가.
- 클래스 javadoc 첫 문단 아래에 한 줄 추가: `컬럼은 호출자가 고른 {@link ApplicationExportColumn} 목록(카탈로그 순)이고, 셀 값은 {@link ApplicationExportRowAssembler} 가 페이지 단위로 만든다.`
- `exportApplications` 교체:

```java
    @Transactional(readOnly = true)
    public ExcelExportFile exportApplications(
            Long jobPostingId,
            AdminApplicationSearchRequest request,
            List<ApplicationExportColumn> columns
    ) {
        if (jobPostingId != null && !jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId);
        }
        AdminApplicationSearchCondition condition = searchConditionFactory.create(jobPostingId, request);

        long total = countExportApplications(condition);
        long maxRows = exportProperties.getMaxRows();
        if (total > maxRows) {
            throw new ExportRowLimitExceededException(total, maxRows);
        }

        ExcelExportSpec<Map<ApplicationExportColumn, String>> spec = buildSpec(columns);
        CommonCodeNames codeNames = rowAssembler.newCodeNames();
        // try 범위는 writer 호출 이후로 좁힌다. 위의 검증 예외(row cap/not found/invalid status)는
        // export generation 실패로 감싸지 않고 그대로 전파한다.
        try {
            Path tempFile = excelExportWriter.writeToTempFile(
                    spec,
                    (page, size) -> rowAssembler.assemble(
                            findExportApplications(condition, PageRequest.of(page, size)), columns, codeNames)
            );
            return new ExcelExportFile(tempFile, buildFileName(jobPostingId), total);
        } catch (IOException | RuntimeException e) {
            throw new ExportGenerationException("applications export 파일 생성 실패", e);
        }
    }

    /** 선택 컬럼(카탈로그 순)으로 시트를 정의한다. 헤더 = 카탈로그 라벨, 1:N 요약 컬럼은 셀 줄바꿈. */
    private static ExcelExportSpec<Map<ApplicationExportColumn, String>> buildSpec(List<ApplicationExportColumn> columns) {
        return new ExcelExportSpec<>(
                "applications",
                columns.stream()
                        .map(column -> new ExportColumn<Map<ApplicationExportColumn, String>>(
                                column.label(), values -> values.get(column), false, column.wrapText()))
                        .toList());
    }
```

- [ ] **Step 4: 통과 확인**

Run: Step 2 명령
Expected: `BUILD SUCCESSFUL` (3 tests). 컨트롤러는 아직 2-인자 호출이라 `compileJava`가 깨진다 — Task 10에서 고친다. 이 Step은 `compileTestJava`까지 필요하므로 **Task 8과 Task 10을 연달아 진행하고 Task 10 Step 5에서 함께 실행**해도 된다.

---

### Task 9: 감사 로그에 `columns` 기록

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/ExportAuditLogger.java:95-107`
- Test: `src/test/java/com/shinyoung/recruit/service/ExportAuditLoggerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExportAuditLoggerTest {

    @Mock
    private ActivityLogService activityLogService;

    @Test
    void applications_export_audit_records_selected_column_keys() {
        ExportAuditLogger logger = new ExportAuditLogger(Clock.systemDefaultZone(), activityLogService);
        ExportAuditContext context = new ExportAuditContext("admin01", "ROLE_ADMIN", "127.0.0.1", "test-agent", "req-1");
        ExcelExportFile file = new ExcelExportFile(Path.of("dummy.xlsx"), "applications-export.xlsx", 3L);

        logger.logApplicationsExport(context, 7L, null, "SUBMITTED", List.of("APPLICATION_ID", "NAME"), file);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(activityLogService).recordRequiresNew(captor.capture());
        ExportMetadata metadata = (ExportMetadata) captor.getValue().metadata();
        // 어떤 항목까지 반출했는지만 남긴다(값은 남기지 않는다).
        assertThat(metadata.filtersSafeJson()).contains("\"columns\":[\"APPLICATION_ID\",\"NAME\"]");
        assertThat(captor.getValue().jobPostingId()).isEqualTo(7L);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `... --tests "com.shinyoung.recruit.service.ExportAuditLoggerTest" ...`
Expected: 컴파일 실패 — `logApplicationsExport` 인자 수 불일치

- [ ] **Step 3: 구현** — `logApplicationsExport` 교체(`java.util.List` import 추가)

```java
    /**
     * 지원현황 export audit. {@code columns} 는 반출한 엑셀 컬럼 key(카탈로그 순)로, 어떤 개인정보 항목까지
     * 내보냈는지 추적하는 용도다. 값 자체는 남기지 않는다.
     */
    public void logApplicationsExport(
            ExportAuditContext context,
            Long jobPostingId,
            Long jobPositionId,
            String status,
            List<String> columns,
            ExcelExportFile file
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("jobPostingId", jobPostingId);
        filters.put("jobPositionId", jobPositionId);
        filters.put("status", status);
        filters.put("columns", columns);
        logExport("APPLICATIONS", context, filters, file);
    }
```

- [ ] **Step 4: 통과 확인** — Task 10 Step 5에서 함께 실행(컨트롤러 호출부가 아직 구 시그니처).

---

### Task 10: 컨트롤러 — 카탈로그 API + `columns` 파라미터

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/controller/AdminExportController.java`
- Test: `src/test/java/com/shinyoung/recruit/controller/AdminExportControllerTest.java`

- [ ] **Step 1: 기존 테스트를 한글 헤더·헤더명 조회로 갱신**

`AdminExportControllerTest`:
- import 추가: `java.util.LinkedHashMap`, `java.util.Map`, `static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath`
- `EXPECTED_HEADER` 교체:

```java
    private static final List<String> EXPECTED_HEADER = List.of(
            "수험번호", "지원분야", "근무지", "지원상태", "최종제출일시", "최신 전형결과",
            "이름", "생년월일", "나이", "휴대폰", "이메일", "최종학력", "최종학교", "졸업년월"
    );
```

- `dataRowsOf` 를 아래 두 헬퍼로 **교체**하고, 파일 안 모든 `dataRowsOf(` 호출을 `dataRecordsOf(` 로 바꾼다:

```java
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
```

- 인덱스 단언을 헤더명 단언으로 바꾼다(변수 타입도 `List<Map<String, String>>`):

| 테스트 | 기존 | 변경 |
| --- | --- | --- |
| `export_applications_returns_xlsx_with_contact_columns_and_no_sensitive_columns` | `row.get(1/2/3)` | `row.get("이름")` = `"Applicant A"`, `row.get("휴대폰")` = `"01011112222"`, `row.get("이메일")` = `"a@example.com"` (`dataRows` 는 `recordsOf(sheet)`) |
| `export_applications_filters_by_status` | `row.get(6)` = `"SUBMITTED"` | `row.get("지원상태")` = `"제출 완료"` |
| `export_applications_filters_by_name_like_the_list_query` | `row.get(1)` | `row.get("이름")` |
| `export_applications_filters_by_phone_number_ignoring_hyphen` | `rows.get(0).get(1)` | `rows.get(0).get("이름")` |
| `export_applications_filters_by_job_position` | `row.get(5)` | `row.get("지원분야")` |
| `export_escapes_formula_injection_in_free_text_cells` | `dataRows.get(0).get(1)` | `dataRows.get(0).get("이름")` |

- 새 테스트 3건 추가:

```java
    @Test
    void export_applications_writes_only_requested_columns_in_catalog_order() throws Exception {
        Long jobPostingId = createJobPosting("columns-select");
        persistApplication(jobPostingId, "col-1", "컬럼대상", "01012340000", "col@example.com", true, false);

        MvcResult result = performExport(get("/api/admin/applications/export")
                .param("columns", "EMAIL,APPLICATION_ID,NAME")
                .with(authentication(adminAuthentication())));

        List<List<String>> sheet = readSheet(result.getResponse().getContentAsByteArray());
        assertThat(sheet.get(0)).containsExactly("수험번호", "이름", "이메일");
        assertThat(sheet.get(1).get(1)).isEqualTo("컬럼대상");
        assertThat(sheet.get(1).get(2)).isEqualTo("col@example.com");
    }

    @Test
    void export_applications_rejects_unknown_column() throws Exception {
        createJobPosting("columns-invalid");

        mockMvc.perform(get("/api/admin/applications/export")
                        .param("columns", "APPLICATION_ID,NOT_A_COLUMN")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest());
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
```

- `export_blocks_applicant_and_anonymous` 끝에 추가:

```java
        mockMvc.perform(get("/api/admin/applications/export/columns")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());
```

- [ ] **Step 2: 실패 확인**

Run: `... --tests "com.shinyoung.recruit.controller.AdminExportControllerTest" ...`
Expected: 컴파일 실패(컨트롤러의 서비스·감사 호출 시그니처 불일치)

- [ ] **Step 3: 구현** — `AdminExportController`

import 추가:

```java
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicationExportColumnGroupResponse;
import com.shinyoung.recruit.service.ApplicationExportColumn;
import java.util.List;
```

두 export 엔드포인트에 파라미터 추가 후 `export(...)` 로 전달:

```java
    @GetMapping("/admin/applications/export")
    public ResponseEntity<StreamingResponseBody> exportApplications(
            @RequestParam(required = false) Long jobPostingId,
            @ModelAttribute AdminApplicationSearchRequest searchRequest,
            @RequestParam(required = false) List<String> columns,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        return export(jobPostingId, searchRequest, columns, userDetails, request);
    }

    @GetMapping("/admin/job-postings/{jobPostingId}/applications/export")
    public ResponseEntity<StreamingResponseBody> exportApplicationsByJobPosting(
            @PathVariable Long jobPostingId,
            @ModelAttribute AdminApplicationSearchRequest searchRequest,
            @RequestParam(required = false) List<String> columns,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        return export(jobPostingId, searchRequest, columns, userDetails, request);
    }
```

`export(...)` 교체:

```java
    /**
     * {@code columns} 는 콤마로 이은 카탈로그 key(Spring 이 목록으로 나눈다). 검색 조건 DTO 에 넣지 않는다 —
     * 목록 조회 요청과 공유하는 DTO 를 엑셀 전용 값으로 오염시키지 않기 위해서다.
     */
    private ResponseEntity<StreamingResponseBody> export(
            Long jobPostingId,
            AdminApplicationSearchRequest searchRequest,
            List<String> columnKeys,
            CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        List<ApplicationExportColumn> columns = ApplicationExportColumn.parse(columnKeys);
        ExcelExportFile file = applicationExportService.exportApplications(jobPostingId, searchRequest, columns);
        // egress fail-close(Phase 09b): 감사 기록 실패 시 응답 없이 전파 — temp xlsx 누수 방지(리뷰 2차 #3).
        try {
            exportAuditLogger.logApplicationsExport(
                    auditContext(actor, userDetails, request),
                    jobPostingId,
                    searchRequest.jobPositionId(),
                    // audit filter 에는 raw 입력이 아니라 canonical 값(enum name)을 남긴다(9b 리뷰 Medium 2).
                    canonicalStatus(searchRequest.status()),
                    columns.stream().map(Enum::name).toList(),
                    file
            );
            return excelExportResponseFactory.toResponse(file);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }
```

카탈로그 엔드포인트 추가(`export(...)` 위):

```java
    /** 엑셀 컬럼 카탈로그(모달 체크박스 원천). 항목 정의는 {@link ApplicationExportColumn} 이 단일 출처다. */
    @GetMapping("/admin/applications/export/columns")
    public ResponseEntity<ApiResponse<List<ApplicationExportColumnGroupResponse>>> exportApplicationColumns() {
        return ResponseEntity.ok(ApiResponse.success(ApplicationExportColumn.catalog()));
    }
```

- [ ] **Step 4: 전체 컴파일 확인**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat compileJava compileTestJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Task 8~10 테스트 실행**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationExportServiceTest" --tests "com.shinyoung.recruit.service.ExportAuditLoggerTest" --tests "com.shinyoung.recruit.controller.AdminExportControllerTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`

---

### Task 11: S1 범위 테스트 일괄 실행

- [ ] **Step 1: 수정 패키지 테스트**

Run: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.*" --tests "com.shinyoung.recruit.controller.AdminExportControllerTest" --tests "com.shinyoung.recruit.controller.AdminApplicationControllerTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`. 실패하면 원인을 고친 뒤 재실행하고, 실패 내역·조치를 S1 보고에 남긴다.

- [ ] **Step 2: S1 보고(마크다운 요약만)**

변경 파일 / 테스트 결과 / 계약 영향(🟡 유지 — 🟢 확정은 S2 마지막) / 남은 이슈를 보고한다. HTML 구현 리포트는 S2 완료 후 1회.
