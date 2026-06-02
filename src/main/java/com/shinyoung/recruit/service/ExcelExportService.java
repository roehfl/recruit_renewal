package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.ExportProperties;
import com.shinyoung.recruit.exception.ExportGenerationException;
import com.shinyoung.recruit.exception.ExportRowLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 이미 materialize된 row list를 받아 row cap 적용 + xlsx temp 파일 생성을 수행하는 공용 export 헬퍼.
 *
 * <p>stage/posting-scoped dataset(stage results / interviews / interview evaluations)은 기존 admin list
 * 쿼리를 재사용해 parity를 보장하므로, 호출자가 parity list를 만들어 넘기면 여기서 cap·writer를 처리한다.
 * row 수가 max를 넘으면 writer를 호출하지 않아 workbook이 생성되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final ExcelExportWriter excelExportWriter;
    private final ExportProperties exportProperties;

    public <T> ExcelExportFile generate(ExcelExportSpec<T> spec, List<T> rows, String fileName) {
        return generate(spec, rows, fileName, true);
    }

    /**
     * upload-template 같은 round-trip 소스는 {@code escapeFormulaPrefix=false}로 호출해 값을 변형하지 않는다.
     */
    public <T> ExcelExportFile generate(
            ExcelExportSpec<T> spec,
            List<T> rows,
            String fileName,
            boolean escapeFormulaPrefix
    ) {
        long maxRows = exportProperties.getMaxRows();
        if (rows.size() > maxRows) {
            throw new ExportRowLimitExceededException(rows.size(), maxRows);
        }
        try {
            Path tempFile = excelExportWriter.writeToTempFile(
                    spec, ExportRowSource.ofList(rows), escapeFormulaPrefix);
            return new ExcelExportFile(tempFile, fileName, rows.size());
        } catch (IOException | RuntimeException e) {
            throw new ExportGenerationException(fileName + " 생성 실패", e);
        }
    }
}
