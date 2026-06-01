package com.shinyoung.recruit.service;

import java.util.function.Function;

/**
 * Excel export 시트의 단일 컬럼 정의: header 텍스트 + row → 셀 문자열 추출기.
 *
 * @param <T> export row 타입(projection DTO)
 */
public record ExportColumn<T>(String header, Function<T, String> extractor) {

    public String value(T row) {
        String extracted = extractor.apply(row);
        return extracted == null ? "" : extracted;
    }
}
