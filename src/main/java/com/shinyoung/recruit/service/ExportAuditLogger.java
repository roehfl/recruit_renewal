package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Export 동작에 대한 SLF4J 구조적 audit 로그. applications export는 admin이 연락처(PII)를 보는
 * 최초 surface이고, 다른 dataset export도 운영 추적이 필요하므로 생성 시 주체/요청 메타 + 필터/행수/파일명을 남긴다.
 *
 * <p>이름/전화번호/이메일 등 PII 값 자체는 audit에 직접 남기지 않는다. 필터는 allowlist 기반의
 * 비-PII 값만 기록하고, 추가로 변경 추적용 {@code filtersHash}를 함께 남긴다. 영속 {@code ActivityLog}
 * 도입 시 이관한다.
 */
@Component
public class ExportAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("recruit.audit.export");

    private final Clock clock;

    public ExportAuditLogger(Clock clock) {
        this.clock = clock;
    }

    /**
     * dataset 공통 export audit. {@code filters}는 비-PII allowlist 값만 담아야 한다.
     */
    public void logExport(
            String datasetType,
            ExportAuditContext context,
            Map<String, Object> filters,
            ExcelExportFile file
    ) {
        String filtersSafeJson = toJson(filters);
        log.info(
                "export audit eventType={}_EXPORT datasetType={} timestamp={} "
                        + "actorLoginId={} authority={} clientIp={} userAgent={} requestId={} "
                        + "filtersHash={} filtersSafeJson={} rowCount={} fileName={}",
                datasetType,
                datasetType,
                LocalDateTime.now(clock),
                context.actorLoginId(),
                context.authority(),
                context.clientIp(),
                context.userAgent(),
                context.requestId(),
                HashUtil.sha256(filtersSafeJson),
                filtersSafeJson,
                file.rowCount(),
                file.fileName()
        );
    }

    public void logApplicationsExport(
            ExportAuditContext context,
            Long jobPostingId,
            Long jobPositionId,
            String status,
            ExcelExportFile file
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("jobPostingId", jobPostingId);
        filters.put("jobPositionId", jobPositionId);
        filters.put("status", status);
        logExport("APPLICATIONS", context, filters, file);
    }

    private String toJson(Map<String, Object> filters) {
        return filters.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + valueJson(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String valueJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return "\"" + value.toString().replace("\"", "'") + "\"";
    }
}
