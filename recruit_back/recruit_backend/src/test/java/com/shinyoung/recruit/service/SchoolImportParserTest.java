package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.UploadProperties;
import com.shinyoung.recruit.dto.request.SchoolImportRowRequest;
import com.shinyoung.recruit.exception.InvalidSchoolException;
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
 * Phase 08c 하드닝: School import 파일 레벨 방어를 parser 단위로 회귀 고정한다(행수/파일크기/확장자/header).
 */
class SchoolImportParserTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Test
    void rejects_when_data_rows_exceed_max_upload_rows() throws Exception {
        SchoolImportParser parser = parser(2, DataSize.ofMegabytes(5));
        byte[] xlsx = xlsx(SchoolImportParser.HEADERS, 3);

        assertThatThrownBy(() -> parser.parse(file("schools.xlsx", xlsx)))
                .isInstanceOf(InvalidSchoolException.class)
                .hasMessageContaining("최대 행 수");
    }

    @Test
    void rejects_when_file_exceeds_max_upload_file_size() {
        SchoolImportParser parser = parser(10_000, DataSize.ofBytes(10));
        byte[] payload = new byte[64];

        assertThatThrownBy(() -> parser.parse(file("schools.xlsx", payload)))
                .isInstanceOf(InvalidSchoolException.class)
                .hasMessageContaining("크기");
    }

    @Test
    void rejects_non_xlsx_extension() throws Exception {
        SchoolImportParser parser = parser(10_000, DataSize.ofMegabytes(5));
        assertThatThrownBy(() -> parser.parse(file("schools.xls", xlsx(SchoolImportParser.HEADERS, 1))))
                .isInstanceOf(InvalidSchoolException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void rejects_wrong_header() throws Exception {
        SchoolImportParser parser = parser(10_000, DataSize.ofMegabytes(5));
        List<String> wrong = new ArrayList<>(SchoolImportParser.HEADERS);
        wrong.set(0, "wrong");
        assertThatThrownBy(() -> parser.parse(file("schools.xlsx", xlsx(wrong, 1))))
                .isInstanceOf(InvalidSchoolException.class)
                .hasMessageContaining("헤더");
    }

    @Test
    void parses_valid_rows_within_limits() throws Exception {
        SchoolImportParser parser = parser(10_000, DataSize.ofMegabytes(5));
        List<SchoolImportRowRequest> rows = parser.parse(file("schools.xlsx", xlsx(SchoolImportParser.HEADERS, 2)));
        assertThat(rows).hasSize(2);
    }

    private SchoolImportParser parser(long maxRows, DataSize maxFileSize) {
        UploadProperties properties = new UploadProperties();
        properties.setMaxRows(maxRows);
        properties.setMaxFileSize(maxFileSize);
        return new SchoolImportParser(properties);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, XLSX, bytes);
    }

    private byte[] xlsx(List<String> header, int dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("schools");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < header.size(); c++) {
                headerRow.createCell(c, CellType.STRING).setCellValue(header.get(c));
            }
            for (int r = 1; r <= dataRows; r++) {
                Row row = sheet.createRow(r);
                row.createCell(0, CellType.STRING).setCellValue("SC" + r);
                row.createCell(1, CellType.STRING).setCellValue("School " + r);
                for (int c = 2; c < 7; c++) {
                    row.createCell(c, CellType.STRING).setCellValue("");
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
