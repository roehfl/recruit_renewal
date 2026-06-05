package com.shinyoung.recruit.service;

/** Application PDF 생성(정보 반출) 감사 metadata(PII-free, 식별자만). */
public record PdfMetadata(
        Long applicationId,
        Long jobPostingId,
        Long jobPositionId
) implements AuditMetadata {
}
