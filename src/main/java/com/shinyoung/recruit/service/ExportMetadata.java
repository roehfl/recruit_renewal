package com.shinyoung.recruit.service;

/**
 * Excel export(정보 반출) 감사 metadata(PII-free). {@code fileName} 은 시스템 생성 다운로드명이다 —
 * 사용자 입력 파일명을 export 에 쓰게 되면 hash 로 전환해야 한다(리뷰 2차 #2).
 */
public record ExportMetadata(
        String datasetType,
        String filtersHash,
        String filtersSafeJson,
        long rowCount,
        String fileName
) implements AuditMetadata {
}
