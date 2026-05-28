package com.shinyoung.recruit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormLayoutSaveRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminApplicationFormLayoutControllerTest {

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
    void GET_레이아웃_조회_기본_레이아웃_반환() throws Exception {
        Long jobPostingId = createDraftJobPosting(true, true, false);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.layoutStored").value(false))
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.pages").isArray())
                .andExpect(jsonPath("$.data.pages").isNotEmpty())
                .andExpect(jsonPath("$.data.availableSections").isArray())
                .andExpect(jsonPath("$.data.availableSections").isNotEmpty());
    }

    @Test
    void GET_레이아웃_조회_availableSections에_전체_레이아웃_섹션_포함() throws Exception {
        Long jobPostingId = createDraftJobPosting(true, false, false);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableSections[?(@.sectionType == 'BASIC_INFO')].enabled").value(true))
                .andExpect(jsonPath("$.data.availableSections[?(@.sectionType == 'BASIC_INFO')].required").value(true))
                .andExpect(jsonPath("$.data.availableSections[?(@.sectionType == 'BASIC_INFO')].source").value("ALWAYS"))
                .andExpect(jsonPath("$.data.availableSections[?(@.sectionType == 'EDUCATION')].enabled").value(true))
                .andExpect(jsonPath("$.data.availableSections[?(@.sectionType == 'EDUCATION')].source").value("APPLICATION_FORM_CONFIG"))
                .andExpect(jsonPath("$.data.availableSections[?(@.sectionType == 'CAREER')].enabled").value(false));
    }

    @Test
    void POST_레이아웃_저장_성공() throws Exception {
        Long jobPostingId = createDraftJobPosting(true, false, true);

        ApplicationFormLayoutSaveRequest request = new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "기본 정보", "기본 정보 페이지", 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0),
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.MILITARY, 1)
                )),
                new ApplicationFormLayoutSaveRequest.PageRequest(2, "학력", null, 1, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.EDUCATION, 0)
                ))
        ));

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.layoutStored").value(true))
                .andExpect(jsonPath("$.data.pages").isArray())
                .andExpect(jsonPath("$.data.pages.length()").value(2))
                .andExpect(jsonPath("$.data.pages[0].pageNo").value(1))
                .andExpect(jsonPath("$.data.pages[0].title").value("기본 정보"))
                .andExpect(jsonPath("$.data.pages[0].description").value("기본 정보 페이지"))
                .andExpect(jsonPath("$.data.pages[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.pages[0].items[0].sectionType").value("BASIC_INFO"))
                .andExpect(jsonPath("$.data.pages[0].items[0].sectionName").value("Basic Info"))
                .andExpect(jsonPath("$.data.pages[1].pageNo").value(2))
                .andExpect(jsonPath("$.data.pages[1].items[0].sectionType").value("EDUCATION"));
    }

    @Test
    void POST_저장_후_GET_조회시_layoutStored_true() throws Exception {
        Long jobPostingId = createDraftJobPosting(false, false, false);

        ApplicationFormLayoutSaveRequest request = new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Basic", null, 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0)
                ))
        ));

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layoutStored").value(true))
                .andExpect(jsonPath("$.data.pages.length()").value(1))
                .andExpect(jsonPath("$.data.pages[0].items[0].sectionType").value("BASIC_INFO"));
    }

    @Test
    void POST_마감된_공고에_저장_시_400() throws Exception {
        Long jobPostingId = createClosedJobPosting();

        ApplicationFormLayoutSaveRequest request = new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Basic", null, 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0)
                ))
        ));

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void POST_접수_시작_후_저장_시_400() throws Exception {
        Long jobPostingId = createPublishedJobPostingInReception();

        ApplicationFormLayoutSaveRequest request = new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Basic", null, 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0)
                ))
        ));

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void POST_비활성_섹션_배치시_검증_실패_400() throws Exception {
        Long jobPostingId = createDraftJobPosting(false, false, false);

        ApplicationFormLayoutSaveRequest request = new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Page", null, 0, List.of(
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.BASIC_INFO, 0),
                        new ApplicationFormLayoutSaveRequest.ItemRequest(ApplicationSectionType.EDUCATION, 1)
                ))
        ));

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void POST_잘못된_요청_body_400() throws Exception {
        Long jobPostingId = createDraftJobPosting(false, false, false);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pages\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void GET_preview_페이지_구조_응답() throws Exception {
        Long jobPostingId = createDraftJobPosting(true, true, true);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/application-form-layout/preview", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.jobPostingTitle").exists())
                .andExpect(jsonPath("$.data.pages").isArray())
                .andExpect(jsonPath("$.data.pages").isNotEmpty())
                .andExpect(jsonPath("$.data.pages[0].pageNo").exists())
                .andExpect(jsonPath("$.data.pages[0].title").exists())
                .andExpect(jsonPath("$.data.pages[0].items").isArray())
                .andExpect(jsonPath("$.data.pages[0].items[0].sectionType").exists())
                .andExpect(jsonPath("$.data.pages[0].items[0].sectionName").exists())
                .andExpect(jsonPath("$.data.pages[0].items[0].required").exists());
    }

    @Test
    void GET_존재하지_않는_공고_404() throws Exception {
        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/application-form-layout", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 지원되지_않는_HTTP_메서드_405() throws Exception {
        Long jobPostingId = createDraftJobPosting(false, false, false);

        mockMvc.perform(put("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/admin/job-postings/{jobPostingId}/application-form-layout", jobPostingId))
                .andExpect(status().isMethodNotAllowed());
    }

    private Long createDraftJobPosting(boolean useEducation, boolean useCareer, boolean useMilitary) {
        return jobPostingService.create(new JobPostingCreateRequest(
                "Layout Test Posting",
                "<p>content</p>",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(useEducation, useCareer, false, false, useMilitary, false, false)
        ));
    }

    private Long createClosedJobPosting() {
        Long id = jobPostingService.create(new JobPostingCreateRequest(
                "Closed Posting",
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
        jobPostingService.publish(id);
        jobPostingService.close(id);
        return id;
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
