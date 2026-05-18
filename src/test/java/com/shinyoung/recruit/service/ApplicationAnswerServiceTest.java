package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationAnswerReplaceRequest;
import com.shinyoung.recruit.dto.request.ApplicationAnswerRequest;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.response.ApplicationQuestionResponse;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.exception.InvalidApplicationAnswerException;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationAnswerServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationAnswerService applicationAnswerService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationAnswerRepository applicationAnswerRepository;

    @Test
    void get_questions_returns_active_questions_and_existing_answers() {
        Applicant applicant = createApplicant("answer-get", "Answer Get");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse second = createQuestion(jobPostingId, 2, QuestionAnswerType.LONG_TEXT, 3000, true);
        JobPostingQuestionResponse first = createQuestion(jobPostingId, 0, QuestionAnswerType.SHORT_TEXT, 500, false);
        JobPostingQuestionResponse inactive = createQuestion(jobPostingId, 1, QuestionAnswerType.LONG_TEXT, 3000, true);
        jobPostingQuestionService.deactivateQuestion(jobPostingId, inactive.questionId());
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, new ApplicationAnswerReplaceRequest(List.of(
                new ApplicationAnswerRequest(second.questionId(), "second answer")
        )));

        List<ApplicationQuestionResponse> responses = applicationAnswerService.getQuestions(applicant.getId(), applicationId);

        assertThat(responses).extracting(ApplicationQuestionResponse::questionId)
                .containsExactly(first.questionId(), second.questionId());
        assertThat(responses.get(0).answerId()).isNull();
        assertThat(responses.get(0).answerText()).isNull();
        assertThat(responses.get(1).answerId()).isNotNull();
        assertThat(responses.get(1).answerText()).isEqualTo("second answer");
    }

    @Test
    void get_questions_returns_empty_when_no_question() {
        Applicant applicant = createApplicant("answer-empty", "Answer Empty");
        Long jobPostingId = createJobPosting();
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);

        List<ApplicationQuestionResponse> responses = applicationAnswerService.getQuestions(applicant.getId(), applicationId);

        assertThat(responses).isEmpty();
    }

    @Test
    void get_questions_allowed_for_draft_submitted_and_withdrawn() {
        Long jobPostingId = createJobPosting();
        createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(jobPostingId);

        Applicant draftApplicant = createApplicant("answer-status-draft", "Answer Status Draft");
        Long draftApplicationId = createApplication(draftApplicant, jobPostingId);
        assertThat(applicationAnswerService.getQuestions(draftApplicant.getId(), draftApplicationId)).hasSize(1);

        Applicant submittedApplicant = createApplicant("answer-status-submitted", "Answer Status Submitted");
        Long submittedApplicationId = createApplication(submittedApplicant, jobPostingId);
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);
        assertThat(applicationAnswerService.getQuestions(submittedApplicant.getId(), submittedApplicationId)).hasSize(1);

        Applicant withdrawnApplicant = createApplicant("answer-status-withdrawn", "Answer Status Withdrawn");
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, jobPostingId);
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);
        assertThat(applicationAnswerService.getQuestions(withdrawnApplicant.getId(), withdrawnApplicationId)).hasSize(1);
    }

    @Test
    void other_applicants_application_is_hidden_for_get_and_replace() {
        Applicant owner = createApplicant("answer-owner", "Answer Owner");
        Applicant other = createApplicant("answer-other", "Answer Other");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(jobPostingId);
        Long applicationId = createApplication(owner, jobPostingId);

        assertThatThrownBy(() -> applicationAnswerService.getQuestions(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                other.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(question.questionId(), "answer")))
        )).isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void replace_answers_success_and_saves_snapshot() {
        Applicant applicant = createApplicant("answer-save", "Answer Save");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);

        List<ApplicationQuestionResponse> responses = applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(question.questionId(), "my answer")))
        );
        ApplicationAnswer saved = applicationAnswerRepository.findByJobApplicationId(applicationId).get(0);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).answerText()).isEqualTo("my answer");
        assertThat(saved.getQuestionTextSnapshot()).isEqualTo(question.questionText());
        assertThat(saved.getCategorySnapshot()).isEqualTo(question.category());
        assertThat(saved.getAnswerTypeSnapshot()).isEqualTo(question.answerType());
        assertThat(saved.getRequiredSnapshot()).isEqualTo(question.required());
        assertThat(saved.getMaxLengthSnapshot()).isEqualTo(question.maxLength());
        assertThat(saved.getSortOrderSnapshot()).isEqualTo(question.sortOrder());
    }

    @Test
    void replace_allows_null_blank_and_required_blank_in_draft() {
        Applicant applicant = createApplicant("answer-blank", "Answer Blank");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse required = createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        JobPostingQuestionResponse optional = createQuestion(jobPostingId, 1, QuestionAnswerType.SHORT_TEXT, 500, false);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);

        applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, new ApplicationAnswerReplaceRequest(List.of(
                new ApplicationAnswerRequest(required.questionId(), "   "),
                new ApplicationAnswerRequest(optional.questionId(), null)
        )));

        assertThat(applicationAnswerRepository.findByJobApplicationId(applicationId))
                .extracting(ApplicationAnswer::getAnswerText)
                .containsExactly("   ", null);
    }

    @Test
    void empty_answers_delete_existing_answers() {
        Applicant applicant = createApplicant("answer-empty-replace", "Answer Empty Replace");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, new ApplicationAnswerReplaceRequest(List.of(
                new ApplicationAnswerRequest(question.questionId(), "old")
        )));

        List<ApplicationQuestionResponse> responses = applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of())
        );

        assertThat(applicationAnswerRepository.findByJobApplicationId(applicationId)).isEmpty();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).answerId()).isNull();
    }

    @Test
    void replace_deletes_existing_answers_and_keeps_only_new_request() {
        Applicant applicant = createApplicant("answer-replace", "Answer Replace");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse first = createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        JobPostingQuestionResponse second = createQuestion(jobPostingId, 1, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, new ApplicationAnswerReplaceRequest(List.of(
                new ApplicationAnswerRequest(first.questionId(), "old")
        )));

        applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, new ApplicationAnswerReplaceRequest(List.of(
                new ApplicationAnswerRequest(second.questionId(), "new")
        )));

        assertThat(applicationAnswerRepository.findByJobApplicationIdOrderBySortOrderSnapshotAscIdAsc(applicationId))
                .extracting(answer -> answer.getJobPostingQuestion().getId())
                .containsExactly(second.questionId());
    }

    @Test
    void replace_fails_when_application_is_not_writable() {
        Applicant submittedApplicant = createApplicant("answer-submitted", "Answer Submitted");
        Long submittedPostingId = createJobPosting();
        JobPostingQuestionResponse submittedQuestion = createQuestion(submittedPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(submittedPostingId);
        Long submittedApplicationId = createApplication(submittedApplicant, submittedPostingId);
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                submittedApplicant.getId(),
                submittedApplicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(submittedQuestion.questionId(), "answer")))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant withdrawnApplicant = createApplicant("answer-withdrawn", "Answer Withdrawn");
        Long withdrawnPostingId = createJobPosting();
        JobPostingQuestionResponse withdrawnQuestion = createQuestion(withdrawnPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(withdrawnPostingId);
        Long withdrawnApplicationId = createApplication(withdrawnApplicant, withdrawnPostingId);
        jobApplicationService.submit(withdrawnApplicant.getId(), withdrawnApplicationId);
        jobApplicationService.withdraw(withdrawnApplicant.getId(), withdrawnApplicationId);

        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                withdrawnApplicant.getId(),
                withdrawnApplicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(withdrawnQuestion.questionId(), "answer")))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_outside_reception_period_or_unpublished_posting() {
        Applicant beforeApplicant = createApplicant("answer-before", "Answer Before");
        Long beforePostingId = createJobPosting();
        JobPostingQuestionResponse beforeQuestion = createQuestion(beforePostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(beforePostingId);
        Long beforeApplicationId = createApplication(beforeApplicant, beforePostingId);
        setReceptionPeriod(beforePostingId, LocalDateTime.of(2026, 6, 16, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0));

        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                beforeApplicant.getId(),
                beforeApplicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(beforeQuestion.questionId(), "answer")))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant afterApplicant = createApplicant("answer-after", "Answer After");
        Long afterPostingId = createJobPosting();
        JobPostingQuestionResponse afterQuestion = createQuestion(afterPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(afterPostingId);
        Long afterApplicationId = createApplication(afterApplicant, afterPostingId);
        setReceptionPeriod(afterPostingId, LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 14, 18, 0));

        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                afterApplicant.getId(),
                afterApplicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(afterQuestion.questionId(), "answer")))
        )).isInstanceOf(InvalidJobApplicationException.class);

        Applicant draftApplicant = createApplicant("answer-draft-posting", "Answer Draft Posting");
        Long draftPostingId = createJobPosting();
        JobPostingQuestionResponse draftQuestion = createQuestion(draftPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(draftPostingId);
        Long draftApplicationId = createApplication(draftApplicant, draftPostingId);
        setJobPostingStatus(draftPostingId, JobPostingStatus.DRAFT);

        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                draftApplicant.getId(),
                draftApplicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(draftQuestion.questionId(), "answer")))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void replace_fails_when_request_or_question_ids_are_invalid() {
        Applicant applicant = createApplicant("answer-invalid", "Answer Invalid");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse active = createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        JobPostingQuestionResponse inactive = createQuestion(jobPostingId, 1, QuestionAnswerType.LONG_TEXT, 3000, true);
        jobPostingQuestionService.deactivateQuestion(jobPostingId, inactive.questionId());
        Long otherJobPostingId = createJobPosting();
        JobPostingQuestionResponse other = createQuestion(otherJobPostingId, 0, QuestionAnswerType.LONG_TEXT, 3000, true);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);

        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, null))
                .isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(null)
        )).isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(null, "answer")))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(
                        new ApplicationAnswerRequest(active.questionId(), "one"),
                        new ApplicationAnswerRequest(active.questionId(), "two")
                ))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(99999L, "answer")))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(other.questionId(), "answer")))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(inactive.questionId(), "answer")))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
    }

    @Test
    void replace_fails_when_answer_length_exceeds_policy() {
        Applicant applicant = createApplicant("answer-length", "Answer Length");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse maxTen = createQuestion(jobPostingId, 0, QuestionAnswerType.LONG_TEXT, 10, true);
        JobPostingQuestionResponse shortText = createQuestion(jobPostingId, 1, QuestionAnswerType.SHORT_TEXT, 500, true);
        JobPostingQuestionResponse longText = createQuestion(jobPostingId, 2, QuestionAnswerType.LONG_TEXT, 5000, true);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);

        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(maxTen.questionId(), "a".repeat(11))))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(shortText.questionId(), "a".repeat(501))))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
        assertThatThrownBy(() -> applicationAnswerService.replaceAnswers(
                applicant.getId(),
                applicationId,
                new ApplicationAnswerReplaceRequest(List.of(new ApplicationAnswerRequest(longText.questionId(), "a".repeat(5001))))
        )).isInstanceOf(InvalidApplicationAnswerException.class);
    }

    private Applicant createApplicant(String loginId, String applicantName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + applicantName);
        applicant.setUserName(applicantName);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private JobPostingQuestionResponse createQuestion(
            Long jobPostingId,
            int sortOrder,
            QuestionAnswerType answerType,
            int maxLength,
            boolean required
    ) {
        return jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question " + sortOrder,
                "Helper " + sortOrder,
                QuestionCategory.JOB_SPECIFIC,
                answerType,
                required,
                null,
                maxLength,
                sortOrder
        ));
    }

    private void publish(Long jobPostingId) {
        jobPostingService.publish(jobPostingId);
    }

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }

    private void setReceptionPeriod(Long jobPostingId, LocalDateTime start, LocalDateTime end) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        jobPosting.updateBasicInfo(jobPosting.getTitle(), jobPosting.getContentHtml(), start, end);
    }

    private void setJobPostingStatus(Long jobPostingId, JobPostingStatus status) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        ReflectionTestUtils.setField(jobPosting, "status", status);
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
