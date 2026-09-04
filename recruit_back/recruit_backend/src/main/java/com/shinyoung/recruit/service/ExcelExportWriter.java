package com.shinyoung.recruit.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * SXSSF(streaming) 기반 공통 Excel export writer.
 *
 * <p>컬럼 정의({@link ExcelExportSpec})와 page 단위 row 소스({@link ExportRowSource})를 받아
 * xlsx temp 파일을 생성한다. SXSSF row access window로 workbook heap을, page fetch로 DB/JPA 메모리를
 * 함께 bound해 대량 export에서 OOM/connection 장시간 점유를 방지한다.
 * 모든 셀은 명시적 string cell로 작성하고, spreadsheet formula/hyperlink로 오인될 수 있는
 * 위험 prefix(`=`,`+`,`-`,`@`, tab, CR/LF)는 apostrophe로 escape한다(formula injection 방어).
 *
 * <p>JPA entity/lazy association이 아니라 projection DTO를 row로 받는다(streaming tx 경계 보호).
 *
 * <p>{@code escapeFormulaPrefix}로 formula injection 방어(apostrophe escape) 적용 여부를 정한다. 일반 export는
 * true(사람이 여는 파일/CSV 변환 대비). upload-template처럼 우리 parser가 다시 읽는 round-trip 소스는 false로 써서
 * 값을 변형하지 않는다(escape가 comment 등을 바꿔 no-op 업로드가 CHANGED로 오판/오염되는 것을 방지). round-trip은
 * 모든 셀이 명시적 string cell이고, 재업로드 시 parser가 formula 셀을 거부하므로 injection도 안전하다.
 */
@Component
public class ExcelExportWriter {

    private static final int ROW_ACCESS_WINDOW = 100;
    private static final int PAGE_SIZE = 1_000;
    private static final String TEMP_PREFIX = "recruit-export-";
    private static final String TEMP_SUFFIX = ".xlsx";

    public <T> Path writeToTempFile(ExcelExportSpec<T> spec, ExportRowSource<T> rowSource) throws IOException {
        return writeToTempFile(spec, rowSource, true);
    }

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

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * formula injection 방어: 위험 prefix로 시작하는 값에 apostrophe를 붙여 텍스트로 강제한다.
     */
    private String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }
}
