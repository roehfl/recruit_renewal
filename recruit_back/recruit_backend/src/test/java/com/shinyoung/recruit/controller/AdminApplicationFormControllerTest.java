package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminApplicationFormControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void GET_설정_현황_목록_조회() throws Exception {
        createDraftJobPosting("2026 상반기 신입공채");

        mockMvc.perform(get("/api/admin/application-forms").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].configState").exists())
                .andExpect(jsonPath("$.data.content[0].sectionSummary.enabledCount").exists())
                .andExpect(jsonPath("$.data.content[0].editable").exists());
    }

    @Test
    void GET_설정_상태_필터가_적용된다() throws Exception {
        createDraftJobPosting("2026 상반기 신입공채");

        mockMvc.perform(get("/api/admin/application-forms").param("configState", "OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // 레이아웃을 저장하지 않은 공고는 DEFAULT 이므로 OK 필터에서는 빠진다.
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void GET_페이지_크기가_범위를_벗어나면_400() throws Exception {
        mockMvc.perform(get("/api/admin/application-forms").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void createDraftJobPosting(String title) {
        jobPostingService.create(new JobPostingCreateRequest(
                title,
                "<p>content</p>",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(true, true, false, false, true, false, false)
        ));
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
