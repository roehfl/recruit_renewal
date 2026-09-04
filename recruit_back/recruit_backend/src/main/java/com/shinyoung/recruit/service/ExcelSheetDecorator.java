package com.shinyoung.recruit.service;

import org.apache.poi.ss.usermodel.Sheet;

/**
 * 모든 행을 기록한 뒤 시트 단위 메타데이터(데이터 유효성 검사, 틀 고정 등)를 덧붙이는 훅.
 * SXSSF는 flush된 행을 다시 열 수 없으므로 셀 값·스타일은 여기서 만지지 않는다.
 */
@FunctionalInterface
public interface ExcelSheetDecorator {

    /**
     * @param sheet        기록이 끝난 시트(header = 0행)
     * @param dataRowCount header를 제외한 데이터 행 수
     */
    void decorate(Sheet sheet, int dataRowCount);
}
