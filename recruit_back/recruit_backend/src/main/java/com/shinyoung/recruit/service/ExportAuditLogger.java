package com.shinyoung.recruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Export(정보 반출) 감사 adapter(Phase 09b dual-write). <b>영속 ActivityLog 가 source of truth</b> 이고
 * SLF4J 구조적 로그는 보조다. ActivityLog 기록은 {@code recordRequiresNew}(별도 tx) + <b>fail-close</b> —
 * 기록이 실패하면 예외가 전파되어 반출 응답이 나가지 않는다(ADR-0006). 호출부(컨트롤러)는 이때 temp xlsx 를
 * 정리하고 예외를 전파해야 한다(temp file 누수 방지, 리뷰 2차 #3).
 *
 * <p>이름/전화번호/이메일 등 PII 값 자체는 어디에도 남기지 않는다. 필터는 allowlist 기반의 비-PII 값만
 * 기록하고, 변경 추적용 {@code filtersHash}를 함께 남긴다.
 */
@Component
public class ExportAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("recruit.audit.export");

    private final Clock clock;
    private final ActivityLogService activityLogService;

    /**
     * filtersSafeJson 직렬화 전용 ObjectMapper. 수동 문자열 조합은 escape 누락(backslash/제어문자) 위험이
     * 있어 audit 코드에 부적합하다(9b 리뷰 Medium 2).
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    public ExportAuditLogger(Clock clock, ActivityLogService activityLogService) {
        this.clock = clock;
        this.activityLogService = activityLogService;
    }

    /**
     * dataset 공통 export audit. {@code filters}는 비-PII allowlist 값만 담아야 한다.
     * ActivityLog insert 실패 시 예외 전파(fail-close) — SLF4J 보조 로그도 남지 않는다.
     */
    public void logExport(
            String datasetType,
            ExportAuditContext context,
            Map<String, Object> filters,
            ExcelExportFile file
    ) {
        String filtersSafeJson = toJson(filters);
        String filtersHash = HashUtil.sha256(filtersSafeJson);

        activityLogService.recordRequiresNew(AuditEvent.builder()
                .actorType(ActorType.EMPLOYEE)
                .actorId(context.actorLoginId())
                .actorRoleSnapshot(context.authority())
                .actionType(exportActionType(datasetType))
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.EXPORT_DATASET)
                .targetId(datasetType)
                .jobPostingId(longFilter(filters, "jobPostingId"))
                .ipAddress(context.clientIp())
                .userAgent(context.userAgent())
                .metadata(new ExportMetadata(
                        datasetType, filtersHash, filtersSafeJson, file.rowCount(), file.fileName()))
                .build());

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
                filtersHash,
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

    /** datasetType → AuditActionType 고정 매핑. 미등록 dataset 은 taxonomy 누락이므로 즉시 실패시킨다. */
    private AuditActionType exportActionType(String datasetType) {
        return switch (datasetType) {
            case "APPLICATIONS" -> AuditActionType.EXPORT_APPLICATIONS;
            case "STAGE_RESULTS" -> AuditActionType.EXPORT_STAGE_RESULTS;
            case "INTERVIEWS" -> AuditActionType.EXPORT_INTERVIEWS;
            case "INTERVIEW_EVALUATIONS" -> AuditActionType.EXPORT_EVALUATIONS;
            case "STAGE_RESULT_UPLOAD_TEMPLATE" -> AuditActionType.EXPORT_STAGE_RESULT_TEMPLATE;
            default -> throw new IllegalArgumentException("Unknown export dataset type: " + datasetType);
        };
    }

    private Long longFilter(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        return value instanceof Long longValue ? longValue : null;
    }

    /**
     * 필터 값을 normalize(제어문자 제거 + trim)한 뒤 ObjectMapper 로 직렬화한다. 키는 코드 고정(allowlist)이고
     * 값만 외부 유래 가능성이 있으므로 값을 sanitize 한다 — CR/LF 로 로그 라인을 위조하거나 escape 누락으로
     * JSON 을 깨뜨릴 수 없다(9b 리뷰 Medium 2).
     */
    private String toJson(Map<String, Object> filters) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        filters.forEach((key, value) -> sanitized.put(key, sanitizeValue(value)));
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize export audit filters", e);
        }
    }

    private Object sanitizeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return value.toString().replaceAll("\\p{Cntrl}", " ").trim();
    }
}
