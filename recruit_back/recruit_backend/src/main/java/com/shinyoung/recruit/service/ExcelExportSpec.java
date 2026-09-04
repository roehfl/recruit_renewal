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
