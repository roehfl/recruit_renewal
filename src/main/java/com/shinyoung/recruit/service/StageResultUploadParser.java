package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.UploadProperties;
import com.shinyoung.recruit.dto.request.StageResultUploadRowRequest;
import com.shinyoung.recruit.exception.InvalidStageResultUploadException;
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
 * upload-template xlsx 파일 레벨 방어 + 행 파싱. write path이므로 export보다 보수적으로 막는다.
 *
 * <ul>
 *     <li>{@code .xlsx}만 허용(.xls/.csv/.xlsm 거부), 파일 크기/행수 한도 적용</li>
 *     <li>첫 sheet만 처리, header signature를 upload-template과 정확히 대조</li>
 *     <li>모든 셀을 문자열로 읽어 로케일 의존 numeric/date parse 제거</li>
 *     <li>formula 셀/토큰 셀 타입은 flag로 표시 → service가 행 오류로 처리</li>
 * </ul>
 *
 * 빈 행은 skip한다(숨김/필터 행은 데이터로 취급). 형식/허용값/3중 교차검증은 service가 수행한다.
 */
@Component
@RequiredArgsConstructor
public class StageResultUploadParser {

    public static final List<String> HEADERS = List.of(
            "stageResultId", "applicationId", "applicantName",
            "stageResultUpdatedAt", "resultStatus", "score", "comment");

    private static final int COLUMN_COUNT = 7;
    private static final int TOKEN_COLUMN = 3;

    private final UploadProperties uploadProperties;

    public List<StageResultUploadRowRequest> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidStageResultUploadException("업로드 파일이 비어 있습니다.");
        }
        validateExtension(file);
        validateSize(file);

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new InvalidStageResultUploadException("업로드 시트를 찾을 수 없습니다.");
            }
            validateHeader(sheet);

            List<StageResultUploadRowRequest> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isEmptyRow(row)) {
                    continue;
                }
                if (rows.size() >= uploadProperties.getMaxRows()) {
                    throw new InvalidStageResultUploadException(
                            "업로드 가능한 최대 행 수(" + uploadProperties.getMaxRows() + ")를 초과했습니다.");
                }
                rows.add(toRow(row));
            }
            return rows;
        } catch (IOException e) {
            throw new InvalidStageResultUploadException("엑셀 파일을 읽을 수 없습니다.");
        } catch (InvalidStageResultUploadException e) {
            throw e;
        } catch (RuntimeException e) {
            // 손상되었거나 .xlsx가 아닌 파일에서 POI가 던지는 예외 등.
            throw new InvalidStageResultUploadException("엑셀 파일을 읽을 수 없습니다.");
        }
    }

    private void validateExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new InvalidStageResultUploadException("업로드 파일 이름이 없습니다.");
        }
        if (!name.toLowerCase().endsWith(".xlsx")) {
            throw new InvalidStageResultUploadException("업로드는 .xlsx 형식만 허용합니다.");
        }
    }

    private void validateSize(MultipartFile file) {
        long max = uploadProperties.getMaxFileSize().toBytes();
        if (file.getSize() > max) {
            throw new InvalidStageResultUploadException("업로드 파일 크기가 허용 한도를 초과했습니다.");
        }
    }

    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new InvalidStageResultUploadException("업로드 템플릿 헤더가 없습니다. upload-template을 사용하세요.");
        }
        for (int c = 0; c < COLUMN_COUNT; c++) {
            Cell cell = header.getCell(c);
            String value = (cell != null && cell.getCellType() == CellType.STRING)
                    ? cell.getStringCellValue().trim()
                    : "";
            if (!HEADERS.get(c).equals(value)) {
                throw new InvalidStageResultUploadException(
                        "업로드 템플릿 헤더가 올바르지 않습니다. upload-template을 사용하세요.");
            }
        }
    }

    private StageResultUploadRowRequest toRow(Row row) {
        boolean formula = false;
        boolean tokenNotString = false;
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
            if (c == TOKEN_COLUMN && type == CellType.NUMERIC) {
                tokenNotString = true;
            }
            values[c] = readCellString(cell, type);
        }
        return new StageResultUploadRowRequest(
                row.getRowNum() + 1,
                values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                formula, tokenNotString);
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
