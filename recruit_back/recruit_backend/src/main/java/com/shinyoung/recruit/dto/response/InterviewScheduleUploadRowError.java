package com.shinyoung.recruit.dto.response;

import java.util.List;

/** 업로드 행 오류. {@code rowNumber} 는 엑셀 기준 1-based(헤더 = 1행). */
public record InterviewScheduleUploadRowError(
        int rowNumber,
        List<String> messages
) {
}
