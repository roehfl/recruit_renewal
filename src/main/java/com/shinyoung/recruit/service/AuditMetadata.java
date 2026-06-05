package com.shinyoung.recruit.service;

/**
 * ActivityLog {@code metadataJson} 의 타입드 입력(Phase 09a foundation → 09b sealed).
 *
 * <p>호출부에서 자유 {@code Map<String,Object>}/raw JSON 문자열을 넘기면 누군가 applicantName/phone/email 을
 * 넣을 위험이 있으므로, actionType 별 typed record 로만 전달한다. 직렬화는 {@link ActivityLogService} 내부에서만
 * 수행한다(리뷰 #3). 구현 record 는 <b>PII-free(지원자 원문 미포함)</b> 만 담는다.
 *
 * <p>actor/ip/ua/correlationId/occurredAt 는 metadata 가 아니라 ActivityLog <b>컬럼</b>에 둔다 — 중복 금지.
 * 사용자 제공 파일명 원문 금지(리뷰 2차 #2) — {@code sourceFileNameHash} + 확장자만.
 * 보존/파기(9c~9e) metadata record 는 해당 슬라이스에서 permits 에 추가한다.
 */
public sealed interface AuditMetadata permits
        ExportMetadata,
        PdfMetadata,
        UploadMetadata,
        UploadConflictMetadata,
        StageResultChangeMetadata,
        EvaluationReopenMetadata,
        AttachmentAdminMetadata {
}
