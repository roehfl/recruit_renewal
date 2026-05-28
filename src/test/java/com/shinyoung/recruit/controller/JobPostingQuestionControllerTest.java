package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.request.QuestionTemplateCreateRequest;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.dto.response.QuestionTemplateResponse;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.service.JobPostingQuestionService;
import com.shinyoung.recruit.service.JobPostingService;
import com.shinyoung.recruit.service.QuestionTemplateService;
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
class JobPostingQuestionControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private QuestionTemplateService questionTemplateService;

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void get_job_posting_questions_success() throws Exception {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/questions", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].questionId").value(question.questionId()))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0));
    }

    @Test
    void create_direct_question_success() throws Exception {
        Long jobPostingId = createJobPosting();

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directJson(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionId").isNumber())
                .andExpect(jsonPath("$.data.questionText").value("Why do you want to join us?"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void create_template_based_question_success() throws Exception {
        Long jobPostingId = createJobPosting();
        QuestionTemplateResponse template = createTemplate();

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionTemplateId": %d,
                                  "sortOrder": 0
                                }
                                """.formatted(template.templateId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionTemplateId").value(template.templateId()))
                .andExpect(jsonPath("$.data.questionText").value("Please introduce yourself."));
    }

    @Test
    void update_question_success() throws Exception {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions/{questionId}", jobPostingId, question.questionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionText").value("Updated question"))
                .andExpect(jsonPath("$.data.answerType").value("SHORT_TEXT"));
    }

    @Test
    void reorder_questions_success() throws Exception {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse first = createQuestion(jobPostingId, 0);
        JobPostingQuestionResponse second = createQuestion(jobPostingId, 1);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions/reorder", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questions": [
                                    {
                                      "questionId": %d,
                                      "sortOrder": 1
                                    },
                                    {
                                      "questionId": %d,
                                      "sortOrder": 0
                                    }
                                  ]
                                }
                                """.formatted(first.questionId(), second.questionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].questionId").value(second.questionId()))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0));
    }

    @Test
    void delete_command_deactivates_question_success() throws Exception {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions/{questionId}/delete", jobPostingId, question.questionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void validation_failure_returns_api_response() throws Exception {
        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionText": "Question",
                                  "category": "GENERAL",
                                  "answerType": "SHORT_TEXT",
                                  "required": true,
                                  "maxLength": 100,
                                  "sortOrder": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalid_enum_returns_api_response() throws Exception {
        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionText": "Question",
                                  "category": "UNKNOWN",
                                  "answerType": "SHORT_TEXT",
                                  "required": true,
                                  "maxLength": 100,
                                  "sortOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void published_job_posting_command_returns_bad_request() throws Exception {
        Long jobPostingId = createJobPosting();
        jobPostingService.publish(jobPostingId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directJson(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void not_found_cases_return_api_response() throws Exception {
        Long jobPostingId = createJobPosting();

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/questions", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/questions/{questionId}", jobPostingId, 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_and_delete_methods_are_not_supported() throws Exception {
        mockMvc.perform(put("/admin/job-postings/{jobPostingId}/questions/{questionId}", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/admin/job-postings/{jobPostingId}/questions/{questionId}", 1L, 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    private Long createJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));
    }

    private QuestionTemplateResponse createTemplate() {
        return questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "Self introduction",
                "Please introduce yourself.",
                "Use concrete examples.",
                QuestionCategory.SELF_INTRODUCTION,
                QuestionAnswerType.LONG_TEXT,
                true,
                5000
        ));
    }

    private JobPostingQuestionResponse createQuestion(Long jobPostingId, int sortOrder) {
        return jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Why do you want to join us?",
                "Focus on role fit.",
                QuestionCategory.JOB_SPECIFIC,
                QuestionAnswerType.LONG_TEXT,
                true,
                null,
                3000,
                sortOrder
        ));
    }

    private String directJson(int sortOrder) {
        return """
                {
                  "questionText": "Why do you want to join us?",
                  "helperText": "Focus on role fit.",
                  "category": "JOB_SPECIFIC",
                  "answerType": "LONG_TEXT",
                  "required": true,
                  "maxLength": 3000,
                  "sortOrder": %d
                }
                """.formatted(sortOrder);
    }

    private String updateJson(int sortOrder) {
        return """
                {
                  "questionText": "Updated question",
                  "helperText": "Updated helper",
                  "category": "GENERAL",
                  "answerType": "SHORT_TEXT",
                  "required": false,
                  "maxLength": 500,
                  "sortOrder": %d
                }
                """.formatted(sortOrder);
    }
}
