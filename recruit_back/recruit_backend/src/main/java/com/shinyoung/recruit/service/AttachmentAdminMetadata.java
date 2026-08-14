package com.shinyoung.recruit.service;

/**
 * 첨부 관리자 행위(delete) 감사 metadata(PII-free 집계만). 첨부 원본 파일명/삭제 사유 원문은
 * 도메인(ApplicationAttachment.deletionReason)이 보유하고, ActivityLog 에는 남기지 않는다.
 */
public record AttachmentAdminMetadata(
        boolean physicalDeleteRequested
) implements AuditMetadata {
}
