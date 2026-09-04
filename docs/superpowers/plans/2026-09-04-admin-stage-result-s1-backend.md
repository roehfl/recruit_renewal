# 전형결과 관리 S1 (백엔드) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 전형결과 관리 화면(S2~S4)이 필요로 하는 백엔드 4건을 넣는다 — 결과 응답 그리드 열 확장, 진행 중 단계 발표일시 수정 허용, 엑셀 템플릿 한글화(헤더·라벨·드롭다운·읽기전용 음영), 부분 판정 업로드 허용.

**Architecture:** 신규 엔티티·테이블 없음. `AdminStageResultResponse`에 필드를 덧붙이고 새 컴포넌트 `AdminStageResultEnricher`가 학력·직전 단계 결과를 배치 조회해 채운다. 엑셀은 공용 writer(`ExcelExportWriter`)에 "읽기전용 열 음영 + 시트 데코레이터" 훅을 추가하고, 업로드 라벨 변환은 `StageResultStatusLabels` 한 곳에서 담당한다. 업로드 검증 순서를 바꿔 "대기 그대로"를 미변경으로 분류한다.

**Tech Stack:** Java 17, Spring Boot 4, JPA(H2 테스트), Apache POI 5.3.0(SXSSF), JUnit 5 + AssertJ + MockMvc.

**설계서:** `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md` §5 (API 계약 변경분).

**작업 루트:** 모든 경로는 `recruit_back/recruit_backend/` 기준. 테스트 실행은 그 디렉터리에서:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "<패턴>" --no-daemon
```

**커밋:** 프로젝트 규칙(`recruit/CLAUDE.md` §6)상 사용자가 명시 요청할 때만 커밋한다. 각 Task는 테스트 통과로 끝내고, 커밋 단계는 두지 않는다.

**문서:** 통합 화면 슬라이스이므로 백엔드 `docs/codex` 이중 문서화는 생략(`recruit/CLAUDE.md` §7). `api-contract.md` 🟡 기재(Task 7)만 한다. HTML 리포트는 전체 구현(S4) 완료 후 1회만 만든다(사용자 지시).

---

## 파일 구조

| 파일 | 역할 | 변경 |
| --- | --- | --- |
| `src/main/java/com/shinyoung/recruit/service/ExportColumn.java` | 컬럼 정의에 `readOnly` 추가 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/ExcelSheetDecorator.java` | 행 기록 후 시트에 검증/틀고정 등을 붙이는 훅 | 신규 |
| `src/main/java/com/shinyoung/recruit/service/ExcelExportSpec.java` | 데코레이터 보유 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/ExcelExportWriter.java` | 읽기전용 열 음영 + 데코레이터 호출 | 수정 |
| `src/test/java/com/shinyoung/recruit/service/ExcelExportWriterTest.java` | writer 단위 테스트 | 신규 |
| `src/main/java/com/shinyoung/recruit/service/StageResultStatusLabels.java` | 결과 한글 라벨 ↔ enum 변환 | 신규 |
| `src/test/java/com/shinyoung/recruit/service/StageResultStatusLabelsTest.java` | 라벨 변환 테스트 | 신규 |
| `src/main/java/com/shinyoung/recruit/service/StageResultUploadParser.java` | 한글 헤더 시그니처 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/StageResultUploadService.java` | 템플릿 라벨·음영·드롭다운, 라벨 파싱, 대기=미변경 규칙, 한글 오류 문구 | 수정 |
| `src/test/java/com/shinyoung/recruit/controller/StageResultUploadControllerTest.java` | 헤더 상수 갱신 + 신규 테스트 5건 | 수정 |
| `src/main/java/com/shinyoung/recruit/dto/response/AdminStageResultResponse.java` | 그리드 열 6개 추가, `Enrichment` | 수정 |
| `src/main/java/com/shinyoung/recruit/service/AdminStageResultEnricher.java` | 학력·직전 단계 결과 배치 조회 → 응답 조립 | 신규 |
| `src/main/java/com/shinyoung/recruit/service/StageResultService.java` | enricher 사용 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/StageResultCorrectionService.java` | enricher 사용 | 수정 |
| `src/test/java/com/shinyoung/recruit/service/StageResultServiceTest.java` | 그리드 열 테스트 | 수정 |
| `src/main/java/com/shinyoung/recruit/domain/entity/Stage.java` | 발표일시 단독 변경 메서드 | 수정 |
| `src/main/java/com/shinyoung/recruit/service/StageService.java` | IN_PROGRESS 발표일시만 허용 | 수정 |
| `src/test/java/com/shinyoung/recruit/service/StageServiceTest.java` | 완화 규칙 테스트 3건 | 수정 |
| `recruit/api-contract.md` | "전형결과 관리" 섹션 🟡 | 수정 |

---

### Task 1: Excel writer — 읽기전용 열 음영 + 시트 데코레이터 훅

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/ExportColumn.java`
- Create: `src/main/java/com/shinyoung/recruit/service/ExcelSheetDecorator.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/ExcelExportSpec.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/ExcelExportWriter.java`
- Test: `src/test/java/com/shinyoung/recruit/service/ExcelExportWriterTest.java`

배경: writer는 SXSSF(streaming)라 100행 넘으면 앞 행에 다시 접근할 수 없다. 그래서 음영은 **셀을 쓸 때** 적용하고, DataValidation·틀고정처럼 시트 단위 메타데이터는 행 기록 후 데코레이터에서 붙인다. 기존 2-인자 생성자는 그대로 두어 호출부(spec 5곳, column 54곳)를 건드리지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/shinyoung/recruit/service/ExcelExportWriterTest.java`:

```java
package com.shinyoung.recruit.service;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읽기전용 열 음영과 시트 데코레이터 훅을 실제 xlsx로 다시 읽어 고정한다(Spring 컨텍스트 없음).
 */
class ExcelExportWriterTest {

    private final ExcelExportWriter writer = new ExcelExportWriter();

    @Test
    void read_only_columns_are_shaded_and_decorator_runs_after_rows() throws Exception {
        AtomicInteger decoratedRows = new AtomicInteger(-1);
        ExcelExportSpec<String> spec = new ExcelExportSpec<>(
                "sheet",
                List.of(
                        new ExportColumn<>("id", value -> value, true),
                        new ExportColumn<>("value", value -> value)),
                (sheet, dataRowCount) -> {
                    decoratedRows.set(dataRowCount);
                    sheet.createFreezePane(0, 1);
                });

        Path file = writer.writeToTempFile(spec, ExportRowSource.ofList(List.of("a", "b")), false);
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(sheet.getRow(1).getCell(0).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(sheet.getRow(1).getCell(1).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.NO_FILL);
            assertThat(sheet.getPaneInformation()).isNotNull();
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(decoratedRows.get()).isEqualTo(2);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void spec_without_decorator_still_writes_plain_cells() throws Exception {
        ExcelExportSpec<String> spec = new ExcelExportSpec<>(
                "sheet", List.of(new ExportColumn<>("value", value -> value)));

        Path file = writer.writeToTempFile(spec, ExportRowSource.ofList(List.of("x")), true);
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("x");
            assertThat(sheet.getRow(1).getCell(0).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.NO_FILL);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ExcelExportWriterTest" --no-daemon
```

Expected: FAIL — `ExportColumn` 3-인자 생성자 없음, `ExcelExportSpec` 3-인자 생성자 없음(컴파일 오류).

- [ ] **Step 3: `ExportColumn`에 `readOnly` 추가**

`src/main/java/com/shinyoung/recruit/service/ExportColumn.java` 전체:

```java
package com.shinyoung.recruit.service;

import java.util.function.Function;

/**
 * Excel export 시트의 단일 컬럼 정의: header 텍스트 + row → 셀 문자열 추출기.
 *
 * <p>{@code readOnly}가 true면 writer가 header·data 셀에 회색 음영을 넣는다(사용자에게 "수정 금지" 열임을
 * 시각적으로 알리는 용도 — 값 보호는 upload parser의 교차검증이 담당한다).
 *
 * @param <T> export row 타입(projection DTO)
 */
public record ExportColumn<T>(String header, Function<T, String> extractor, boolean readOnly) {

    public ExportColumn(String header, Function<T, String> extractor) {
        this(header, extractor, false);
    }

    public String value(T row) {
        String extracted = extractor.apply(row);
        return extracted == null ? "" : extracted;
    }
}
```

- [ ] **Step 4: `ExcelSheetDecorator` 생성**

`src/main/java/com/shinyoung/recruit/service/ExcelSheetDecorator.java`:

```java
package com.shinyoung.recruit.service;

import org.apache.poi.ss.usermodel.Sheet;

/**
 * 모든 행을 기록한 뒤 시트 단위 메타데이터(데이터 유효성 검사, 틀 고정 등)를 덧붙이는 훅.
 * SXSSF는 flush된 행을 다시 열 수 없으므로 셀 값·스타일은 여기서 만지지 않는다.
 */
@FunctionalInterface
public interface ExcelSheetDecorator {

    /**
     * @param sheet        기록이 끝난 시트(header = 0행)
     * @param dataRowCount header를 제외한 데이터 행 수
     */
    void decorate(Sheet sheet, int dataRowCount);
}
```

- [ ] **Step 5: `ExcelExportSpec`에 데코레이터 보유**

`src/main/java/com/shinyoung/recruit/service/ExcelExportSpec.java` 전체:

```java
package com.shinyoung.recruit.service;

import java.util.List;

/**
 * 하나의 dataset에 대한 Excel export 정의: 시트 이름 + 컬럼 목록(header + value extractor) + 선택적 시트 데코레이터.
 * dataset마다 "컬럼 정의 + row mapper"만 선언하면 되어 07b 이후 dataset 추가 비용을 낮춘다.
 *
 * @param decorator 행 기록 후 시트에 검증/틀고정 등을 붙이는 훅. 없으면 null
 * @param <T> export row 타입(projection DTO)
 */
public record ExcelExportSpec<T>(String sheetName, List<ExportColumn<T>> columns, ExcelSheetDecorator decorator) {

    public ExcelExportSpec {
        columns = List.copyOf(columns);
    }

    public ExcelExportSpec(String sheetName, List<ExportColumn<T>> columns) {
        this(sheetName, columns, null);
    }
}
```

- [ ] **Step 6: writer에 음영·데코레이터 적용**

`src/main/java/com/shinyoung/recruit/service/ExcelExportWriter.java`에서 `writeToTempFile(spec, rowSource, escapeFormulaPrefix)` 본문과 `writeHeader`/`writeStringCell`을 아래로 교체한다(클래스 javadoc·상수·`nullSafe`·`sanitize`는 그대로).

```java
    public <T> Path writeToTempFile(
            ExcelExportSpec<T> spec,
            ExportRowSource<T> rowSource,
            boolean escapeFormulaPrefix
    ) throws IOException {
        Path tempFile = Files.createTempFile(TEMP_PREFIX, TEMP_SUFFIX);
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW);
        try (OutputStream out = Files.newOutputStream(tempFile)) {
            Sheet sheet = workbook.createSheet(spec.sheetName());
            List<ExportColumn<T>> columns = spec.columns();
            CellStyle readOnlyStyle = createReadOnlyStyle(workbook);

            writeHeader(sheet, columns, escapeFormulaPrefix, readOnlyStyle);

            int rowIndex = 1;
            int page = 0;
            while (true) {
                List<T> batch = rowSource.fetch(page++, PAGE_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (T row : batch) {
                    Row excelRow = sheet.createRow(rowIndex++);
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        ExportColumn<T> column = columns.get(columnIndex);
                        writeStringCell(excelRow, columnIndex, column.value(row), escapeFormulaPrefix,
                                column.readOnly() ? readOnlyStyle : null);
                    }
                }
                if (batch.size() < PAGE_SIZE) {
                    break;
                }
            }

            if (spec.decorator() != null) {
                spec.decorator().decorate(sheet, rowIndex - 1);
            }

            workbook.write(out);
            return tempFile;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        } finally {
            // 예외 경로에서도 SXSSF 내부 temp file이 남지 않도록 항상 dispose() 한다.
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException closeError) {
                // close 실패는 무시한다. dispose()로 SXSSF temp file은 이미 정리되었다.
            }
        }
    }

    /** 읽기전용 열 음영(회색 25%). workbook당 한 번만 만든다 — 셀마다 style을 만들면 style 한도를 넘는다. */
    private CellStyle createReadOnlyStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private <T> void writeHeader(
            Sheet sheet,
            List<ExportColumn<T>> columns,
            boolean escapeFormulaPrefix,
            CellStyle readOnlyStyle
    ) {
        Row header = sheet.createRow(0);
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            ExportColumn<T> column = columns.get(columnIndex);
            writeStringCell(header, columnIndex, column.header(), escapeFormulaPrefix,
                    column.readOnly() ? readOnlyStyle : null);
        }
    }

    private void writeStringCell(
            Row row,
            int columnIndex,
            String value,
            boolean escapeFormulaPrefix,
            CellStyle style
    ) {
        Cell cell = row.createCell(columnIndex, CellType.STRING);
        cell.setCellValue(escapeFormulaPrefix ? sanitize(value) : nullSafe(value));
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
```

import 추가:

```java
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
```

- [ ] **Step 7: 테스트 통과 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ExcelExportWriterTest" --tests "com.shinyoung.recruit.service.ExcelExportServiceTest" --tests "com.shinyoung.recruit.service.ApplicationExportServiceTest" --no-daemon
```

Expected: PASS (기존 export 테스트 회귀 없음 — 2-인자 생성자 유지).

---

### Task 2: `StageResultStatusLabels` — 한글 라벨 ↔ enum

**Files:**
- Create: `src/main/java/com/shinyoung/recruit/service/StageResultStatusLabels.java`
- Test: `src/test/java/com/shinyoung/recruit/service/StageResultStatusLabelsTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.StageResultStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StageResultStatusLabelsTest {

    @Test
    void label_maps_every_status_to_korean() {
        assertThat(StageResultStatusLabels.label(StageResultStatus.PENDING)).isEqualTo("대기");
        assertThat(StageResultStatusLabels.label(StageResultStatus.PASSED)).isEqualTo("합격");
        assertThat(StageResultStatusLabels.label(StageResultStatus.FAILED)).isEqualTo("불합격");
        assertThat(StageResultStatusLabels.label(StageResultStatus.HOLD)).isEqualTo("보류");
        assertThat(StageResultStatusLabels.label(StageResultStatus.ABSENT)).isEqualTo("결시");
        assertThat(StageResultStatusLabels.label(StageResultStatus.WITHDRAWN)).isEqualTo("철회");
        assertThat(StageResultStatusLabels.label(null)).isEmpty();
    }

    @Test
    void parse_accepts_korean_label_and_enum_name_case_insensitively() {
        assertThat(StageResultStatusLabels.parse("합격")).contains(StageResultStatus.PASSED);
        assertThat(StageResultStatusLabels.parse(" 불합격 ")).contains(StageResultStatus.FAILED);
        assertThat(StageResultStatusLabels.parse("PASSED")).contains(StageResultStatus.PASSED);
        assertThat(StageResultStatusLabels.parse("hold")).contains(StageResultStatus.HOLD);
        assertThat(StageResultStatusLabels.parse("대기")).contains(StageResultStatus.PENDING);
    }

    @Test
    void parse_rejects_unknown_or_blank() {
        assertThat(StageResultStatusLabels.parse("합")).isEmpty();
        assertThat(StageResultStatusLabels.parse("")).isEmpty();
        assertThat(StageResultStatusLabels.parse(null)).isEmpty();
    }

    @Test
    void upload_choices_exclude_pending() {
        assertThat(StageResultStatusLabels.uploadChoices())
                .containsExactly("합격", "불합격", "보류", "결시", "철회");
    }
}
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.StageResultStatusLabelsTest" --no-daemon
```

Expected: FAIL — 클래스 없음(컴파일 오류).

- [ ] **Step 3: 구현**

`src/main/java/com/shinyoung/recruit/service/StageResultStatusLabels.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 전형 결과 상태의 관리자용 한글 라벨. 엑셀 업로드 템플릿(prefill·드롭다운)과 업로드 파싱이 같은 표를 쓴다.
 * 파싱은 한글 라벨과 enum 이름(대소문자 무시)을 모두 받아, 이전 영문 템플릿 값도 그대로 해석된다.
 */
public final class StageResultStatusLabels {

    private static final Map<StageResultStatus, String> LABELS = new EnumMap<>(Map.of(
            StageResultStatus.PENDING, "대기",
            StageResultStatus.PASSED, "합격",
            StageResultStatus.FAILED, "불합격",
            StageResultStatus.HOLD, "보류",
            StageResultStatus.ABSENT, "결시",
            StageResultStatus.WITHDRAWN, "철회"));

    /** 업로드에서 선택 가능한 값 = 전체 − 대기(PENDING). 드롭다운 목록 순서. */
    private static final List<String> UPLOAD_CHOICES = List.of("합격", "불합격", "보류", "결시", "철회");

    private StageResultStatusLabels() {
    }

    public static String label(StageResultStatus status) {
        return status == null ? "" : LABELS.get(status);
    }

    public static Optional<StageResultStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        for (Map.Entry<StageResultStatus, String> entry : LABELS.entrySet()) {
            if (entry.getValue().equals(value)) {
                return Optional.of(entry.getKey());
            }
        }
        try {
            return Optional.of(StageResultStatus.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static List<String> uploadChoices() {
        return UPLOAD_CHOICES;
    }
}
```

- [ ] **Step 4: 통과 확인**

같은 명령. Expected: PASS.

---

### Task 3: 업로드 템플릿 한글화 + 라벨 파싱 + 부분 판정 업로드

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/service/StageResultUploadParser.java` (`HEADERS`, 헤더 오류 문구)
- Modify: `src/main/java/com/shinyoung/recruit/service/StageResultUploadService.java`
- Test: `src/test/java/com/shinyoung/recruit/controller/StageResultUploadControllerTest.java`

규칙(설계서 §5.3):
- 헤더 7개 한글 고정. 앞 4열 읽기전용 음영. 헤더 행 틀고정. 결과 열(4)에 드롭다운(합격/불합격/보류/결시/철회).
- 결과 값은 한글 라벨·enum명 모두 파싱. 빈칸은 오류.
- 파일 값 `대기` + DB 현재값 PENDING → **미변경**. 판정된 행을 `대기`로 → 오류. `대기` 유지하면서 점수·코멘트만 바꿈 → 오류(백엔드 bulk가 PENDING을 거부하므로 미리 막는다).

- [ ] **Step 1: 컨트롤러 테스트 헤더 상수 갱신 + 신규 테스트 5건 추가**

`StageResultUploadControllerTest.java`의 `HEADER` 상수를 파서 상수 참조로 바꾼다:

```java
    private static final List<String> HEADER = StageResultUploadParser.HEADERS;
```

`// ---------- template ----------` 블록의 기존 `upload_template_returns_xlsx_with_prefilled_values_and_no_pii_columns` 아래에 추가:

```java
    @Test
    void upload_template_uses_korean_headers_labels_shading_and_result_dropdown() throws Exception {
        Fixture fixture = fixtureWithResults("A", "B");

        MvcResult result = performTemplate(fixture.stageId());

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
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
```

`// ---------- preview ----------` 블록 끝에 추가:

```java
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
```

`// ---------- commit ----------` 블록 끝에 추가:

```java
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
```

import 추가(테스트 파일 상단):

```java
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import java.io.ByteArrayInputStream;
```

(`XSSFWorkbook`, `Sheet`, `MvcResult`, `StageResult`는 이미 import되어 있다 — 없으면 추가.)

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.StageResultUploadControllerTest" --no-daemon
```

Expected: FAIL — 신규 5건 실패(헤더 영문, `합격` 파싱 오류, `대기` 오류, 드롭다운 없음). 기존 테스트는 `HEADER`가 파서 상수를 참조하므로 통과.

- [ ] **Step 3: 파서 헤더 한글화**

`StageResultUploadParser.java`의 `HEADERS`를 교체:

```java
    /** upload-template 헤더 시그니처(순서 고정). 앞 4열은 읽기전용 echo/토큰, 뒤 3열만 편집 대상. */
    public static final List<String> HEADERS = List.of(
            "시스템ID(수정금지)", "수험번호(수정금지)", "이름(수정금지)", "수정토큰(수정금지)",
            "결과", "점수", "코멘트");
```

`validateHeader`의 두 오류 문구를 각각 아래로 바꾼다:

```java
                throw new InvalidStageResultUploadException("업로드 템플릿 헤더가 없습니다. 엑셀 템플릿 다운로드 파일을 사용하세요.");
```

```java
                throw new InvalidStageResultUploadException(
                        "업로드 템플릿 헤더가 올바르지 않습니다. 엑셀 템플릿 다운로드 파일을 사용하세요.");
```

(`StageResultUploadParserTest.rejects_wrong_header_signature`는 "헤더" 포함 여부만 검사하므로 그대로 통과.)

- [ ] **Step 4: 서비스 — 템플릿 스펙(라벨·음영·드롭다운·틀고정)**

`StageResultUploadService.java`에서 `ALLOWED_STATUSES` 상수와 그 javadoc을 **삭제**하고, `TEMPLATE_SPEC`을 아래로 교체한다:

```java
    /** 결과 열 인덱스(0-based). 드롭다운 대상. */
    private static final int RESULT_COLUMN = 4;

    private static final ExcelExportSpec<StageResultUploadTemplateRow> TEMPLATE_SPEC = new ExcelExportSpec<>(
            "stage-result-upload",
            List.of(
                    new ExportColumn<>(StageResultUploadParser.HEADERS.get(0), StageResultUploadTemplateRow::stageResultId, true),
                    new ExportColumn<>(StageResultUploadParser.HEADERS.get(1), StageResultUploadTemplateRow::applicationId, true),
                    new ExportColumn<>(StageResultUploadParser.HEADERS.get(2), StageResultUploadTemplateRow::applicantName, true),
                    new ExportColumn<>(StageResultUploadParser.HEADERS.get(3), StageResultUploadTemplateRow::stageResultUpdatedAt, true),
                    new ExportColumn<>(StageResultUploadParser.HEADERS.get(4), StageResultUploadTemplateRow::resultStatus),
                    new ExportColumn<>(StageResultUploadParser.HEADERS.get(5), StageResultUploadTemplateRow::score),
                    new ExportColumn<>(StageResultUploadParser.HEADERS.get(6), StageResultUploadTemplateRow::comment)),
            StageResultUploadService::decorateTemplateSheet);
```

`generateTemplate`의 row 매핑에서 결과 값을 라벨로 바꾼다:

```java
                        StageResultStatusLabels.label(result.getResultStatus()),
```

(기존 `result.getResultStatus() == null ? "" : result.getResultStatus().name()` 줄을 대체.)

클래스 하단(`formatToken` 근처)에 데코레이터 추가:

```java
    /** 헤더 틀고정 + 결과 열 드롭다운(합격/불합격/보류/결시/철회). 데이터가 0행이어도 2행에는 걸어 둔다. */
    private static void decorateTemplateSheet(Sheet sheet, int dataRowCount) {
        sheet.createFreezePane(0, 1);
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(
                StageResultStatusLabels.uploadChoices().toArray(String[]::new));
        CellRangeAddressList range = new CellRangeAddressList(
                1, Math.max(1, dataRowCount), RESULT_COLUMN, RESULT_COLUMN);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("결과", "합격 / 불합격 / 보류 / 결시 / 철회 중 하나를 선택하세요.");
        sheet.addValidationData(validation);
    }
```

import 추가:

```java
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
```

- [ ] **Step 5: 서비스 — 라벨 파싱 + 대기 규칙 + 한글 오류 문구**

`validate(...)` 메서드를 아래로 교체한다(시그니처 동일, `forCommit` 이후 STALE 판정·CHANGED 반환 로직은 기존과 같다):

```java
    private ValidatedUploadRow validate(
            StageResultUploadRowRequest row,
            Map<Long, StageResult> resultMap,
            Set<Long> duplicateIds,
            boolean forCommit
    ) {
        List<String> errors = new ArrayList<>();

        if (row.formulaCellPresent()) {
            errors.add("수식(formula) 셀은 허용되지 않습니다.");
        }
        if (row.tokenCellNotString()) {
            errors.add("수정토큰은 문자열 셀이어야 합니다.");
        }

        Long stageResultId = parseLong(row.stageResultId());
        if (stageResultId == null) {
            errors.add("시스템ID는 필수이며 숫자여야 합니다.");
        }
        Long applicationId = parseLong(row.applicationId());
        if (applicationId == null) {
            errors.add("수험번호는 필수이며 숫자여야 합니다.");
        }

        StageResultStatus newStatus = parseResultStatus(blankToNull(row.resultStatus()), errors);
        BigDecimal newScore = parseScore(blankToNull(row.score()), errors);
        String newComment = blankToNull(row.comment());
        if (newComment != null && newComment.length() > COMMENT_MAX_LENGTH) {
            errors.add("코멘트는 2000자 이하여야 합니다.");
        }

        if (stageResultId != null && duplicateIds.contains(stageResultId)) {
            errors.add("시스템ID가 파일 내에서 중복되었습니다.");
        }

        StageResult current = stageResultId == null ? null : resultMap.get(stageResultId);
        if (stageResultId != null && !duplicateIds.contains(stageResultId)) {
            if (current == null) {
                errors.add("이 단계의 대상자가 아니거나 존재하지 않습니다.");
            } else if (applicationId != null
                    && !current.getJobApplication().getId().equals(applicationId)) {
                errors.add("수험번호가 시스템ID와 일치하지 않습니다.");
            }
        }

        // 대기(PENDING) 규칙: 현재도 대기면 "손대지 않은 행"으로 보고 미변경 처리한다(부분 판정 업로드 허용).
        // 판정된 행을 대기로 되돌리거나, 대기인 채로 점수·코멘트만 넣는 것은 bulk가 거부하므로 여기서 막는다.
        if (current != null && newStatus == StageResultStatus.PENDING) {
            if (current.getResultStatus() != StageResultStatus.PENDING) {
                errors.add("판정된 결과를 대기로 되돌릴 수 없습니다.");
            } else if (!scoreEquals(current.getScore(), newScore)
                    || !Objects.equals(blankToNull(current.getComment()), newComment)) {
                errors.add("대기 상태에서는 점수·코멘트를 입력할 수 없습니다. 결과를 먼저 판정하세요.");
            }
        }

        if (!errors.isEmpty()) {
            return new ValidatedUploadRow(
                    StageResultUploadRowResult.error(
                            row.rowNumber(), stageResultId, applicationId, row.applicantName(), errors),
                    null, null, null);
        }

        boolean changed = current.getResultStatus() != newStatus
                || !scoreEquals(current.getScore(), newScore)
                || !Objects.equals(blankToNull(current.getComment()), newComment);

        if (!changed) {
            return new ValidatedUploadRow(
                    StageResultUploadRowResult.unchanged(
                            row.rowNumber(), stageResultId, applicationId, row.applicantName()),
                    newStatus, newScore, newComment);
        }

        StageResultUploadDiff diff = StageResultUploadDiff.of(current, newStatus, newScore, newComment);

        if (forCommit) {
            String currentToken = formatToken(current.getUpdatedAt());
            String rowToken = row.stageResultUpdatedAt() == null ? "" : row.stageResultUpdatedAt().trim();
            if (!currentToken.equals(rowToken)) {
                return new ValidatedUploadRow(
                        StageResultUploadRowResult.stale(
                                row.rowNumber(), stageResultId, applicationId, row.applicantName(),
                                currentToken, diff),
                        newStatus, newScore, newComment);
            }
        }

        return new ValidatedUploadRow(
                StageResultUploadRowResult.changed(
                        row.rowNumber(), stageResultId, applicationId, row.applicantName(), diff),
                newStatus, newScore, newComment);
    }

    private StageResultStatus parseResultStatus(String raw, List<String> errors) {
        if (raw == null) {
            errors.add("결과는 필수입니다.");
            return null;
        }
        Optional<StageResultStatus> parsed = StageResultStatusLabels.parse(raw);
        if (parsed.isEmpty()) {
            errors.add("허용되지 않는 결과입니다: " + raw + " (합격/불합격/보류/결시/철회)");
            return null;
        }
        return parsed.get();
    }

    private BigDecimal parseScore(String raw, List<String> errors) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add("점수 형식이 올바르지 않습니다: " + raw);
            return null;
        }
    }
```

import 추가: `import java.util.Optional;`

클래스 javadoc의 "편집 컬럼은 {@code resultStatus}/…" 문장 뒤에 한 줄 보강:

```java
 * 결과 값은 한글 라벨(합격/불합격/보류/결시/철회) 또는 enum 이름을 받는다. 파일 값이 대기이고 DB도 PENDING이면
 * 미변경으로 분류해 부분 판정 업로드를 허용한다({@link StageResultStatusLabels}).
```

- [ ] **Step 6: 통과 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.StageResultUploadControllerTest" --tests "com.shinyoung.recruit.service.StageResultUploadParserTest" --no-daemon
```

Expected: PASS. 특히 기존 `preview_flags_blank_result_status_and_pending_as_error`(현재 PASSED 행에 PENDING → 여전히 오류)와 `commit_rejects_all_when_any_row_invalid`가 그대로 통과해야 한다.

---

### Task 4: `AdminStageResultResponse` 그리드 열 + `AdminStageResultEnricher`

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/dto/response/AdminStageResultResponse.java`
- Create: `src/main/java/com/shinyoung/recruit/service/AdminStageResultEnricher.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/StageResultService.java` (`getResults`, `updateResult`)
- Modify: `src/main/java/com/shinyoung/recruit/service/StageResultCorrectionService.java` (`correctResult` 반환)
- Test: `src/test/java/com/shinyoung/recruit/service/StageResultServiceTest.java`

추가 필드(응답 끝에 덧붙임 — 기존 JSON 소비자 하위호환): `decidedBy`, `workLocation`, `applicationType`, `finalEducationLevel`, `finalSchoolName`, `previousStageResultStatus`.

- [ ] **Step 1: 실패하는 테스트 작성**

`StageResultServiceTest.java`에 필드·import·테스트·헬퍼 추가.

필드(기존 `@Autowired` 목록 아래):

```java
    @Autowired
    private ApplicationEducationRepository educationRepository;
```

import:

```java
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import java.time.LocalDate;
```

테스트(기존 `initialize_*` 테스트들 뒤):

```java
    @Test
    void get_results_includes_grid_fields_and_previous_stage_result() {
        Long jobPostingId = createJobPosting();
        Long applicationId = createSubmittedApplication("stage-result-grid", jobPostingId);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        educationRepository.save(education(application, EducationLevel.HIGH_SCHOOL, "First High School", 0));
        educationRepository.save(education(application, EducationLevel.UNIVERSITY, "Second University", 1));
        Long documentStageId = createStage(jobPostingId, 0, false);
        Long interviewStageId = createStage(jobPostingId, 1, true);
        Long documentResultId = initializeAndFirstResultId(documentStageId);
        stageService.start(jobPostingId, documentStageId);
        stageResultService.updateResult(documentStageId, documentResultId,
                new StageResultUpdateRequest(StageResultStatus.PASSED, new BigDecimal("80"), null), ACTOR);
        stageResultService.initialize(interviewStageId);

        AdminStageResultResponse document = stageResultService.getResults(documentStageId).get(0);
        AdminStageResultResponse interview = stageResultService.getResults(interviewStageId).get(0);

        assertThat(document.decidedBy()).isEqualTo(ACTOR);
        assertThat(document.workLocation()).isEqualTo(application.getWorkLocationNameSnapshot());
        assertThat(document.applicationType()).isEqualTo(application.getJobPosition().getApplicationType());
        assertThat(document.finalEducationLevel()).isEqualTo(EducationLevel.UNIVERSITY);
        assertThat(document.finalSchoolName()).isEqualTo("Second University");
        assertThat(document.previousStageResultStatus()).isNull(); // 첫 단계
        assertThat(interview.previousStageResultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(interview.decidedBy()).isNull(); // 초기화 직후 미판정
    }

    @Test
    void update_result_response_carries_grid_fields() {
        Long jobPostingId = createJobPosting();
        createSubmittedApplication("stage-result-grid-update", jobPostingId);
        Long stageId = createStage(jobPostingId);
        Long resultId = initializeAndFirstResultId(stageId);
        stageService.start(jobPostingId, stageId);

        AdminStageResultResponse response = stageResultService.updateResult(stageId, resultId,
                new StageResultUpdateRequest(StageResultStatus.HOLD, null, "검토"), ACTOR);

        assertThat(response.decidedBy()).isEqualTo(ACTOR);
        assertThat(response.previousStageResultStatus()).isNull();
        assertThat(response.finalEducationLevel()).isNull(); // 학력 미입력
    }
```

헬퍼(파일 하단 `createApplicant` 위):

```java
    private ApplicationEducation education(
            JobApplication application,
            EducationLevel educationLevel,
            String schoolName,
            Integer sortOrder
    ) {
        return ApplicationEducation.create(
                application,
                educationLevel,
                schoolName,
                educationLevel == EducationLevel.HIGH_SCHOOL ? null : "Computer Science",
                null,
                null,
                null,
                LocalDate.of(2021, 3, 1),
                LocalDate.of(2025, 2, 28),
                GraduationStatus.GRADUATED,
                DayNightType.DAY,
                CampusType.MAIN,
                false,
                "KR",
                sortOrder
        );
    }
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.StageResultServiceTest" --no-daemon
```

Expected: FAIL — `decidedBy()` 등 접근자 없음(컴파일 오류).

- [ ] **Step 3: 응답 DTO 확장**

`src/main/java/com/shinyoung/recruit/dto/response/AdminStageResultResponse.java` 전체:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 전형 결과 행. 뒤쪽 6개 필드는 전형결과 관리 화면 그리드 열(2026-09-04)로, 학력·직전 단계 결과는
 * {@code AdminStageResultEnricher}가 배치 조회해 채운다. 지원자용 응답과 공유하지 않는다.
 *
 * @param decidedBy                 판정자(관리자 로그인 id). 미판정이면 null
 * @param workLocation              지원자가 선택한 근무지 표시명. 없으면 null
 * @param applicationType           모집분야 지원구분
 * @param finalEducationLevel       최고 학력 행의 학력(지원현황 조회와 같은 판정). 학력 없으면 null
 * @param finalSchoolName           최고 학력 행의 학교명
 * @param previousStageResultStatus 같은 공고에서 stageOrder가 바로 앞인 단계의 결과. 첫 단계·결과 없음이면 null
 */
public record AdminStageResultResponse(
        Long stageResultId,
        Long stageId,
        Long applicationId,
        String applicantName,
        Long jobPositionId,
        String jobPositionName,
        JobApplicationStatus applicationStatus,
        StageResultStatus resultStatus,
        BigDecimal score,
        String comment,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        String decidedBy,
        String workLocation,
        JobPositionApplicationType applicationType,
        EducationLevel finalEducationLevel,
        String finalSchoolName,
        StageResultStatus previousStageResultStatus
) {

    /** 배치 조회로 채우는 파생값. 없으면 {@link #empty()}. */
    public record Enrichment(
            EducationLevel finalEducationLevel,
            String finalSchoolName,
            StageResultStatus previousStageResultStatus
    ) {
        public static Enrichment empty() {
            return new Enrichment(null, null, null);
        }
    }

    public static AdminStageResultResponse from(StageResult result, Enrichment enrichment) {
        JobApplication application = result.getJobApplication();
        return new AdminStageResultResponse(
                result.getId(),
                result.getStage().getId(),
                application.getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                application.getStatus(),
                result.getResultStatus(),
                result.getScore(),
                result.getComment(),
                application.getSubmittedAt(),
                result.getDecidedAt(),
                result.getDecidedBy(),
                application.getWorkLocationNameSnapshot(),
                application.getJobPosition().getApplicationType(),
                enrichment.finalEducationLevel(),
                enrichment.finalSchoolName(),
                enrichment.previousStageResultStatus()
        );
    }
}
```

(기존 `from(StageResult)`는 제거한다. 호출부 3곳은 Step 5에서 교체.)

- [ ] **Step 4: `AdminStageResultEnricher` 생성**

`src/main/java/com/shinyoung/recruit/service/AdminStageResultEnricher.java`:

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 전형 결과 응답의 파생 필드(최종학력·직전 단계 결과)를 **단계 단위 배치 2회**로 채운다(N+1 없음).
 *
 * <p>한 번에 넘기는 결과들은 모두 같은 단계여야 한다(직전 단계를 첫 행의 단계로 판정한다). 호출부는
 * 단계 결과 목록·단건 판정·정정 응답이라 이 전제를 만족한다.
 *
 * <p>최종학력 판정(최고 EducationLevel, 동률이면 id 큰 행)은 {@code JobApplicationService.loadAdminSummaryEnrichments}와
 * 같은 규칙이다 — 지원현황 조회와 그리드가 다른 학교를 보여주면 안 된다.
 */
@Component
@RequiredArgsConstructor
public class AdminStageResultEnricher {

    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final ApplicationEducationRepository applicationEducationRepository;

    public AdminStageResultResponse toResponse(StageResult result) {
        return toResponses(List.of(result)).get(0);
    }

    public List<AdminStageResultResponse> toResponses(List<StageResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = results.stream()
                .map(result -> result.getJobApplication().getId())
                .distinct()
                .toList();
        Map<Long, ApplicationEducation> finalEducations = loadFinalEducations(applicationIds);
        Map<Long, StageResultStatus> previousStatuses = loadPreviousStageStatuses(results.get(0).getStage(), applicationIds);

        return results.stream()
                .map(result -> {
                    Long applicationId = result.getJobApplication().getId();
                    ApplicationEducation education = finalEducations.get(applicationId);
                    return AdminStageResultResponse.from(result, new AdminStageResultResponse.Enrichment(
                            education == null ? null : education.getEducationLevel(),
                            education == null ? null : education.getSchoolName(),
                            previousStatuses.get(applicationId)));
                })
                .toList();
    }

    private Map<Long, ApplicationEducation> loadFinalEducations(List<Long> applicationIds) {
        Comparator<ApplicationEducation> finalEducationComparator = Comparator
                .comparingInt((ApplicationEducation education) -> education.getEducationLevel().ordinal())
                .thenComparing(ApplicationEducation::getId);
        return applicationEducationRepository.findByJobApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(
                        education -> education.getJobApplication().getId(),
                        education -> education,
                        (left, right) -> finalEducationComparator.compare(left, right) >= 0 ? left : right
                ));
    }

    /** 같은 공고의 단계를 stageOrder 순으로 훑어 현재 단계 바로 앞 단계를 찾고, 그 단계의 결과를 배치 조회한다. */
    private Map<Long, StageResultStatus> loadPreviousStageStatuses(Stage stage, List<Long> applicationIds) {
        List<Stage> stages = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(stage.getJobPosting().getId());
        Stage previous = null;
        for (Stage candidate : stages) {
            if (candidate.getId().equals(stage.getId())) {
                break;
            }
            previous = candidate;
        }
        if (previous == null) {
            return Map.of();
        }
        return stageResultRepository.findByStageIdAndJobApplicationIdIn(previous.getId(), applicationIds).stream()
                .collect(Collectors.toMap(
                        result -> result.getJobApplication().getId(),
                        StageResult::getResultStatus
                ));
    }
}
```

- [ ] **Step 5: 서비스 두 곳에서 enricher 사용**

`StageResultService.java`:

필드 추가:

```java
    private final AdminStageResultEnricher enricher;
```

`getResults` 교체:

```java
    public List<AdminStageResultResponse> getResults(Long stageId) {
        findStage(stageId);
        return enricher.toResponses(stageResultRepository.findByStageIdForAdminList(stageId));
    }
```

`updateResult` 마지막 줄 교체:

```java
        return enricher.toResponse(stageResult);
```

`StageResultCorrectionService.java`:

필드 추가:

```java
    private final AdminStageResultEnricher enricher;
```

`correctResult` 마지막 줄 교체:

```java
        return enricher.toResponse(stageResult);
```

(두 서비스 모두 `@RequiredArgsConstructor`라 생성자 수정 없음. 테스트에서 `new StageResultService(...)`로 직접 생성하는 곳은 없다 — 모두 `@Autowired`.)

- [ ] **Step 6: 통과 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.StageResultServiceTest" --tests "com.shinyoung.recruit.service.StageResultCorrectionServiceTest" --tests "com.shinyoung.recruit.controller.StageResultControllerTest" --no-daemon
```

Expected: PASS.

---

### Task 5: 진행 중 단계는 발표일시만 수정 허용

**Files:**
- Modify: `src/main/java/com/shinyoung/recruit/domain/entity/Stage.java`
- Modify: `src/main/java/com/shinyoung/recruit/service/StageService.java` (`update`)
- Test: `src/test/java/com/shinyoung/recruit/service/StageServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`StageServiceTest.java`의 `update_stage_fails_when_stage_status_is_not_ready` 바로 뒤에 추가:

```java
    @Test
    void update_in_progress_stage_changes_announcement_datetime_only() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.IN_PROGRESS);
        LocalDateTime postponed = LocalDateTime.of(2026, 8, 1, 10, 0);

        stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 0, postponed, false));

        Stage updated = stageRepository.findById(stageId).orElseThrow();
        assertThat(updated.getResultAnnouncementDateTime()).isEqualTo(postponed);
        assertThat(updated.getStageName()).isEqualTo("Document screening");
        assertThat(updated.getStatus()).isEqualTo(StageStatus.IN_PROGRESS);
    }

    @Test
    void update_in_progress_stage_fails_when_locked_field_changes() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.IN_PROGRESS);

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Renamed", StageType.DOCUMENT, 0, LocalDateTime.of(2026, 8, 1, 10, 0), false)))
                .isInstanceOf(InvalidStageException.class)
                .hasMessageContaining("resultAnnouncementDateTime");
        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 5, LocalDateTime.of(2026, 8, 1, 10, 0), false)))
                .isInstanceOf(InvalidStageException.class);
        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 0, LocalDateTime.of(2026, 8, 1, 10, 0), true)))
                .isInstanceOf(InvalidStageException.class);
    }

    @Test
    void update_announced_stage_fails_even_for_announcement_datetime() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, createStageRequest(0, false));
        Stage stage = stageRepository.findById(stageId).orElseThrow();
        ReflectionTestUtils.setField(stage, "status", StageStatus.RESULT_ANNOUNCED);

        assertThatThrownBy(() -> stageService.update(jobPostingId, stageId, new StageUpdateRequest(
                "Document screening", StageType.DOCUMENT, 0, LocalDateTime.of(2026, 8, 1, 10, 0), false)))
                .isInstanceOf(InvalidStageException.class);
    }
```

(`Stage`, `StageStatus`, `StageType`, `StageUpdateRequest`, `LocalDateTime`, `ReflectionTestUtils`, `InvalidStageException`은 이 테스트 파일에 이미 import되어 있다.)

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.StageServiceTest" --no-daemon
```

Expected: `update_in_progress_stage_changes_announcement_datetime_only` FAIL(`InvalidStageException: Only READY stage can be changed.`), `update_in_progress_stage_fails_when_locked_field_changes` FAIL(메시지에 `resultAnnouncementDateTime` 없음). 나머지 PASS.

- [ ] **Step 3: 엔티티에 발표일시 단독 변경 메서드**

`Stage.java`의 `reorder(...)` 위에 추가:

```java
    /** 진행 중 단계에서 허용되는 유일한 변경(발표일 연기 등). 이름·유형·순서·최종단계는 건드리지 않는다. */
    public void updateResultAnnouncementDateTime(LocalDateTime resultAnnouncementDateTime) {
        this.resultAnnouncementDateTime = resultAnnouncementDateTime;
    }
```

- [ ] **Step 4: 서비스 `update` 분기**

`StageService.java`의 `update` 메서드를 아래로 교체:

```java
    @Transactional
    public Long update(Long jobPostingId, Long stageId, StageUpdateRequest request) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingEditable(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageRequest(
                request.stageName(),
                request.stageType(),
                request.stageOrder()
        );

        if (stage.getStatus() == StageStatus.IN_PROGRESS) {
            // 진행 중 단계는 발표일시만 조정할 수 있다(발표일 연기 등 운영 필요). 나머지 필드는 현재 값과 같아야 한다.
            validateOnlyAnnouncementDateTimeChanged(stage, request);
            stage.updateResultAnnouncementDateTime(request.resultAnnouncementDateTime());
            return stage.getId();
        }

        validateStageEditable(stage);
        validateStageOrderForUpdate(jobPostingId, request.stageOrder(), stageId);
        validateFinalStageForUpdate(jobPostingId, request.finalStage(), stageId);

        stage.update(
                request.stageName(),
                request.stageType(),
                request.stageOrder(),
                request.resultAnnouncementDateTime(),
                request.finalStage()
        );
        return stage.getId();
    }
```

`validateStageEditable` 바로 아래에 추가:

```java
    private void validateOnlyAnnouncementDateTimeChanged(Stage stage, StageUpdateRequest request) {
        boolean lockedFieldChanged = !stage.getStageName().equals(request.stageName())
                || stage.getStageType() != request.stageType()
                || !stage.getStageOrder().equals(request.stageOrder())
                || stage.isFinalStage() != request.finalStage();
        if (lockedFieldChanged) {
            throw new InvalidStageException(
                    "In progress stage allows changing resultAnnouncementDateTime only.");
        }
    }
```

- [ ] **Step 5: 통과 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.StageServiceTest" --tests "com.shinyoung.recruit.controller.StageControllerTest" --no-daemon
```

Expected: PASS. 기존 `update_stage_fails_when_stage_status_is_not_ready`(IN_PROGRESS + 이름 변경)는 새 분기에서도 예외라 그대로 통과.

---

### Task 6: 전체 대상 회귀 실행

- [ ] **Step 1: Stage·엑셀 관련 테스트 일괄 실행**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.Stage*" --tests "com.shinyoung.recruit.controller.Stage*" --tests "com.shinyoung.recruit.controller.ApplicationStageResultControllerTest" --tests "com.shinyoung.recruit.service.ApplicationStageResultServiceTest" --tests "com.shinyoung.recruit.service.ExcelExport*" --tests "com.shinyoung.recruit.service.ApplicationExportServiceTest" --tests "com.shinyoung.recruit.service.AdminApplicationSectionServiceTest" --no-daemon
```

Expected: 전부 PASS. 실패 시 원인은 대개 (a) 응답 record 위치 인자 순서, (b) 테스트 파일 import 누락, (c) 한글 문구를 `hasMessageContaining`으로 검사하던 기존 단언 — 각각 이 계획의 코드와 대조해 고친다.

- [ ] **Step 2: 컴파일 경고·미사용 import 정리**

`StageResultUploadService.java`에서 `ALLOWED_STATUSES` 삭제로 불필요해진 `java.util.Set` import가 다른 곳(`duplicateStageResultIds`)에서 여전히 쓰이는지 확인한다 — 쓰이므로 유지. `StageResultStatus` import는 계속 필요.

---

### Task 7: `api-contract.md` 🟡 기재

**Files:**
- Modify: `recruit/api-contract.md` (파일 끝에 추가)

- [ ] **Step 1: 섹션 추가**

```markdown
---

### 화면: 전형결과 관리 (AdminStageResultView)  🟡 초안 (2026-09-04)

- 프론트: `src/views/admin/stageResult/AdminStageResultView.vue` 외(S2~S4 예정) + `src/api/admin/adminStageApi.ts`
- 백엔드: `StageController`, `StageResultController`, `StageResultUploadController`, `AdminExportController`
- 설계: `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md`
- 그대로 사용(형태 변경 없음): `GET /admin/job-postings/{id}/stages`, `POST …/stages`, `POST …/stages/{stageId}`, `POST …/stages/{stageId}/delete`, `POST …/stages/reorder`, `POST …/stages/{stageId}/start|announce|close`, `GET /admin/stages/{stageId}/results`, `POST …/results/initialize`, `POST …/results/{resultId}`, `POST …/results/bulk`, `POST …/results/{resultId}/correct`, `GET …/results/{resultId}/histories`, `GET …/results/upload-template`, `POST …/results/upload/preview`, `POST …/results/upload/commit`, `GET …/results/export`

#### 변경 1: `AdminStageResultResponse` 그리드 열 추가  🟡 (S1)

- 적용: `GET /admin/stages/{stageId}/results`, `initialize`·`bulk`·단건·`correct` 응답(같은 DTO). 기존 필드 유지, 끝에 추가
- 추가 필드: `decidedBy`(판정자), `workLocation`(근무지 표시명), `applicationType`(`JobPositionApplicationType`), `finalEducationLevel`(`EducationLevel`), `finalSchoolName`, `previousStageResultStatus`(직전 stageOrder 단계의 `StageResultStatus`, 첫 단계면 null)
- 최종학력 판정은 지원현황 조회와 동일(최고 레벨, 동률이면 id 큰 행)

#### 변경 2: 진행 중 단계 발표일시 수정 허용  🟡 (S1)

- `POST /admin/job-postings/{id}/stages/{stageId}` 요청 형태 불변(`stageName, stageType, stageOrder, resultAnnouncementDateTime, finalStage`)
- READY: 전체 수정(기존). **IN_PROGRESS: `resultAnnouncementDateTime`만 변경 가능**, 다른 필드는 현재 값과 같아야 함(다르면 400). RESULT_ANNOUNCED·CLOSED: 400(기존)
- 프론트는 잠긴 필드에 현재 값을 그대로 채워 보낸다

#### 변경 3: 엑셀 업로드 템플릿 한글화  🟡 (S1)

- `GET …/results/upload-template` 헤더(순서 고정): `시스템ID(수정금지) | 수험번호(수정금지) | 이름(수정금지) | 수정토큰(수정금지) | 결과 | 점수 | 코멘트`. 앞 4열 회색 음영, 헤더 행 틀고정, 결과 열 드롭다운(합격/불합격/보류/결시/철회)
- 결과 값 prefill은 한글 라벨(대기/합격/불합격/보류/결시/철회). 업로드 파싱은 한글 라벨·enum명 모두 허용, 빈칸은 오류
- **부분 판정 업로드**: 파일 `대기` + DB PENDING → 미변경(`UNCHANGED`)으로 적용 제외. 판정된 행을 `대기`로 되돌리기, `대기`인 채 점수·코멘트 입력 → 행 오류
- 행 오류 문구 한글화(`결과는 필수입니다`, `허용되지 않는 결과입니다: …`, `판정된 결과를 대기로 되돌릴 수 없습니다`, `대기 상태에서는 점수·코멘트를 입력할 수 없습니다. 결과를 먼저 판정하세요` 등). 응답 형태(`StageResultUploadPreviewResponse`/`CommitResponse`)는 불변
- 이전 영문 헤더 파일은 헤더 불일치로 거부(템플릿 재다운로드)
```

- [ ] **Step 2: 확인**

`api-contract.md` 끝에 섹션이 있고, 상태 표기가 🟡인지 눈으로 확인한다. S4 완료 시 🟢로 갱신한다.

---

## 자체 검토 결과

- 설계서 §5.1 → Task 4, §5.2 → Task 5, §5.3(헤더·라벨·드롭다운·음영·부분 판정) → Task 1~3, §5 계약 → Task 7. §5.4는 변경 없음.
- Task 간 타입 일치: `ExportColumn(header, extractor, readOnly)` · `ExcelExportSpec(sheetName, columns, decorator)` · `ExcelSheetDecorator.decorate(Sheet, int)` · `StageResultStatusLabels.label/parse/uploadChoices` · `AdminStageResultResponse.from(StageResult, Enrichment)` · `AdminStageResultEnricher.toResponse/toResponses` · `Stage.updateResultAnnouncementDateTime` — 각 정의 Task와 사용 Task에서 동일.
- 순서 의존: Task 3은 Task 1·2에 의존. Task 4·5는 독립. Task 6은 전부 이후. Task 7은 언제든.
