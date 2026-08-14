package com.shinyoung.recruit.dto.response;

/**
 * School import 에서 건너뛴(적용 안 된) 행과 사유.
 */
public record SchoolImportRowError(
        int rowNumber,
        String reason
) {
}
