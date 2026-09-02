package com.shinyoung.recruit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminApplicationFormConfigControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void POST_양식_저장_성공() throws Exception {
        Long jobPostingId = createDraftJobPosting();

        mockMvc.perform(post("/api/admin/job-postings/{jobPostingId}/application-form-config", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ApplicationFormConfigRequest(true, false, true, false, false, false, false)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.useEducation").value(true))
                // require* 를 생략하면 기존 설정값(false)이 유지된다.
                .andExpect(jsonPath("$.data.requireEducation").value(false))
                .andExpect(jsonPath("$.data.useCareer").value(false))
                .andExpect(jsonPath("$.data.useCertificate").value(true))
                .andExpect(jsonPath("$.data.requireCertificate").value(false));
    }

    @Test
    void POST_접수가_시작된_공고면_400() throws Exception {
        Long jobPostingId = createPublishedJobPostingInReception();

        mockMvc.perform(post("/api/admin/job-postings/{jobPostingId}/application-form-config", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void POST_없는_공고면_404() throws Exception {
        mockMvc.perform(post("/api/admin/job-postings/{jobPostingId}/application-form-config", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long createDraftJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "Form Config Test Posting",
                "<p>content</p>",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private Long createPublishedJobPostingInReception() {
        Long id = jobPostingService.create(new JobPostingCreateRequest(
                "In Reception Posting",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
        jobPostingService.publish(id);
        return id;
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
