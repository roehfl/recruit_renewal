package com.shinyoung.recruit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 학교 검색 API 계약 테스트. 인증키가 없는 테스트 환경이므로 외부 호출은 일어나지 않는다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class SchoolSearchControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 검색어가_없으면_외부호출_없이_빈목록() throws Exception {
        mockMvc.perform(get("/api/schools").param("educationLevel", "HIGH_SCHOOL").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 학교구분_누락은_400() throws Exception {
        mockMvc.perform(get("/api/schools").param("q", "서울").with(anonymous()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증키_미설정이면_502() throws Exception {
        // 테스트 환경에는 NEIS 인증키가 없다. 설정 누락은 상위 의존 장애로 502.
        mockMvc.perform(get("/api/schools").param("q", "서울").param("educationLevel", "HIGH_SCHOOL")
                        .with(anonymous()))
                .andExpect(status().isBadGateway());
    }
}
