package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import lombok.Builder;

/**
 * ActivityLogService 기록 요청(Phase 09a). 호출부는 이 record 를 만들어 {@code recordInCurrentTx}/
 * {@code recordRequiresNew} 에 전달한다.
 *
 * <p>{@code applicantId} 는 {@code applicantRefHash}(HMAC) 계산 입력으로만 쓰이고 원문은 저장되지 않는다.
 * {@code correlationId} 는 보통 null 로 두면 ActivityLogService 가 MDC(요청 필터)에서 채운다.
 */
@Builder
public record AuditEvent(
        ActorType actorType,
        String actorId,
        String actorRoleSnapshot,
        AuditActionType actionType,
        AuditActionResult actionResult,
        AuditTargetType targetType,
        String targetId,
        Long jobPostingId,
        Long applicationId,
        Long applicantId,
        AuditReasonCode reasonCode,
        String reasonMessage,
        String ipAddress,
        String userAgent,
        String correlationId,
        AuditMetadata metadata
) {
}
