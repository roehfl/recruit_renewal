package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionOrderRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionReorderRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionUpdateRequest;
import com.shinyoung.recruit.dto.request.QuestionTemplateCreateRequest;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.dto.response.QuestionTemplateResponse;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.exception.InvalidJobPostingQuestionException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.JobPostingQuestionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class JobPostingQuestionServiceTest {

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    @Autowired
    private QuestionTemplateService questionTemplateService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingQuestionRepository jobPostingQuestionRepository;

    @Test
    void create_direct_question_success() {
        Long jobPostingId = createJobPosting();

        JobPostingQuestionResponse response = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));

        assertThat(response.questionId()).isNotNull();
        assertThat(response.questionTemplateId()).isNull();
        assertThat(response.questionText()).isEqualTo("Why do you want to join us?");
        assertThat(response.category()).isEqualTo(QuestionCategory.JOB_SPECIFIC);
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_template_question_copies_snapshot() {
        Long jobPostingId = createJobPosting();
        QuestionTemplateResponse template = createTemplate();

        JobPostingQuestionResponse response = jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                template.templateId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        ));

        assertThat(response.questionTemplateId()).isEqualTo(template.templateId());
        assertThat(response.questionText()).isEqualTo(template.questionText());
        assertThat(response.helperText()).isEqualTo(template.helperText());
        assertThat(response.category()).isEqualTo(template.category());
        assertThat(response.answerType()).isEqualTo(template.answerType());
        assertThat(response.required()).isEqualTo(template.defaultRequired());
        assertThat(response.maxLength()).isEqualTo(template.defaultMaxLength());
    }

    @Test
    void create_template_question_allows_override_snapshot() {
        Long jobPostingId = createJobPosting();
        QuestionTemplateResponse template = createTemplate();

        JobPostingQuestionResponse response = jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                template.templateId(),
                "Override question",
                "Override helper",
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                false,
                10,
                300,
                0
        ));

        assertThat(response.questionText()).isEqualTo("Override question");
        assertThat(response.helperText()).isEqualTo("Override helper");
        assertThat(response.category()).isEqualTo(QuestionCategory.GENERAL);
        assertThat(response.answerType()).isEqualTo(QuestionAnswerType.SHORT_TEXT);
        assertThat(response.required()).isFalse();
        assertThat(response.minLength()).isEqualTo(10);
        assertThat(response.maxLength()).isEqualTo(300);
    }

    @Test
    void inactive_template_cannot_create_question() {
        Long jobPostingId = createJobPosting();
        QuestionTemplateResponse template = createTemplate();
        questionTemplateService.deactivateTemplate(template.templateId());

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                template.templateId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void create_question_fails_when_job_posting_is_not_draft() {
        Long jobPostingId = createJobPosting();
        jobPostingService.publish(jobPostingId);

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0)))
                .isInstanceOf(InvalidJobPostingQuestionException.class);

        jobPostingService.close(jobPostingId);

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0)))
                .isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void get_questions_returns_sort_order_asc_and_inactive_also() {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse second = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(2));
        JobPostingQuestionResponse first = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));
        JobPostingQuestionResponse inactive = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(1));
        jobPostingQuestionService.deactivateQuestion(jobPostingId, inactive.questionId());

        List<JobPostingQuestionResponse> questions = jobPostingQuestionService.getQuestions(jobPostingId);

        assertThat(questions).extracting(JobPostingQuestionResponse::questionId)
                .containsExactly(first.questionId(), inactive.questionId(), second.questionId());
        assertThat(questions).extracting(JobPostingQuestionResponse::active)
                .containsExactly(true, false, true);
    }

    @Test
    void update_question_success_and_template_reference_is_not_changed() {
        Long jobPostingId = createJobPosting();
        QuestionTemplateResponse template = createTemplate();
        JobPostingQuestionResponse created = jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                template.templateId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        ));

        JobPostingQuestionResponse updated = jobPostingQuestionService.updateQuestion(jobPostingId, created.questionId(), updateRequest(0));

        assertThat(updated.questionTemplateId()).isEqualTo(template.templateId());
        assertThat(updated.questionText()).isEqualTo("Updated question");
        assertThat(updated.answerType()).isEqualTo(QuestionAnswerType.SHORT_TEXT);
    }

    @Test
    void update_question_fails_after_publish() {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse created = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));
        jobPostingService.publish(jobPostingId);

        assertThatThrownBy(() -> jobPostingQuestionService.updateQuestion(jobPostingId, created.questionId(), updateRequest(0)))
                .isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void deactivate_question_is_soft_delete() {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse created = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));

        JobPostingQuestionResponse deactivated = jobPostingQuestionService.deactivateQuestion(jobPostingId, created.questionId());

        assertThat(deactivated.active()).isFalse();
        JobPostingQuestion entity = jobPostingQuestionRepository.findById(created.questionId()).orElseThrow();
        assertThat(entity.getActive()).isFalse();
    }

    @Test
    void reorder_questions_success() {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse first = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));
        JobPostingQuestionResponse second = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(1));
        JobPostingQuestionResponse third = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(2));

        List<JobPostingQuestionResponse> reordered = jobPostingQuestionService.reorderQuestions(jobPostingId, new JobPostingQuestionReorderRequest(List.of(
                new JobPostingQuestionOrderRequest(first.questionId(), 2),
                new JobPostingQuestionOrderRequest(second.questionId(), 0),
                new JobPostingQuestionOrderRequest(third.questionId(), 1)
        )));

        assertThat(reordered).extracting(JobPostingQuestionResponse::questionId)
                .containsExactly(second.questionId(), third.questionId(), first.questionId());
        assertThat(reordered).extracting(JobPostingQuestionResponse::sortOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void reorder_request_must_include_all_active_questions() {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse first = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));
        jobPostingQuestionService.createQuestion(jobPostingId, directRequest(1));

        assertThatThrownBy(() -> jobPostingQuestionService.reorderQuestions(jobPostingId, new JobPostingQuestionReorderRequest(List.of(
                new JobPostingQuestionOrderRequest(first.questionId(), 0)
        )))).isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void reorder_duplicate_question_id_or_sort_order_fails() {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse first = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));
        JobPostingQuestionResponse second = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(1));

        assertThatThrownBy(() -> jobPostingQuestionService.reorderQuestions(jobPostingId, new JobPostingQuestionReorderRequest(List.of(
                new JobPostingQuestionOrderRequest(first.questionId(), 0),
                new JobPostingQuestionOrderRequest(first.questionId(), 1)
        )))).isInstanceOf(InvalidJobPostingQuestionException.class);

        assertThatThrownBy(() -> jobPostingQuestionService.reorderQuestions(jobPostingId, new JobPostingQuestionReorderRequest(List.of(
                new JobPostingQuestionOrderRequest(first.questionId(), 0),
                new JobPostingQuestionOrderRequest(second.questionId(), 0)
        )))).isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void reorder_other_job_posting_question_fails() {
        Long firstJobPostingId = createJobPosting();
        Long secondJobPostingId = createJobPosting();
        JobPostingQuestionResponse first = jobPostingQuestionService.createQuestion(firstJobPostingId, directRequest(0));
        JobPostingQuestionResponse other = jobPostingQuestionService.createQuestion(secondJobPostingId, directRequest(0));

        assertThatThrownBy(() -> jobPostingQuestionService.reorderQuestions(firstJobPostingId, new JobPostingQuestionReorderRequest(List.of(
                new JobPostingQuestionOrderRequest(first.questionId(), 0),
                new JobPostingQuestionOrderRequest(other.questionId(), 1)
        )))).isInstanceOf(JobPostingQuestionNotFoundException.class);
    }

    @Test
    void duplicate_sort_order_fails_on_create_and_update() {
        Long jobPostingId = createJobPosting();
        jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));
        JobPostingQuestionResponse second = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(1));

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0)))
                .isInstanceOf(InvalidJobPostingQuestionException.class);
        assertThatThrownBy(() -> jobPostingQuestionService.updateQuestion(jobPostingId, second.questionId(), updateRequest(0)))
                .isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void direct_question_required_fields_missing_fail() {
        Long jobPostingId = createJobPosting();

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                null,
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                true,
                null,
                100,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question",
                null,
                null,
                QuestionAnswerType.SHORT_TEXT,
                true,
                null,
                100,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question",
                null,
                QuestionCategory.GENERAL,
                null,
                true,
                null,
                100,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                null,
                null,
                100,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void length_policy_fail() {
        Long jobPostingId = createJobPosting();

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                true,
                101,
                100,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                true,
                null,
                501,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question",
                null,
                QuestionCategory.GENERAL,
                QuestionAnswerType.LONG_TEXT,
                true,
                null,
                5001,
                0
        ))).isInstanceOf(InvalidJobPostingQuestionException.class);
    }

    @Test
    void not_found_cases_fail() {
        Long jobPostingId = createJobPosting();

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(99999L, directRequest(0)))
                .isInstanceOf(JobPostingNotFoundException.class);
        assertThatThrownBy(() -> jobPostingQuestionService.updateQuestion(jobPostingId, 99999L, updateRequest(0)))
                .isInstanceOf(JobPostingQuestionNotFoundException.class);
    }

    @Test
    void null_request_fails_with_invalid_question_exception() {
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = jobPostingQuestionService.createQuestion(jobPostingId, directRequest(0));

        assertThatThrownBy(() -> jobPostingQuestionService.createQuestion(jobPostingId, null))
                .isInstanceOf(InvalidJobPostingQuestionException.class);
        assertThatThrownBy(() -> jobPostingQuestionService.updateQuestion(jobPostingId, question.questionId(), null))
                .isInstanceOf(InvalidJobPostingQuestionException.class);
        assertThatThrownBy(() -> jobPostingQuestionService.reorderQuestions(jobPostingId, null))
                .isInstanceOf(InvalidJobPostingQuestionException.class);
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

    private JobPostingQuestionCreateRequest directRequest(int sortOrder) {
        return new JobPostingQuestionCreateRequest(
                null,
                "Why do you want to join us?",
                "Focus on role fit.",
                QuestionCategory.JOB_SPECIFIC,
                QuestionAnswerType.LONG_TEXT,
                true,
                null,
                3000,
                sortOrder
        );
    }

    private JobPostingQuestionUpdateRequest updateRequest(int sortOrder) {
        return new JobPostingQuestionUpdateRequest(
                "Updated question",
                "Updated helper",
                QuestionCategory.GENERAL,
                QuestionAnswerType.SHORT_TEXT,
                false,
                null,
                500,
                sortOrder
        );
    }
}
