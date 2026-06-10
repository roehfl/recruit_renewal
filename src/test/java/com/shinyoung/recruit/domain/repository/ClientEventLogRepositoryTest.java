package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.InvalidClientEventLogException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ClientEventLogRepositoryTest {

    @Autowired
    ClientEventLogRepository clientEventLogRepository;

    private ClientEventLog.ClientEventLogBuilder baseBuilder(String sessionId, String eventId) {
        return ClientEventLog.builder()
                .receivedAt(LocalDateTime.of(2026, 6, 10, 12, 0))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId(sessionId)
                .clientEventId(eventId);
    }

    @Test
    void 저장_조회_enum_매핑() {
        ClientEventLog saved = clientEventLogRepository.save(baseBuilder("session-0001", "event-0001")
                .relatedCorrelationId("corr-1")
                .pageCode("APPLICATION_FORM")
                .httpMethod("POST")
                .apiPath("/api/applicant/applications/1/education")
                .httpStatus(500)
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("Request failed with status code 500")
                .applicationId(123L)
                .jobPostingId(10L)
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .metadataJson("{\"durationMs\":1250}")
                .build());

        assertThat(saved.getId()).isNotNull();
        ClientEventLog found = clientEventLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventType()).isEqualTo(ClientEventType.API_ERROR);
        assertThat(found.getSeverity()).isEqualTo(ClientEventSeverity.ERROR);
        assertThat(found.getSource()).isEqualTo(ClientEventSource.APPLICANT_WEB);
        assertThat(found.getReceivedAt()).isEqualTo(LocalDateTime.of(2026, 6, 10, 12, 0));
        assertThat(found.getMetadataJson()).isEqualTo("{\"durationMs\":1250}");
        assertThat(clientEventLogRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_session_event_쌍은_unique_제약으로_거부된다() {
        clientEventLogRepository.saveAndFlush(baseBuilder("session-0001", "event-0001").build());

        assertThatThrownBy(() ->
                clientEventLogRepository.saveAndFlush(baseBuilder("session-0001", "event-0001").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsBy로_중복을_선확인한다() {
        clientEventLogRepository.save(baseBuilder("session-0001", "event-0001").build());

        assertThat(clientEventLogRepository
                .existsByClientSessionIdAndClientEventId("session-0001", "event-0001")).isTrue();
        assertThat(clientEventLogRepository
                .existsByClientSessionIdAndClientEventId("session-0001", "other")).isFalse();
    }

    @Test
    void 필수값_누락이면_엔티티_생성이_거부된다() {
        assertThatThrownBy(() -> ClientEventLog.builder()
                .receivedAt(LocalDateTime.of(2026, 6, 10, 12, 0))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-0001")
                // clientEventId 누락
                .build())
                .isInstanceOf(InvalidClientEventLogException.class);
    }
}
