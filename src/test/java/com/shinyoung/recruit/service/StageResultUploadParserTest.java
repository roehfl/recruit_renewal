package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.UploadProperties;
import com.shinyoung.recruit.dto.request.StageResultUploadRowRequest;
import com.shinyoung.recruit.exception.InvalidStageResultUploadException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 07f 하드닝: upload 파일 레벨 방어를 parser 단위로 회귀 고정한다(행수/파일크기/확장자/header).
 * Spring 컨텍스트 없이 빠르게 경계값을 검증한다.
 */
class StageResultUploadParserTest {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Test
    void rejects_when_data_rows_exceed_max_upload_rows() throws Exception {
        StageResultUploadParser parser = parser(2, DataSize.ofMegabytes(5));
        byte[] xlsx = xlsx(StageResultUploadParser.HEADERS, 3); // 3 data rows > max 2

        assertThatThrownBy(() -> parser.parse(file("upload.xlsx", xlsx)))
                .isInstanceOf(InvalidStageResultUploadException.class)
                .hasMessageContaining("최대 행 수");
    }

    @Test
    void rejects_when_file_exceeds_max_upload_file_size() {
        StageResultUploadParser parser = parser(10_000, DataSize.ofBytes(10));
        byte[] payload = new byte[64]; // 64 bytes > max 10 bytes; 크기 검사가 파싱보다 먼저 동작

        assertThatThrownBy(() -> parser.parse(file("upload.xlsx", payload)))
                .isInstanceOf(InvalidStageResultUploadException.class)
                .hasMessageContaining("크기");
    }

    @Test
    void rejects_non_xlsx_extension() throws Exception {
        StageResultUploadParser parser = parser(10_000, DataSize.ofMegabytes(5));
        byte[] xlsx = xlsx(StageResultUploadParser.HEADERS, 1);

        assertThatThrownBy(() -> parser.parse(file("upload.xls", xlsx)))
                .isInstanceOf(InvalidStageResultUploadException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void rejects_wrong_header_signature() throws Exception {
        StageResultUploadParser parser = parser(10_000, DataSize.ofMegabytes(5));
        List<String> wrongHeader = new ArrayList<>(StageResultUploadParser.HEADERS);
        wrongHeader.set(0, "wrong");
        byte[] xlsx = xlsx(wrongHeader, 1);

        assertThatThrownBy(() -> parser.parse(file("upload.xlsx", xlsx)))
                .isInstanceOf(InvalidStageResultUploadException.class)
                .hasMessageContaining("헤더");
    }

    @Test
    void parses_valid_rows_within_limits() throws Exception {
        StageResultUploadParser parser = parser(10_000, DataSize.ofMegabytes(5));
        byte[] xlsx = xlsx(StageResultUploadParser.HEADERS, 2);

        List<StageResultUploadRowRequest> rows = parser.parse(file("upload.xlsx", xlsx));
        assertThat(rows).hasSize(2);
    }

    private StageResultUploadParser parser(long maxRows, DataSize maxFileSize) {
        UploadProperties properties = new UploadProperties();
        properties.setMaxRows(maxRows);
        properties.setMaxFileSize(maxFileSize);
        return new StageResultUploadParser(properties);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, XLSX_CONTENT_TYPE, bytes);
    }

    private byte[] xlsx(List<String> header, int dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("stage-result-upload");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < header.size(); c++) {
                Cell cell = headerRow.createCell(c, CellType.STRING);
                cell.setCellValue(header.get(c));
            }
            for (int r = 1; r <= dataRows; r++) {
                Row row = sheet.createRow(r);
                // stageResultId/applicationId/.../resultStatus 등 어떤 값이든 파일 레벨 검증에는 무관.
                row.createCell(0, CellType.STRING).setCellValue(String.valueOf(r));
                row.createCell(1, CellType.STRING).setCellValue(String.valueOf(r));
                row.createCell(2, CellType.STRING).setCellValue("name" + r);
                row.createCell(3, CellType.STRING).setCellValue("2026-06-01T10:00:00");
                row.createCell(4, CellType.STRING).setCellValue("PASSED");
                row.createCell(5, CellType.STRING).setCellValue("");
                row.createCell(6, CellType.STRING).setCellValue("");
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
