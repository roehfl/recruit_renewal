package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.dto.request.ApplicationAnswerReplaceRequest;
import com.shinyoung.recruit.dto.request.ApplicationAnswerRequest;
import com.shinyoung.recruit.dto.response.ApplicationQuestionResponse;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.exception.InvalidApplicationAnswerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationAnswerService {

    private static final int SHORT_TEXT_MAX_LENGTH = 500;
    private static final int LONG_TEXT_MAX_LENGTH = 5000;

    private final ApplicationSectionAccessService sectionAccessService;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;

    public List<ApplicationQuestionResponse> getQuestions(Long applicantId, Long applicationId) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        List<JobPostingQuestion> questions = getActiveQuestions(application);
        return getQuestionResponses(applicationId, questions);
    }

    @Transactional
    public List<ApplicationQuestionResponse> replaceAnswers(
            Long applicantId,
            Long applicationId,
            ApplicationAnswerReplaceRequest request
    ) {
        validateRequest(request);
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);

        List<JobPostingQuestion> activeQuestions = getActiveQuestions(application);
        Map<Long, JobPostingQuestion> questionMap = activeQuestions.stream()
                .collect(Collectors.toMap(JobPostingQuestion::getId, question -> question));
        validateAnswerItems(request.answers(), questionMap);

        applicationAnswerRepository.deleteByJobApplicationId(applicationId);
        applicationAnswerRepository.flush();

        List<ApplicationAnswer> answers = request.answers().stream()
                .map(answer -> ApplicationAnswer.create(
                        application,
                        questionMap.get(answer.questionId()),
                        answer.answerText()
                ))
                .toList();
        applicationAnswerRepository.saveAll(answers);
        applicationAnswerRepository.flush();

        return getQuestionResponses(applicationId, activeQuestions);
    }

    private List<JobPostingQuestion> getActiveQuestions(JobApplication application) {
        return jobPostingQuestionRepository.findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(
                application.getJobPosting().getId()
        );
    }

    private List<ApplicationQuestionResponse> getQuestionResponses(
            Long applicationId,
            List<JobPostingQuestion> questions
    ) {
        Map<Long, ApplicationAnswer> answerMap = applicationAnswerRepository.findByJobApplicationId(applicationId).stream()
                .collect(Collectors.toMap(answer -> answer.getJobPostingQuestion().getId(), answer -> answer));

        return questions.stream()
                .map(question -> ApplicationQuestionResponse.of(question, answerMap.get(question.getId())))
                .toList();
    }

    private void validateRequest(ApplicationAnswerReplaceRequest request) {
        if (request == null || request.answers() == null) {
            throw new InvalidApplicationAnswerException("Answer list is required.");
        }
    }

    private void validateAnswerItems(
            List<ApplicationAnswerRequest> answers,
            Map<Long, JobPostingQuestion> activeQuestionMap
    ) {
        Set<Long> questionIds = new HashSet<>();
        for (ApplicationAnswerRequest answer : answers) {
            validateAnswerItem(answer, activeQuestionMap);
            if (!questionIds.add(answer.questionId())) {
                throw new InvalidApplicationAnswerException("Question id is duplicated.");
            }
        }
    }

    private void validateAnswerItem(
            ApplicationAnswerRequest answer,
            Map<Long, JobPostingQuestion> activeQuestionMap
    ) {
        if (answer == null) {
            throw new InvalidApplicationAnswerException("Answer item is required.");
        }
        if (answer.questionId() == null) {
            throw new InvalidApplicationAnswerException("Question id is required.");
        }

        JobPostingQuestion question = activeQuestionMap.get(answer.questionId());
        if (question == null) {
            throw new InvalidApplicationAnswerException("Question is not active for this job posting.");
        }
        validateAnswerText(answer.answerText(), question);
    }

    private void validateAnswerText(String answerText, JobPostingQuestion question) {
        if (answerText == null) {
            return;
        }
        if (answerText.length() > question.getMaxLength()) {
            throw new InvalidApplicationAnswerException("Answer text exceeds question max length.");
        }
        if (question.getAnswerType() == QuestionAnswerType.SHORT_TEXT && answerText.length() > SHORT_TEXT_MAX_LENGTH) {
            throw new InvalidApplicationAnswerException("SHORT_TEXT answer must be 500 characters or less.");
        }
        if (question.getAnswerType() == QuestionAnswerType.LONG_TEXT && answerText.length() > LONG_TEXT_MAX_LENGTH) {
            throw new InvalidApplicationAnswerException("LONG_TEXT answer must be 5000 characters or less.");
        }
    }
}
