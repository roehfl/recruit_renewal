package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.response.ClientEventLogResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.ClientEventLogNotFoundException;
import com.shinyoung.recruit.exception.InvalidClientEventQueryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** read 가드(설계 8.2 — default 7일/max 90일/size 100) + 권한별 마스킹(8.3 — stackSummary 포함, 리뷰 Blocker 3). */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ClientEventLogReadServiceTest {

    @Autowired
    ClientEventLogReadService readService;

    @Autowired
    ClientEventLogRepository repository;

    private ClientEventLog seed(String sessionId, LocalDateTime receivedAt) {
        return repository.save(ClientEventLog.builder()
                .receivedAt(receivedAt)
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId(sessionId)
                .clientEventId("event-" + sessionId)
                .ipAddress("10.0.0.1")
                .userAgent("test-agent")
                .principalHash("abc123hash")
                .stackSummary("at submit (app.js:1)")
                .build());
    }

    @Test
    void 범위_미지정이면_최근_7일만_조회된다() {
        seed("session-0001", LocalDateTime.now().minusDays(1));
        seed("session-0002", LocalDateTime.now().minusDays(10)); // default range(7일) 밖

        PageResponse<ClientEventLogResponse> result = readService.search(
                null, null, null, null, null, null, null, null, null, 0, 20, false);

        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void includeSensitive가_false면_민감_필드가_마스킹된다() {
        seed("session-0001", LocalDateTime.now().minusDays(1));

        ClientEventLogResponse response = readService.search(
                null, null, null, null, null, null, null, null, null, 0, 20, false).content().get(0);

        assertThat(response.ipAddress()).isEqualTo("***");
        assertThat(response.userAgent()).isEqualTo("***");
        assertThat(response.principalHash()).isEqualTo("***");
        assertThat(response.stackSummary()).isEqualTo("***");
    }

    @Test
    void includeSensitive가_true면_원문을_본다() {
        seed("session-0001", LocalDateTime.now().minusDays(1));

        ClientEventLogResponse response = readService.search(
                null, null, null, null, null, null, null, null, null, 0, 20, true).content().get(0);

        assertThat(response.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(response.userAgent()).isEqualTo("test-agent");
        assertThat(response.principalHash()).isEqualTo("abc123hash");
        assertThat(response.stackSummary()).isEqualTo("at submit (app.js:1)");
    }

    @Test
    void 범위가_90일을_넘으면_거부된다() {
        assertThatThrownBy(() -> readService.search(
                null, null, null, null, null, null, null,
                LocalDateTime.now().minusDays(120), LocalDateTime.now(), 0, 20, false))
                .isInstanceOf(InvalidClientEventQueryException.class);
    }

    @Test
    void size가_100을_넘으면_거부된다() {
        assertThatThrownBy(() -> readService.search(
                null, null, null, null, null, null, null, null, null, 0, 101, false))
                .isInstanceOf(InvalidClientEventQueryException.class);
    }

    @Test
    void 없는_id_단건_조회는_NotFound_예외다() {
        assertThatThrownBy(() -> readService.getEvent(999999L, false))
                .isInstanceOf(ClientEventLogNotFoundException.class);
    }
}
