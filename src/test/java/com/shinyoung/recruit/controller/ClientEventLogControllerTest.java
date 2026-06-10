package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수집 API(Phase 09f-1) — permitAll, source 위조 거부(리뷰 Blocker 1), metadata allowlist(Blocker 2),
 * safe message code(Blocker 3), 중복 duplicate 흡수(409 누출 없음, Major 4), JSON-only 계약.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ClientEventLogControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String body(String source, String sessionId, String eventId, String extraJsonFields) {
        return """
                {
                  "eventType": "API_ERROR",
                  "severity": "ERROR",
                  "source": "%s",
                  "clientSessionId": "%s",
                  "clientEventId": "%s"%s
                }
                """.formatted(source, sessionId, eventId,
                extraJsonFields.isEmpty() ? "" : ",\n" + extraJsonFields);
    }

    private Authentication applicantAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "applicant01", "", "지원자", List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    void 미인증_상태에서도_수집된다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void 인증_상태에서도_수집된다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .with(authentication(applicantAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    @Test
    void anonymous가_ADMIN_WEB_source를_보내면_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ADMIN_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증_사용자도_ADMIN_WEB_source는_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .with(authentication(applicantAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ADMIN_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientSessionId_형식_위반은_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", "한글세션아이디!!", UUID.randomUUID().toString(), "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 허용되지_않은_metadata_key는_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                "\"metadata\": {\"phoneNumber\": \"010-1234-5678\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void message에_한글이_섞이면_400이다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                "\"message\": \"홍길동 지원자 저장 실패\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_이벤트_재전송은_duplicate로_흡수되고_409로_새지_않는다() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", sessionId, eventId, "")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", sessionId, eventId, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(false))
                .andExpect(jsonPath("$.data.duplicate").value(true));
    }

    @Test
    void JSON이_아닌_content_type은_415다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void CORS_응답에_X_Request_Id가_노출된다() throws Exception {
        mockMvc.perform(post("/api/client-events")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICANT_WEB", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"));
    }
}
