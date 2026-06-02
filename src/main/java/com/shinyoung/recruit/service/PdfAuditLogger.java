package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Application PDF 생성 audit. PDF는 식별된 1인의 전체 지원서로 Phase 07에서 가장 집중된 PII surface이므로
 * 생성 시 주체/요청 메타 + 대상 식별자를 SLF4J 구조적 로그로 남긴다(PII 값 자체는 기록하지 않는다).
 * 영속 {@code ActivityLog} 도입 시 이관한다.
 */
@Component
public class PdfAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("recruit.audit.pdf");

    private final Clock clock;

    public PdfAuditLogger(Clock clock) {
        this.clock = clock;
    }

    public void logApplicationPdf(
            ExportAuditContext context,
            Long applicationId,
            Long jobPostingId,
            Long jobPositionId
    ) {
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
