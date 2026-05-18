package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.QuestionTemplateCreateRequest;
import com.shinyoung.recruit.dto.request.QuestionTemplateUpdateRequest;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.QuestionTemplateResponse;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.exception.InvalidQuestionTemplateException;
import com.shinyoung.recruit.exception.QuestionTemplateNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class QuestionTemplateServiceTest {

    @Autowired
    private QuestionTemplateService questionTemplateService;

    @Test
    void create_template_success() {
        QuestionTemplateResponse response = questionTemplateService.createTemplate(createRequest());

        assertThat(response.templateId()).isNotNull();
        assertThat(response.title()).isEqualTo("Self introduction");
        assertThat(response.category()).isEqualTo(QuestionCategory.SELF_INTRODUCTION);
        assertThat(response.answerType()).isEqualTo(QuestionAnswerType.LONG_TEXT);
        assertThat(response.defaultRequired()).isTrue();
        assertThat(response.defaultMaxLength()).isEqualTo(5000);
        assertThat(response.active()).isTrue();
    }

    @Test
    void get_templates_and_active_filter_success() {
        questionTemplateService.createTemplate(createRequest());
        QuestionTemplateResponse inactive = questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "General question",
                "What value do you care about most?",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                false,
                300
        ));
        questionTemplateService.deactivateTemplate(inactive.templateId());

        PageResponse<QuestionTemplateResponse> all = questionTemplateService.getTemplates(null, 0, 20);
        PageResponse<QuestionTemplateResponse> active = questionTemplateService.getTemplates(true, 0, 20);
        PageResponse<QuestionTemplateResponse> deactivated = questionTemplateService.getTemplates(false, 0, 20);

        assertThat(all.content()).hasSize(2);
        assertThat(active.content()).hasSize(1);
        assertThat(deactivated.content()).hasSize(1);
        assertThat(deactivated.content().get(0).active()).isFalse();
    }

    @Test
    void get_template_success() {
        QuestionTemplateResponse created = questionTemplateService.createTemplate(createRequest());

        QuestionTemplateResponse found = questionTemplateService.getTemplate(created.templateId());

        assertThat(found.templateId()).isEqualTo(created.templateId());
        assertThat(found.questionText()).isEqualTo("Please introduce yourself.");
    }

    @Test
    void update_template_success() {
        QuestionTemplateResponse created = questionTemplateService.createTemplate(createRequest());

        QuestionTemplateResponse updated = questionTemplateService.updateTemplate(created.templateId(), new QuestionTemplateUpdateRequest(
                "Motivation",
                "Why do you want to join us?",
                "Focus on role fit.",
                QuestionCategory.JOB_SPECIFIC,
                QuestionAnswerType.SHORT_TEXT,
                true,
                500
        ));

        assertThat(updated.title()).isEqualTo("Motivation");
        assertThat(updated.answerType()).isEqualTo(QuestionAnswerType.SHORT_TEXT);
        assertThat(updated.defaultMaxLength()).isEqualTo(500);
        assertThat(updated.active()).isTrue();
    }

    @Test
    void deactivate_template_success() {
        QuestionTemplateResponse created = questionTemplateService.createTemplate(createRequest());

        QuestionTemplateResponse deactivated = questionTemplateService.deactivateTemplate(created.templateId());

        assertThat(deactivated.active()).isFalse();
    }

    @Test
    void get_template_fails_when_not_found() {
        assertThatThrownBy(() -> questionTemplateService.getTemplate(99999L))
                .isInstanceOf(QuestionTemplateNotFoundException.class);
    }

    @Test
    void page_request_invalid_values_fail() {
        assertThatThrownBy(() -> questionTemplateService.getTemplates(null, -1, 20))
                .isInstanceOf(InvalidQuestionTemplateException.class);
        assertThatThrownBy(() -> questionTemplateService.getTemplates(null, 0, 0))
                .isInstanceOf(InvalidQuestionTemplateException.class);
        assertThatThrownBy(() -> questionTemplateService.getTemplates(null, 0, 101))
                .isInstanceOf(InvalidQuestionTemplateException.class);
    }

    @Test
    void required_fields_missing_fail() {
        assertThatThrownBy(() -> questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                " ",
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                true,
                100
        ))).isInstanceOf(InvalidQuestionTemplateException.class);

        assertThatThrownBy(() -> questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "Title",
                " ",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                true,
                100
        ))).isInstanceOf(InvalidQuestionTemplateException.class);

        assertThatThrownBy(() -> questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "Title",
                "Question",
                null,
                null,
                QuestionAnswerType.SHORT_TEXT,
                true,
                100
        ))).isInstanceOf(InvalidQuestionTemplateException.class);

        assertThatThrownBy(() -> questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "Title",
                "Question",
                null,
                QuestionCategory.GENERAL,
                null,
                true,
                100
        ))).isInstanceOf(InvalidQuestionTemplateException.class);

        assertThatThrownBy(() -> questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "Title",
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                null,
                100
        ))).isInstanceOf(InvalidQuestionTemplateException.class);
    }

    @Test
    void null_request_fails_with_invalid_template_exception() {
        QuestionTemplateResponse created = questionTemplateService.createTemplate(createRequest());

        assertThatThrownBy(() -> questionTemplateService.createTemplate(null))
                .isInstanceOf(InvalidQuestionTemplateException.class);
        assertThatThrownBy(() -> questionTemplateService.updateTemplate(created.templateId(), null))
                .isInstanceOf(InvalidQuestionTemplateException.class);
    }

    @Test
    void default_max_length_limit_fail() {
        assertThatThrownBy(() -> questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "Short",
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                true,
                501
        ))).isInstanceOf(InvalidQuestionTemplateException.class);

        assertThatThrownBy(() -> questionTemplateService.createTemplate(new QuestionTemplateCreateRequest(
                "Long",
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.LONG_TEXT,
                true,
                5001
        ))).isInstanceOf(InvalidQuestionTemplateException.class);
    }

    private QuestionTemplateCreateRequest createRequest() {
        return new QuestionTemplateCreateRequest(
                "Self introduction",
                "Please introduce yourself.",
                "Use concrete examples.",
                QuestionCategory.SELF_INTRODUCTION,
                QuestionAnswerType.LONG_TEXT,
                true,
                5000
        );
    }
}
