package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.UploadProperties;
import com.shinyoung.recruit.dto.request.InterviewScheduleUploadRowRequest;
import com.shinyoung.recruit.exception.InvalidInterviewScheduleUploadException;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 면접 스케줄 업로드 xlsx 파일 레벨 방어 + 행 파싱. 형식·허용값·조 일관성 검증은 {@link InterviewScheduleService}가 한다.
 *
 * <ul>
 *     <li>{@code .xlsx}만 허용, 업로드 공통 크기/행수 한도 적용</li>
 *     <li>첫 sheet만 처리, 헤더를 {@link #HEADERS}와 정확히 대조</li>
 *     <li>사용자가 엑셀에서 날짜·시각을 입력하면 셀이 날짜 형식 숫자로 바뀐다. 일자 열은 {@code yyyy-MM-dd},
 *     시각 열은 {@code HH:mm} 문자열로 정규화한다</li>
 *     <li>수식 셀은 flag로 표시 → service가 행 오류로 처리</li>
 * </ul>
 *
 * 빈 행은 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class InterviewScheduleUploadParser {

    /** 조회 표·템플릿·다운로드와 같은 헤더(순서 고정). */
    public static final List<String> HEADERS = List.of(
            "일자", "장소", "도착시간", "면접시간", "면접순서", "조", "면접관", "수험번호", "성명");

    private static final int COLUMN_COUNT = 9;
    private static final int DATE_COLUMN = 0;
    private static final int ARRIVAL_TIME_COLUMN = 2;
    private static final int INTERVIEW_TIME_COLUMN = 3;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final UploadProperties uploadProperties;

    public List<InterviewScheduleUploadRowRequest> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInterviewScheduleUploadException("업로드 파일이 비어 있습니다.");
        }
        validateExtension(file);
        validateSize(file);

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new InvalidInterviewScheduleUploadException("업로드 시트를 찾을 수 없습니다.");
            }
            validateHeader(sheet);

            List<InterviewScheduleUploadRowRequest> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isEmptyRow(row)) {
                    continue;
                }
                if (rows.size() >= uploadProperties.getMaxRows()) {
                    throw new InvalidInterviewScheduleUploadException(
                            "업로드 가능한 최대 행 수(" + uploadProperties.getMaxRows() + ")를 초과했습니다.");
                }
                rows.add(toRow(row));
            }
            return rows;
        } catch (IOException e) {
            throw new InvalidInterviewScheduleUploadException("엑셀 파일을 읽을 수 없습니다.");
        } catch (InvalidInterviewScheduleUploadException e) {
            throw e;
        } catch (RuntimeException e) {
            // 손상되었거나 .xlsx가 아닌 파일에서 POI가 던지는 예외 등.
            throw new InvalidInterviewScheduleUploadException("엑셀 파일을 읽을 수 없습니다.");
        }
    }

    private void validateExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new InvalidInterviewScheduleUploadException("업로드 파일 이름이 없습니다.");
        }
        if (!name.toLowerCase().endsWith(".xlsx")) {
            throw new InvalidInterviewScheduleUploadException("업로드는 .xlsx 형식만 허용합니다.");
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > uploadProperties.getMaxFileSize().toBytes()) {
            throw new InvalidInterviewScheduleUploadException("업로드 파일 크기가 허용 한도를 초과했습니다.");
        }
    }

    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new InvalidInterviewScheduleUploadException("엑셀 헤더가 없습니다. 엑셀 다운로드 파일을 사용하세요.");
        }
        for (int c = 0; c < COLUMN_COUNT; c++) {
            Cell cell = header.getCell(c);
            String value = (cell != null && cell.getCellType() == CellType.STRING)
                    ? cell.getStringCellValue().trim()
                    : "";
            if (!HEADERS.get(c).equals(value)) {
                throw new InvalidInterviewScheduleUploadException(
                        "엑셀 헤더가 올바르지 않습니다(" + String.join(" | ", HEADERS) + "). 엑셀 다운로드 파일을 사용하세요.");
            }
        }
    }

    private InterviewScheduleUploadRowRequest toRow(Row row) {
        boolean formula = false;
        String[] values = new String[COLUMN_COUNT];
        for (int c = 0; c < COLUMN_COUNT; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) {
                values[c] = "";
                continue;
            }
            if (cell.getCellType() == CellType.FORMULA) {
                formula = true;
                values[c] = "";
                continue;
            }
            values[c] = readCellString(cell, c);
        }
        return new InterviewScheduleUploadRowRequest(
                row.getRowNum() + 1,
                values[0], values[1], values[2], values[3], values[4],
                values[5], values[6], values[7], values[8],
                formula);
    }

    private String readCellString(Cell cell, int column) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> numericToString(cell, column);
            default -> "";
        };
    }

    private String numericToString(Cell cell, int column) {
        double value = cell.getNumericCellValue();
        if (column == DATE_COLUMN && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        if ((column == ARRIVAL_TIME_COLUMN || column == INTERVIEW_TIME_COLUMN) && DateUtil.isCellDateFormatted(cell)) {
            // 엑셀 시각 = 하루의 비율. 부동소수 오차(09:29:59.999)를 피하려고 분 단위로 반올림한다.
            long minutes = Math.round((value - Math.floor(value)) * MINUTES_PER_DAY) % MINUTES_PER_DAY;
            return LocalTime.of((int) (minutes / 60), (int) (minutes % 60)).toString();
        }
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
