package com.shinyoung.recruit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinyoung.recruit.service.nice.MockNiceClient;
import com.shinyoung.recruit.service.nice.NicePlaindataCodec;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class NiceVerificationControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Clock clock;

    @Autowired
    private NicePlaindataCodec codec;

    private MockMvc mockMvc;
    private MockNiceClient client;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        // 애플리케이션 빈과 같은 Clock 을 쓴다. 암호문 생성 시각 검사가 어긋나지 않게 한다.
        client = new MockNiceClient(clock, codec);
    }

    /** 요청을 발급하고 그 EncodeData 에서 REQ_SEQ 를 꺼낸다. */
    private String issueReqSeq(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/nice/request").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String encodeData = new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data").path("encodeData").asText();
        return codec.decode(client.decode(encodeData).plaindata()).get("REQ_SEQ");
    }

    private String niceSuccessPayload(String reqSeq) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        return client.encode(codec.encode(fields));
    }

    /** 콜백은 세션 없이(cross-site POST) form-urlencoded 로 들어온다. */
    private String callbackAndExtractToken(String reqSeq) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/nice/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", niceSuccessPayload(reqSeq)))
                .andExpect(status().isSeeOther())
                .andReturn();

        return result.getResponse().getHeader("Location").split("token=")[1];
    }

    @Test
    void callbackWithoutSessionRedirectsToFrontResultRoute() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String reqSeq = issueReqSeq(session);

        MvcResult result = mockMvc.perform(post("/api/auth/nice/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", niceSuccessPayload(reqSeq)))
                .andExpect(status().isSeeOther())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith("/nice-auth/result?token="));
    }

    /*
     * 콜백은 NICE 가 사용자 브라우저(팝업)로 폼 POST 한다. 실패를 400 JSON 으로 돌려주면 팝업에 원시 JSON 이
     * 그대로 렌더되고 부모창에 실패가 전달되지 않는다. 토큰 없이 결과 화면으로 보내면 프론트가 FAIL 을 알리고 닫는다.
     */
    @Test
    void callbackFailureRedirectsToResultRouteWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/nice/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", "INVALID"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/nice-auth/result"));
    }

    @Test
    void errorCallbackFailureRedirectsToResultRouteWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/nice/callback/error")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("EncodeData", "INVALID"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/nice-auth/result"));
    }

    @Test
    void resultStoresIdentityInSessionAndOmitsCiFromResponse() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = callbackAndExtractToken(issueReqSeq(session));

        MvcResult result = mockMvc.perform(post("/api/auth/nice/result")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.phoneNumber").value("01012345678"))
                .andReturn();

        // 응답 본문 어디에도 CI 가 없어야 한다. 이 기능의 핵심 보안 속성이다.
        assertEquals(-1, result.getResponse().getContentAsString().indexOf("CI-VALUE-1"));

        NiceVerifiedIdentity identity = (NiceVerifiedIdentity)
                session.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY);
        assertNotNull(identity);
        assertEquals("CI-VALUE-1", identity.ci());
    }

    @Test
    void resultFromDifferentSessionDoesNotPopulateSession() throws Exception {
        MockHttpSession requester = new MockHttpSession();
        String token = callbackAndExtractToken(issueReqSeq(requester));

        MockHttpSession attacker = new MockHttpSession();
        mockMvc.perform(post("/api/auth/nice/result")
                        .session(attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().is4xxClientError());

        assertNull(attacker.getAttribute(NiceVerificationController.VERIFIED_SESSION_KEY));
    }

    @Test
    void resultTokenCannotBeExchangedTwice() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = callbackAndExtractToken(issueReqSeq(session));

        mockMvc.perform(post("/api/auth/nice/result")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/nice/result")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void blankTokenIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/auth/nice/result")
                        .session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().is4xxClientError());
    }
}
