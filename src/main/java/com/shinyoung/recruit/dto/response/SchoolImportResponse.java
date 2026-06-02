package com.shinyoung.recruit.dto.response;

import java.util.List;

/**
 * School xlsx import(upsert) 결과 요약. all-or-nothing 이 아니라 행 단위로 적용하고, 유효하지 않은 행만 skip 한다.
 *
 * @param inserted 신규 insert 행 수
 * @param updated  기존 update 행 수
 * @param skipped  적용 안 된 행 수(= errors 길이)
 */
public record SchoolImportResponse(
        int totalRows,
        int inserted,
        int updated,
        int skipped,
        List<SchoolImportRowError> errors
) {
}
