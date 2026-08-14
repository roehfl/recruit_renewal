package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Application PDF 생성(정보 반출) 감사 adapter(Phase 09b dual-write). 영속 ActivityLog 가 source of truth,
 * SLF4J 는 보조. <b>fail-close</b> — ActivityLog insert 실패 시 예외 전파로 PDF 응답이 나가지 않는다(ADR-0006).
 * PDF 는 메모리 byte[] 라 temp 파일 정리는 불필요하다. PII 값 자체는 기록하지 않는다.
 */
@Component
public class PdfAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("recruit.audit.pdf");

    private final Clock clock;
    private final ActivityLogService activityLogService;

    public PdfAuditLogger(Clock clock, ActivityLogService activityLogService) {
        this.clock = clock;
        this.activityLogService = activityLogService;
    }

    public void logApplicationPdf(
            ExportAuditContext context,
            Long applicationId,
            Long jobPostingId,
            Long jobPositionId
    ) {
        activityLogService.recordRequiresNew(AuditEvent.builder()
                .actorType(ActorType.EMPLOYEE)
                .actorId(context.actorLoginId())
                .actorRoleSnapshot(context.authority())
                .actionType(AuditActionType.APPLICATION_PDF)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.APPLICATION_PDF)
                .targetId(applicationId == null ? null : String.valueOf(applicationId))
                .jobPostingId(jobPostingId)
                .applicationId(applicationId)
                .ipAddress(context.clientIp())
                .userAgent(context.userAgent())
                .metadata(new PdfMetadata(applicationId, jobPostingId, jobPositionId))
                .build());

        log.info(
                "pdf audit eventType=APPLICATION_PDF applicationId={} jobPostingId={} jobPositionId={} timestamp={} "
                        + "actorLoginId={} authority={} clientIp={} userAgent={} requestId={}",
                applicationId,
                jobPostingId,
                jobPositionId,
                LocalDateTime.now(clock),
                context.actorLoginId(),
                context.authority(),
                context.clientIp(),
                context.userAgent(),
                context.requestId());
    }
}
