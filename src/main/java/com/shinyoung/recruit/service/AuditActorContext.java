package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.ActorType;

/**
 * 서비스 계층 감사 계측용 행위자/요청 컨텍스트(Phase 09b). 컨트롤러를 거치지 않는 위치(@Transactional
 * 서비스 내부)에서 in-tx 감사를 남길 때 {@link AuditRequestContextResolver} 가 채워 준다. PII 값은 담지 않는다.
 */
public record AuditActorContext(
        ActorType actorType,
        String actorId,
        String actorRoleSnapshot,
        String ipAddress,
        String userAgent
) {
}
