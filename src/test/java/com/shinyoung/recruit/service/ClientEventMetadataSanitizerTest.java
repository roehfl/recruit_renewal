package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** eventType별 exact allowlist(설계 6.5, 리뷰 Blocker 2) — allowlist 외 key는 전부 400. 금지 key는 2차 방어선. */
class ClientEventMetadataSanitizerTest {

    private final ClientEventMetadataSanitizer sanitizer = new ClientEventMetadataSanitizer(4000);

    @Test
    void null_또는_빈_metadata는_null을_반환한다() {
        assertThat(sanitizer.sanitize(ClientEventType.API_ERROR, null)).isNull();
        assertThat(sanitizer.sanitize(ClientEventType.API_ERROR, Map.of())).isNull();
    }

    @Test
    void allowlist_key는_JSON으로_직렬화된다() {
        String json = sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("durationMs", 1250, "retryable", false));

        assertThat(json).contains("\"durationMs\":1250");
        assertThat(json).contains("\"retryable\":false");
    }

    @Test
    void allowlist에_없는_key는_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR, Map.of("unknownKey", 1)))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void PII성_key는_어느_eventType에서도_거부된다() {
        for (String piiKey : List.of("mobile", "schoolName", "companyName", "fileName")) {
            assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR, Map.of(piiKey, "x")))
                    .as("key=%s", piiKey)
                    .isInstanceOf(InvalidClientEventLogException.class);
        }
    }

    @Test
    void 다른_eventType의_허용_key라도_해당_eventType_allowlist에_없으면_거부된다() {
        // fileExtension은 ATTACHMENT_UPLOAD_FAILED 전용
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR, Map.of("fileExtension", "pdf")))
                .isInstanceOf(InvalidClientEventLogException.class);
        // axiosCode는 API_ERROR/NETWORK_ERROR 전용
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.ATTACHMENT_UPLOAD_FAILED, Map.of("axiosCode", "ERR")))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void metadata가_허용되지_않는_eventType은_key가_있으면_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.SESSION_EXPIRED, Map.of("durationMs", 1)))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void nested_object와_array_value는_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("durationMs", Map.of("inner", 1))))
                .isInstanceOf(InvalidClientEventLogException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("durationMs", List.of(1, 2))))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void 문자열_value_200자_초과는_거부된다() {
        assertThatThrownBy(() -> sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("axiosCode", "x".repeat(201))))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void 직렬화_결과가_최대_길이를_초과하면_거부된다() {
        ClientEventMetadataSanitizer small = new ClientEventMetadataSanitizer(20);

        assertThatThrownBy(() -> small.sanitize(ClientEventType.API_ERROR,
                Map.of("axiosCode", "long-enough-value-here")))
                .isInstanceOf(InvalidClientEventLogException.class);
    }

    @Test
    void 문자열_value의_제어문자는_공백으로_치환된다() {
        String json = sanitizer.sanitize(ClientEventType.API_ERROR,
                Map.of("axiosCode", "ERR\r\nBAD\tCODE"));

        assertThat(json).doesNotContain("\\r").doesNotContain("\\n").doesNotContain("\\t");
        assertThat(json).contains("ERR BAD CODE");
    }

    @Test
    void null_value는_허용된다() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("axiosCode", null);

        String json = sanitizer.sanitize(ClientEventType.API_ERROR, metadata);

        assertThat(json).contains("\"axiosCode\":null");
    }
}
