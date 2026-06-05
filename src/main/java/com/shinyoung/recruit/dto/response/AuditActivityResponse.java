package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.AuditTargetType;

import java.time.LocalDateTime;

/**
 * 감사 로그 read API 응답(Phase 09b). 권한별 projection 분리 —
 * {@code ipAddress}/{@code userAgent} 는 민감 필드라 ROLE_PRIVACY_ADMIN 만 원문을 보고,
 * ROLE_RECRUIT_ADMIN 은 마스킹({@code ***}) 값을 받는다. 지원자 원문 PII 는 애초에 저장되지 않는다.
 */
public record AuditActivityResponse(
        Long id,
        LocalDateTime occurredAt,
        ActorType actorType,
        String actorId,
        String actorRoleSnapshot,
        AuditActionType actionType,
        AuditActionResult actionResult,
        AuditTargetType targetType,
        String targetId,
        Long jobPostingId,
        Long applicationId,
        String applicantRefHash,
        AuditReasonCode reasonCode,
        String reasonMessage,
        String correlationId,
        String ipAddress,
        String userAgent,
        String metadataJson
) {

    private static final String MASKED = "***";

    public static AuditActivityResponse from(ActivityLog log, boolean includeSensitive) {
        return new AuditActivityResponse(
                log.getId(),
                log.getOccurredAt(),
                log.getActorType(),
                log.getActorId(),
                log.getActorRoleSnapshot(),
                log.getActionType(),
                log.getActionResult(),
                log.getTargetType(),
                log.getTargetId(),
                log.getJobPostingId(),
                log.getApplicationId(),
                log.getApplicantRefHash(),
                log.getReasonCode(),
                log.getReasonMessage(),
                log.getCorrelationId(),
                mask(log.getIpAddress(), includeSensitive),
                mask(log.getUserAgent(), includeSensitive),
                log.getMetadataJson()
        );
    }

    private static String mask(String value, boolean includeSensitive) {
        if (value == null || includeSensitive) {
            return value;
        }
        return MASKED;
    }
}
