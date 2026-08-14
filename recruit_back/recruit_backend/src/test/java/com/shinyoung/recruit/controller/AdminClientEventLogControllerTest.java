package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 09f-3 관리자 조회 — narrow matcher 권한 + 권한별 마스킹(stackSummary 포함) + 가드 + 404. */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminClientEventLogControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ClientEventLogRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private ClientEventLog seed() {
        return repository.save(ClientEventLog.builder()
                .receivedAt(LocalDateTime.now().minusDays(1))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-0001")
                .clientEventId("event-0001")
                .applicationId(123L)
                .ipAddress("10.0.0.1")
                .userAgent("test-agent")
                .principalHash("abc123hash")
                .stackSummary("at submit (app.js:1)")
                .build());
    }

    private Authentication auth(String... roles) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "admin01", "인사팀", "관리자",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList());
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    void RECRUIT_ADMIN은_민감_필드가_마스킹된다() throws Exception {
        seed();

        mockMvc.perform(get("/api/admin/client-events")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("***"))
                .andExpect(jsonPath("$.data.content[0].userAgent").value("***"))
                .andExpect(jsonPath("$.data.content[0].principalHash").value("***"))
                .andExpect(jsonPath("$.data.content[0].stackSummary").value("***"))
                .andExpect(jsonPath("$.data.content[0].clientSessionId").value("session-0001"));
    }

    @Test
    void PRIVACY_ADMIN은_원문을_본다() throws Exception {
        seed();

        mockMvc.perform(get("/api/admin/client-events")
                        .with(authentication(auth("ROLE_PRIVACY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("10.0.0.1"))
                .andExpect(jsonPath("$.data.content[0].stackSummary").value("at submit (app.js:1)"));
    }

    @Test
    void 단건_조회와_404를_지원한다() throws Exception {
        ClientEventLog saved = seed();

        mockMvc.perform(get("/api/admin/client-events/" + saved.getId())
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientEventId").value("event-0001"));

        mockMvc.perform(get("/api/admin/client-events/999999")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void applicationId_필터가_동작한다() throws Exception {
        seed();

        mockMvc.perform(get("/api/admin/client-events")
                        .param("applicationId", "999")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 최신순으로_정렬된다() throws Exception {
        seed(); // receivedAt = now-1d, eventId event-0001
        repository.save(ClientEventLog.builder()
                .receivedAt(LocalDateTime.now().minusHours(1))
                .eventType(ClientEventType.JS_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-0002")
                .clientEventId("event-0002")
                .build());

        mockMvc.perform(get("/api/admin/client-events")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].clientEventId").value("event-0002"))
                .andExpect(jsonPath("$.data.content[1].clientEventId").value("event-0001"));
    }

    @Test
    void size_초과는_400이다() throws Exception {
        mockMvc.perform(get("/api/admin/client-events")
                        .param("size", "101")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void APPLICANT_권한은_403이다() throws Exception {
        mockMvc.perform(get("/api/admin/client-events")
                        .with(authentication(auth("ROLE_APPLICANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미인증은_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/client-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void PRIVACY_ADMIN은_cleanup을_수동_트리거할_수_있다() throws Exception {
        repository.save(ClientEventLog.builder()
                .receivedAt(LocalDateTime.now().minusDays(120))
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId("session-old-0001")
                .clientEventId("event-old-0001")
                .build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/client-events/cleanup")
                        .with(authentication(auth("ROLE_PRIVACY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1));
    }

    @Test
    void RECRUIT_ADMIN은_cleanup을_트리거할_수_없다() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/client-events/cleanup")
                        .with(authentication(auth("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isForbidden());
    }
}
