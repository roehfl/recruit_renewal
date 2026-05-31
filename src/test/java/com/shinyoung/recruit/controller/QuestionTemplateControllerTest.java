package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.QuestionTemplateCreateRequest;
import com.shinyoung.recruit.dto.response.QuestionTemplateResponse;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class QuestionTemplateControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private QuestionTemplateService questionTemplateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void get_question_templates_success() throws Exception {
        createTemplate();

        mockMvc.perform(get("/api/admin/question-templates")
                        .param("page", "0")
                        .param("size", "20")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Self introduction"));
    }

    @Test
    void get_question_template_success() throws Exception {
        QuestionTemplateResponse template = createTemplate();

        mockMvc.perform(get("/api/admin/question-templates/{templateId}", template.templateId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.templateId").value(template.templateId()))
                .andExpect(jsonPath("$.data.questionText").value("Please introduce yourself."));
    }

    @Test
    void create_question_template_success() throws Exception {
        mockMvc.perform(post("/api/admin/question-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.templateId").isNumber())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void update_question_template_success() throws Exception {
        QuestionTemplateResponse template = createTemplate();

        mockMvc.perform(post("/api/admin/question-templates/{templateId}", template.templateId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Motivation",
                                  "questionText": "Why do you want to join us?",
                                  "helperText": "Focus on role fit.",
                                  "category": "JOB_SPECIFIC",
                                  "answerType": "SHORT_TEXT",
                                  "defaultRequired": true,
                                  "defaultMaxLength": 500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Motivation"))
                .andExpect(jsonPath("$.data.answerType").value("SHORT_TEXT"));
    }

    @Test
    void deactivate_question_template_success() throws Exception {
        QuestionTemplateResponse template = createTemplate();

        mockMvc.perform(post("/api/admin/question-templates/{templateId}/deactivate", template.templateId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void validation_failure_returns_api_response() throws Exception {
        mockMvc.perform(post("/api/admin/question-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "questionText": "Question",
                                  "category": "GENERAL",
                                  "answerType": "SHORT_TEXT",
                                  "defaultRequired": true,
                                  "defaultMaxLength": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalid_enum_returns_api_response() throws Exception {
        mockMvc.perform(post("/api/admin/question-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Title",
                                  "questionText": "Question",
                                  "category": "UNKNOWN",
                                  "answerType": "SHORT_TEXT",
                                  "defaultRequired": true,
                                  "defaultMaxLength": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void question_template_not_found_returns_api_response() throws Exception {
        mockMvc.perform(get("/api/admin/question-templates/{templateId}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_and_delete_methods_are_not_supported() throws Exception {
        mockMvc.perform(put("/api/admin/question-templates/{templateId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/admin/question-templates/{templateId}", 1L))
                .andExpect(status().isMethodNotAllowed());
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

    private String createJson() {
        return """
                {
                  "title": "Self introduction",
                  "questionText": "Please introduce yourself.",
                  "helperText": "Use concrete examples.",
                  "category": "SELF_INTRODUCTION",
                  "answerType": "LONG_TEXT",
                  "defaultRequired": true,
                  "defaultMaxLength": 5000
                }
                """;
    }
}
