package com.shinyoung.recruit.service;

import java.util.List;

/**
 * 하나의 dataset에 대한 Excel export 정의: 시트 이름 + 컬럼 목록(header + value extractor).
 * dataset마다 "컬럼 정의 + row mapper"만 선언하면 되어 07b 이후 dataset 추가 비용을 낮춘다.
 *
 * @param <T> export row 타입(projection DTO)
 */
public record ExcelExportSpec<T>(String sheetName, List<ExportColumn<T>> columns) {

    public ExcelExportSpec {
        columns = List.copyOf(columns);
    }
}
