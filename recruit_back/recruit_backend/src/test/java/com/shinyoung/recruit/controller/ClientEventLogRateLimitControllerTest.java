package com.shinyoung.recruit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * rate limit 429(Phase 09f-1). in-memory limiter 상태가 컨텍스트 단위로 공유되므로
 * 낮은 한도 properties로 별도 컨텍스트를 만들어 다른 테스트와 격리한다.
 */
@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "client-event-log.rate-limit.per-minute-ip=2",
        "client-event-log.rate-limit.per-minute-session=2",
        "client-event-log.rate-limit.per-minute-principal=2"
})
@Transactional
class ClientEventLogRateLimitControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String body(String sessionId) {
        return """
                {
                  "eventType": "API_ERROR",
                  "severity": "ERROR",
                  "source": "APPLICANT_WEB",
                  "clientSessionId": "%s",
                  "clientEventId": "%s"
                }
                """.formatted(sessionId, UUID.randomUUID().toString());
    }

    @Test
    void ip_한도_초과는_sessionId를_바꿔도_429다() throws Exception {
        // per-minute-ip=2 — sessionId를 매번 바꿔 session 한도를 피해도 ip 한도가 차단한다(리뷰 Major 5).
        mockMvc.perform(post("/api/client-events").contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/client-events").contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString()))).andExpect(status().isOk());

        mockMvc.perform(post("/api/client-events").contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }
}
