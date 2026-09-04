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

    @Test
    void decorator_receives_zero_data_rows_when_source_is_empty() throws Exception {
        AtomicInteger decoratedRows = new AtomicInteger(-1);
        ExcelExportSpec<String> spec = new ExcelExportSpec<>(
                "sheet",
                List.of(new ExportColumn<>("value", value -> value)),
                (sheet, dataRowCount) -> decoratedRows.set(dataRowCount));

        Path file = writer.writeToTempFile(spec, ExportRowSource.ofList(List.of()), false);
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isZero(); // header만 존재
            assertThat(decoratedRows.get()).isZero();
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
