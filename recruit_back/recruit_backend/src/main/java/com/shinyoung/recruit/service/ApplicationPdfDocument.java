package com.shinyoung.recruit.service;

/**
 * 생성된 Application PDF 핸들. 지원자 1명 = PDF 1개라 메모리 byte[]로 보유한다(대용량 streaming 대상 아님).
 * 컨트롤러는 content를 응답 body로 쓰고, audit를 위해 jobPostingId/jobPositionId를 함께 받는다.
 */
public record ApplicationPdfDocument(
        byte[] content,
        String fileName,
        Long jobPostingId,
        Long jobPositionId
) {

    public static final String CONTENT_TYPE = "application/pdf";
}
