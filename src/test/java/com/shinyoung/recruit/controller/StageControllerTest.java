package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.service.JobPostingService;
import com.shinyoung.recruit.service.StageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class StageControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private StageService stageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void get_stages_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long firstStageId = createStage(jobPostingId, 0, false);
        Long secondStageId = createStage(jobPostingId, 1, true);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].id").value(firstStageId))
                .andExpect(jsonPath("$.data[1].id").value(secondStageId));
    }

    @Test
    void get_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages/{stageId}", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.id").value(stageId))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void create_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStageJson(0, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void update_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}", jobPostingId, stageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStageJson(1, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void reorder_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long firstStageId = createStage(jobPostingId, 0, false);
        Long secondStageId = createStage(jobPostingId, 1, true);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/reorder", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "stageId": %d,
                                      "stageOrder": 1
                                    },
                                    {
                                      "stageId": %d,
                                      "stageOrder": 0
                                    }
                                  ]
                                }
                                """.formatted(firstStageId, secondStageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].id").value(secondStageId))
                .andExpect(jsonPath("$.data[0].stageOrder").value(0))
                .andExpect(jsonPath("$.data[1].id").value(firstStageId))
                .andExpect(jsonPath("$.data[1].stageOrder").value(1));
    }

    @Test
    void start_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/start", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void announce_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/announce", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void close_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        stageService.announce(jobPostingId, stageId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/close", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void delete_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/delete", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void invalid_create_request_returns_api_response() throws Exception {
        mockMvc.perform(post("/admin/job-postings/1/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stageName": " ",
                                  "stageType": "DOCUMENT",
                                  "stageOrder": 0,
                                  "resultAnnouncementDateTime": "2026-07-01T10:00:00",
                                  "finalStage": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalid_reorder_request_returns_api_response() throws Exception {
        mockMvc.perform(post("/admin/job-postings/1/stages/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "stageId": null,
                                      "stageOrder": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void stage_not_found_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages/{stageId}", jobPostingId, 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalid_status_command_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/announce", jobPostingId, stageId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_method_is_not_supported_for_stage_update() throws Exception {
        mockMvc.perform(put("/admin/job-postings/{jobPostingId}/stages/{stageId}", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void delete_http_method_is_not_supported_for_stage_delete() throws Exception {
        mockMvc.perform(delete("/admin/job-postings/{jobPostingId}/stages/{stageId}", 1L, 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    private Long createJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 2, 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));
    }

    private Long createStage(Long jobPostingId, int stageOrder, boolean finalStage) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Document screening",
                StageType.DOCUMENT,
                stageOrder,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                finalStage
        ));
    }

    private String createStageJson(int stageOrder, boolean finalStage) {
        return """
                {
                  "stageName": "Document screening",
                  "stageType": "DOCUMENT",
                  "stageOrder": %d,
                  "resultAnnouncementDateTime": "2026-07-01T10:00:00",
                  "finalStage": %s
                }
                """.formatted(stageOrder, finalStage);
    }

    private String updateStageJson(int stageOrder, boolean finalStage) {
        return """
                {
                  "stageName": "First interview",
                  "stageType": "FIRST_INTERVIEW",
                  "stageOrder": %d,
                  "resultAnnouncementDateTime": "2026-07-02T10:00:00",
                  "finalStage": %s
                }
                """.formatted(stageOrder, finalStage);
    }
}
