package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.UploadProperties;
import com.shinyoung.recruit.dto.request.SchoolImportRowRequest;
import com.shinyoung.recruit.exception.InvalidSchoolException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * School import xlsx 파일 레벨 방어 + 행 파싱. 07d 의 upload parser 패턴을 재사용한다.
 *
 * <ul>
 *     <li>{@code .xlsx}만, 파일 크기/행수 한도({@link UploadProperties} 재사용)</li>
 *     <li>첫 sheet, header signature 정확 대조, 셀 문자열 판독(로케일 의존 제거)</li>
 *     <li>formula 셀은 flag → service 가 행 skip</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SchoolImportParser {

    public static final List<String> HEADERS = List.of(
            "schoolName", "schoolType", "schoolCategory", "educationMode", "region", "address", "countryCode");

    private static final int COLUMN_COUNT = 7;

    private final UploadProperties uploadProperties;

    public List<SchoolImportRowRequest> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidSchoolException("업로드 파일이 비어 있습니다.");
        }
        validateExtension(file);
        validateSize(file);

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new InvalidSchoolException("업로드 시트를 찾을 수 없습니다.");
            }
            validateHeader(sheet);

            List<SchoolImportRowRequest> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isEmptyRow(row)) {
                    continue;
                }
                if (rows.size() >= uploadProperties.getMaxRows()) {
                    throw new InvalidSchoolException(
                            "업로드 가능한 최대 행 수(" + uploadProperties.getMaxRows() + ")를 초과했습니다.");
                }
                rows.add(toRow(row));
            }
            return rows;
        } catch (IOException e) {
            throw new InvalidSchoolException("엑셀 파일을 읽을 수 없습니다.");
        } catch (InvalidSchoolException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidSchoolException("엑셀 파일을 읽을 수 없습니다.");
        }
    }

    private void validateExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new InvalidSchoolException("업로드 파일 이름이 없습니다.");
        }
        if (!name.toLowerCase().endsWith(".xlsx")) {
            throw new InvalidSchoolException("업로드는 .xlsx 형식만 허용합니다.");
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > uploadProperties.getMaxFileSize().toBytes()) {
            throw new InvalidSchoolException("업로드 파일 크기가 허용 한도를 초과했습니다.");
        }
    }

    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new InvalidSchoolException("업로드 템플릿 헤더가 없습니다.");
        }
        for (int c = 0; c < COLUMN_COUNT; c++) {
            Cell cell = header.getCell(c);
            String value = (cell != null && cell.getCellType() == CellType.STRING)
                    ? cell.getStringCellValue().trim()
                    : "";
            if (!HEADERS.get(c).equals(value)) {
                throw new InvalidSchoolException("업로드 템플릿 헤더가 올바르지 않습니다. 기대 컬럼: " + HEADERS);
            }
        }
    }

    private SchoolImportRowRequest toRow(Row row) {
        boolean formula = false;
        String[] values = new String[COLUMN_COUNT];
        for (int c = 0; c < COLUMN_COUNT; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) {
                values[c] = "";
                continue;
            }
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                formula = true;
                values[c] = "";
                continue;
            }
            values[c] = readCellString(cell, type);
        }
        return new SchoolImportRowRequest(
                row.getRowNum() + 1,
                values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                formula);
    }

    private String readCellString(Cell cell, CellType type) {
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> numericToString(cell);
            default -> "";
        };
    }

    private String numericToString(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toString();
        }
        double value = cell.getNumericCellValue();
        if (!Double.isInfinite(value) && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).toPlainString();
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c < COLUMN_COUNT; c++) {
            Cell cell = row.getCell(c);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                continue;
            }
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().trim().isEmpty()) {
                continue;
            }
            return false;
        }
        return true;
    }
}
