package com.shinyoung.recruit.service;

import java.util.function.Function;

/**
 * Excel export 시트의 단일 컬럼 정의: header 텍스트 + row → 셀 문자열 추출기.
 *
 * <p>{@code readOnly}가 true면 writer가 header·data 셀에 회색 음영을 넣는다(사용자에게 "수정 금지" 열임을
 * 시각적으로 알리는 용도 — 값 보호는 upload parser의 교차검증이 담당한다).
 *
 * @param <T> export row 타입(projection DTO)
 */
public record ExportColumn<T>(String header, Function<T, String> extractor, boolean readOnly) {

    public ExportColumn(String header, Function<T, String> extractor) {
        this(header, extractor, false);
    }

    public String value(T row) {
        String extracted = extractor.apply(row);
        return extracted == null ? "" : extracted;
    }
}
