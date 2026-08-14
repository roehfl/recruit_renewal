package com.shinyoung.recruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * client event metadata 검증/직렬화(Phase 09f, 설계 6.5).
 *
 * <p><b>eventType별 exact allowlist</b>(리뷰 Blocker 2) — allowlist에 없는 key는 400으로 거부한다.
 * 금지 key 패턴(denylist)은 allowlist 통과 후에도 적용되는 2차 방어선으로, 향후 allowlist 확장 시
 * PII key가 실수로 추가되는 것을 막는다.
 *
 * <p>value는 String/Number/Boolean/null만 허용한다(nested object/array 금지). 문자열은 200자 이하,
 * 직렬화 결과는 {@code client-event-log.max-metadata-json-length}(기본 4000자) 이하.
 */
@Component
public class ClientEventMetadataSanitizer {

    static final int MAX_KEYS = 20;
    static final int MAX_KEY_LENGTH = 50;
    static final int MAX_STRING_VALUE_LENGTH = 200;

    /** eventType별 허용 key(설계 6.5 표). 여기 없는 eventType/key 조합은 전부 거부. */
    private static final Map<ClientEventType, Set<String>> ALLOWLIST = Map.ofEntries(
            Map.entry(ClientEventType.API_ERROR, Set.of("durationMs", "retryable", "axiosCode")),
            Map.entry(ClientEventType.API_TIMEOUT, Set.of("durationMs", "timeoutMs")),
            Map.entry(ClientEventType.NETWORK_ERROR, Set.of("durationMs", "axiosCode")),
            Map.entry(ClientEventType.SESSION_EXPIRED, Set.of()),
            Map.entry(ClientEventType.FORBIDDEN, Set.of()),
            Map.entry(ClientEventType.JS_ERROR, Set.of("file", "line", "column")),
            Map.entry(ClientEventType.UNHANDLED_REJECTION, Set.of("reasonType")),
            Map.entry(ClientEventType.APPLICATION_DRAFT_SAVE_FAILED, Set.of("sectionCode", "failedStep")),
            Map.entry(ClientEventType.APPLICATION_SUBMIT_CLICKED, Set.of()),
            Map.entry(ClientEventType.APPLICATION_SUBMIT_FAILED, Set.of("sectionCode", "failedStep")),
            Map.entry(ClientEventType.ATTACHMENT_UPLOAD_FAILED, Set.of("fileSize", "fileExtension", "uploadStep")),
            Map.entry(ClientEventType.CLIENT_VALIDATION_FAILED, Set.of("sectionCode", "fieldCount", "errorCount")),
            Map.entry(ClientEventType.PAGE_OPENED, Set.of()),
            Map.entry(ClientEventType.CHECKPOINT, Set.of("checkpointCode"))
    );

    /** 2차 방어선 — allowlist가 확장돼도 절대 허용하면 안 되는 PII성 key(대소문자 무시, 설계 6.5). */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "name", "username", "applicantname", "email", "phone", "phonenumber",
            "ci", "cihash", "password", "birth", "address",
            "content", "answer", "essay", "resume", "coverletter",
            "filename", "originalfilename", "body", "requestbody", "responsebody"
    );

    private final int maxMetadataJsonLength;

    /** 직렬화 전용 ObjectMapper — 앱(web) Jackson 설정 변경이 저장 포맷에 새지 않도록 분리(ActivityLogService 선례). */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public ClientEventMetadataSanitizer(
            @Value("${client-event-log.max-metadata-json-length:4000}") int maxMetadataJsonLength
    ) {
        this.maxMetadataJsonLength = maxMetadataJsonLength;
    }

    /** 검증 통과 시 JSON 문자열, metadata 부재 시 null. 위반 시 {@link InvalidClientEventLogException}(400). */
    public String sanitize(ClientEventType eventType, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        if (metadata.size() > MAX_KEYS) {
            throw new InvalidClientEventLogException("metadata key는 최대 " + MAX_KEYS + "개입니다.");
        }

        Set<String> allowed = ALLOWLIST.getOrDefault(eventType, Set.of());
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
                throw new InvalidClientEventLogException("metadata key 형식이 올바르지 않습니다.");
            }
            if (!allowed.contains(key)) {
                throw new InvalidClientEventLogException(
                        "허용되지 않는 metadata key입니다. eventType=" + eventType + ", key=" + key);
            }
            if (FORBIDDEN_KEYS.contains(key.toLowerCase())) {
                throw new InvalidClientEventLogException("금지된 metadata key입니다. key=" + key);
            }
            sanitized.put(key, sanitizeValue(key, entry.getValue()));
        }

        String json = serialize(sanitized);
        if (json.length() > maxMetadataJsonLength) {
            throw new InvalidClientEventLogException(
                    "metadata 직렬화 길이는 최대 " + maxMetadataJsonLength + "자입니다.");
        }
        return json;
    }

    private Object sanitizeValue(String key, Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String s) {
            if (s.length() > MAX_STRING_VALUE_LENGTH) {
                throw new InvalidClientEventLogException(
                        "metadata 문자열 value는 최대 " + MAX_STRING_VALUE_LENGTH + "자입니다. key=" + key);
            }
            return s.replaceAll("\\p{Cntrl}+", " ").trim();
        }
        throw new InvalidClientEventLogException(
                "metadata value는 String/Number/Boolean/null만 허용됩니다. key=" + key);
    }

    private String serialize(Map<String, Object> sanitized) {
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            throw new InvalidClientEventLogException("metadata 직렬화에 실패했습니다.");
        }
    }
}
