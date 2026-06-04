package com.shinyoung.recruit.service;

/**
 * ActivityLog {@code metadataJson} 의 타입드 입력(Phase 09a foundation).
 *
 * <p>호출부에서 자유 {@code Map<String,Object>}/raw JSON 문자열을 넘기면 누군가 applicantName/phone/email 을
 * 넣을 위험이 있으므로, actionType 별 typed record 로만 전달한다. 직렬화는 {@link ActivityLogService} 내부에서만
 * 수행한다(리뷰 #3). 구현 record 는 <b>PII-free(지원자 원문 미포함)</b> 만 담는다.
 *
 * <p>09b 에서 이 인터페이스를 {@code sealed} 로 좁히고 구체 record(ExportMetadata/PdfMetadata/UploadMetadata/
 * StageResultChangeMetadata/PurgeBatchMetadata …)를 추가한다.
 */
public interface AuditMetadata {
}
