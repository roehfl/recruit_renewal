// src/test/java/com/shinyoung/recruit/service/ClientEventLogServiceTest.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.request.ClientEventLogRequest;
import com.shinyoung.recruit.dto.response.ClientEventLogIngestResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientEventLogServiceTest {

    private ClientEventLogRepository repository;
    private ClientEventLogService service;
    private final AuditHmac auditHmac = new AuditHmac("test-client-event-secret");
    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-06-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        repository = mock(ClientEventLogRepository.class);
        service = new ClientEventLogService(
                repository,
                new ClientEventMetadataSanitizer(4000),
                new ClientEventRateLimiter(fixedClock, 1000, 1000, 1000),
                auditHmac,
                fixedClock,
                true
        );
    }

    private ClientEventLogRequest request(ClientEventSource source, String message, Map<String, Object> metadata) {
        return new ClientEventLogRequest(
                ClientEventType.API_ERROR, ClientEventSeverity.ERROR, source,
                "5d7c00ff-0d53-4ea3-bd44-68a9f7d68f9f", "6a1bd08e-3cd1-4c0f-85c0-e6a65e4a0a33",
                LocalDateTime.of(2026, 6, 10, 11, 59),
                "7c51f646-e75b-4d19-9f69-f82f7f0f2dc4",
                "APPLICATION_FORM", "EDUCATION_SECTION", "/applications/123/form?step=2", "SAVE_EDUCATION",
                10L, 123L,
                "POST", "/api/applicant/applications/123/education?retry=1", 500, "INTERNAL_SERVER_ERROR",
                message, null, "at saveEducation (app.js:120)",
                "2026.06.10-1", "Chrome", "126", "Windows", "1440x900", "Asia/Seoul",
                metadata
        );
    }

    private MockHttpServletRequest servletRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("10.0.0.9");
        servletRequest.addHeader("User-Agent", "JUnit-agent");
        return servletRequest;
    }

    @Test
    void 정상_수집시_서버값으로_저장된다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ClientEventLogIngestResponse response = service.record(
                request(ClientEventSource.APPLICANT_WEB, "Request failed with status code 500",
                        Map.of("durationMs", 1250)),
                null, servletRequest());

        assertThat(response.accepted()).isTrue();
        assertThat(response.duplicate()).isFalse();

        ArgumentCaptor<ClientEventLog> captor = ArgumentCaptor.forClass(ClientEventLog.class);
        verify(repository).saveAndFlush(captor.capture());
        ClientEventLog saved = captor.getValue();
        assertThat(saved.getReceivedAt()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-06-10T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.9");        // 서버 추출값
        assertThat(saved.getUserAgent()).isEqualTo("JUnit-agent");     // 서버 추출값
        assertThat(saved.getApiPath()).isEqualTo("/api/applicant/applications/123/education"); // query 제거
        assertThat(saved.getRoutePath()).isEqualTo("/applications/123/form");                  // query 제거
        assertThat(saved.getPrincipalHash()).isNull();
        assertThat(saved.getPrincipalType()).isNull();
        assertThat(saved.getMetadataJson()).contains("\"durationMs\":1250");
    }

    @Test
    void 인증_사용자는_HMAC_hash만_저장되고_원문은_저장되지_않는다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "applicant01", "", "지원자", List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));

        service.record(request(ClientEventSource.APPLICANT_WEB, null, null), userDetails, servletRequest());

        ArgumentCaptor<ClientEventLog> captor = ArgumentCaptor.forClass(ClientEventLog.class);
        verify(repository).saveAndFlush(captor.capture());
        ClientEventLog saved = captor.getValue();
        assertThat(saved.getPrincipalHash())
                .isEqualTo(auditHmac.hmacHex("CLIENT_PRINCIPAL:applicant01"))
                .doesNotContain("applicant01");
        assertThat(saved.getPrincipalType()).isEqualTo("Employee"); // fromLdap 픽스처의 userType
    }

    @Test
    void source가_APPLICANT_WEB이_아니면_거부된다() {
        assertThatThrownBy(() -> service.record(
                request(ClientEventSource.ADMIN_WEB, null, null), null, servletRequest()))
                .isInstanceOf(InvalidClientEventLogException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void message의_7자리_이상_연속_숫자는_마스킹된다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(request(ClientEventSource.APPLICANT_WEB, "submit failed 010-1234-5678 retry", null),
                null, servletRequest());

        ArgumentCaptor<ClientEventLog> captor = ArgumentCaptor.forClass(ClientEventLog.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMessage())
                .isEqualTo("submit failed * retry")
                .doesNotContain("010")
                .doesNotContain("5678");
    }

    @Test
    void 중복_선확인되면_저장없이_duplicate_응답한다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(true);

        ClientEventLogIngestResponse response = service.record(
                request(ClientEventSource.APPLICANT_WEB, null, null), null, servletRequest());

        assertThat(response.accepted()).isFalse();
        assertThat(response.duplicate()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void race_충돌도_duplicate_응답으로_흡수되고_예외가_전파되지_않는다() {
        when(repository.existsByClientSessionIdAndClientEventId(anyString(), anyString())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk violation"));

        ClientEventLogIngestResponse response = service.record(
                request(ClientEventSource.APPLICANT_WEB, null, null), null, servletRequest());

        assertThat(response.accepted()).isFalse();
        assertThat(response.duplicate()).isTrue();
    }

    @Test
    void enabled가_꺼져있으면_저장하지_않는다() {
        ClientEventLogService disabled = new ClientEventLogService(
                repository, new ClientEventMetadataSanitizer(4000),
                new ClientEventRateLimiter(fixedClock, 1000, 1000, 1000), auditHmac, fixedClock, false);

        ClientEventLogIngestResponse response = disabled.record(
                request(ClientEventSource.APPLICANT_WEB, null, null), null, servletRequest());

        assertThat(response.accepted()).isFalse();
        assertThat(response.duplicate()).isFalse();
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).existsByClientSessionIdAndClientEventId(anyString(), anyString());
    }
}
