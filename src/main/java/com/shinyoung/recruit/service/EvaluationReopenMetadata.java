package com.shinyoung.recruit.service;

/** 면접 평가 reopen 감사 metadata(PII-free — 식별자/시각만). */
public record EvaluationReopenMetadata(
        long interviewId,
        String previousSubmittedAt
) implements AuditMetadata {
}
