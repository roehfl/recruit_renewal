package com.shinyoung.recruit.service;

/**
 * Export audit 공통 컨텍스트(요청/주체 메타). 07b/07d/07e로 audit이 확장돼도 logger 시그니처를
 * 다시 뜯지 않도록 요청 단위 메타를 하나로 묶는다. PII 값은 담지 않는다.
 */
public record ExportAuditContext(
        String actorLoginId,
        String authority,
        String clientIp,
        String userAgent,
        String requestId
) {
}
