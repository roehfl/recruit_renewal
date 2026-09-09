package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.entity.QuestionTemplate;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.QuestionTemplateRepository;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionOrderRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionReorderRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionUpdateRequest;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.exception.InvalidJobPostingQuestionException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.JobPostingQuestionNotFoundException;
import com.shinyoung.recruit.exception.QuestionTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingQuestionService {

    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final JobPostingRepository jobPostingRepository;
    private final QuestionTemplateRepository questionTemplateRepository;

    public List<JobPostingQuestionResponse> getQuestions(Long jobPostingId) {
        ensureJobPostingExists(jobPostingId);
        return jobPostingQuestionRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId).stream()
                .map(JobPostingQuestionResponse::from)
                .toList();
    }

    @Transactional
    public JobPostingQuestionResponse createQuestion(Long jobPostingId, JobPostingQuestionCreateRequest request) {
        validateRequestExists(request);
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingDraft(jobPosting);
        validateSortOrderForCreate(jobPostingId, request.sortOrder());

        JobPostingQuestion question;
        if (request.questionTemplateId() == null) {
            QuestionSnapshot snapshot = directSnapshot(request);
            validateQuestionSnapshot(snapshot);
            question = JobPostingQuestion.createDirect(
                    jobPosting,
                    snapshot.questionText(),
                    snapshot.helperText(),
                    snapshot.category(),
                    snapshot.answerType(),
                    snapshot.required(),
                    snapshot.minLength(),
                    snapshot.maxLength(),
                    snapshot.sortOrder()
            );
        } else {
            QuestionTemplate template = findActiveTemplate(request.questionTemplateId());
            QuestionSnapshot snapshot = templateSnapshot(template, request);
            validateQuestionSnapshot(snapshot);
            question = JobPostingQuestion.createFromTemplate(
                    jobPosting,
                    template,
                    snapshot.questionText(),
                    snapshot.helperText(),
                    snapshot.category(),
                    snapshot.answerType(),
                    snapshot.required(),
                    snapshot.minLength(),
                    snapshot.maxLength(),
                    snapshot.sortOrder()
            );
        }

        return JobPostingQuestionResponse.from(jobPostingQuestionRepository.save(question));
    }

    @Transactional
    public JobPostingQuestionResponse updateQuestion(
            Long jobPostingId,
            Long questionId,
            JobPostingQuestionUpdateRequest request
    ) {
        validateRequestExists(request);
        JobPosting jobPosting = findJobPosting(jobPostingId);
        JobPostingQuestion question = findQuestion(jobPostingId, questionId);

        QuestionSnapshot snapshot = new QuestionSnapshot(
                request.questionText(),
                request.helperText(),
                request.category(),
                request.answerType(),
                request.required(),
                request.minLength(),
                request.maxLength(),
                request.sortOrder()
        );
        validateQuestionSnapshot(snapshot);

        if (jobPosting.getStatus() == JobPostingStatus.DRAFT) {
            validateSortOrderForUpdate(jobPostingId, request.sortOrder(), questionId);
        } else {
            validateTextOnlyUpdate(question, snapshot);
        }

        question.update(
                snapshot.questionText(),
                snapshot.helperText(),
                snapshot.category(),
                snapshot.answerType(),
                snapshot.required(),
                snapshot.minLength(),
                snapshot.maxLength(),
                snapshot.sortOrder()
        );
        return JobPostingQuestionResponse.from(question);
    }

    @Transactional
    public List<JobPostingQuestionResponse> reorderQuestions(Long jobPostingId, JobPostingQuestionReorderRequest request) {
        validateRequestExists(request);
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingDraft(jobPosting);

        List<JobPostingQuestion> questions =
                jobPostingQuestionRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId);
        validateReorderRequest(questions, request);

        Map<Long, JobPostingQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(JobPostingQuestion::getId, question -> question));
        for (JobPostingQuestionOrderRequest item : request.questions()) {
            questionMap.get(item.questionId()).changeOrder(item.sortOrder());
        }

        return jobPostingQuestionRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId).stream()
                .map(JobPostingQuestionResponse::from)
                .toList();
    }

    /**
     * 공고 질문을 삭제한다. 질문 편집은 DRAFT 공고에서만 허용되므로(아래 {@code validateJobPostingDraft})
     * 이 시점에는 지원자 답변이 존재할 수 없어 ApplicationAnswer FK 를 건드리지 않는다.
     */
    @Transactional
    public void deleteQuestion(Long jobPostingId, Long questionId) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingDraft(jobPosting);
        JobPostingQuestion question = findQuestion(jobPostingId, questionId);

        jobPostingQuestionRepository.delete(question);
    }

    private void ensureJobPostingExists(Long jobPostingId) {
        if (!jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("JobPosting not found. id=" + jobPostingId);
        }
    }

    private JobPosting findJobPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("JobPosting not found. id=" + jobPostingId));
    }

    private JobPostingQuestion findQuestion(Long jobPostingId, Long questionId) {
        return jobPostingQuestionRepository.findByIdAndJobPostingId(questionId, jobPostingId)
                .orElseThrow(() -> new JobPostingQuestionNotFoundException("JobPostingQuestion not found. id=" + questionId));
    }

    private QuestionTemplate findActiveTemplate(Long templateId) {
        QuestionTemplate template = questionTemplateRepository.findById(templateId)
                .orElseThrow(() -> new QuestionTemplateNotFoundException("QuestionTemplate not found. id=" + templateId));
        if (!Boolean.TRUE.equals(template.getActive())) {
            throw new InvalidJobPostingQuestionException("Inactive question template cannot be used.");
        }
        return template;
    }

    private void validateJobPostingDraft(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.DRAFT) {
            throw new InvalidJobPostingQuestionException("Question configuration is allowed only for DRAFT JobPosting.");
        }
    }

    /**
     * 발행된 공고에서는 문구(questionText/helperText)만 고칠 수 있다. 오타 수정을 허용하되
     * 답변 정책(answerType/required/minLength/maxLength)과 순서를 바꾸면 이미 작성된 답변이
     * 소급해서 정책 위반 상태가 되므로 막는다.
     *
     * <p>이미 답변한 지원자의 관리자 화면·PDF 는 답변 시점 스냅샷을 보여주므로 문구 수정이 반영되지 않는다.
     * 지원자가 본 문구를 그대로 보존하기 위한 의도된 동작이다.
     */
    private void validateTextOnlyUpdate(JobPostingQuestion question, QuestionSnapshot snapshot) {
        boolean policyChanged = question.getCategory() != snapshot.category()
                || question.getAnswerType() != snapshot.answerType()
                || !Objects.equals(question.getRequired(), snapshot.required())
                || !Objects.equals(question.getMinLength(), snapshot.minLength())
                || !Objects.equals(question.getMaxLength(), snapshot.maxLength())
                || !Objects.equals(question.getSortOrder(), snapshot.sortOrder());
        if (policyChanged) {
            throw new InvalidJobPostingQuestionException(
                    "Only question text can be changed after the job posting is published.");
        }
    }

    private void validateRequestExists(Object request) {
        if (request == null) {
            throw new InvalidJobPostingQuestionException("Request is required.");
        }
    }

    private QuestionSnapshot directSnapshot(JobPostingQuestionCreateRequest request) {
        return new QuestionSnapshot(
                request.questionText(),
                request.helperText(),
                request.category(),
                request.answerType(),
                request.required(),
                request.minLength(),
                request.maxLength(),
                request.sortOrder()
        );
    }

    private QuestionSnapshot templateSnapshot(QuestionTemplate template, JobPostingQuestionCreateRequest request) {
        return new QuestionSnapshot(
                request.questionText() == null ? template.getQuestionText() : request.questionText(),
                request.helperText() == null ? template.getHelperText() : request.helperText(),
                request.category() == null ? template.getCategory() : request.category(),
                request.answerType() == null ? template.getAnswerType() : request.answerType(),
                request.required() == null ? template.getDefaultRequired() : request.required(),
                request.minLength(),
                request.maxLength() == null ? template.getDefaultMaxLength() : request.maxLength(),
                request.sortOrder()
        );
    }

    private void validateQuestionSnapshot(QuestionSnapshot snapshot) {
        if (snapshot.questionText() == null || snapshot.questionText().isBlank()) {
            throw new InvalidJobPostingQuestionException("Question text is required.");
        }
        if (snapshot.questionText().length() > 2000) {
            throw new InvalidJobPostingQuestionException("Question text must be 2000 characters or less.");
        }
        if (snapshot.category() == null) {
            throw new InvalidJobPostingQuestionException("Question category is required.");
        }
        if (snapshot.answerType() == null) {
            throw new InvalidJobPostingQuestionException("Question answer type is required.");
        }
        if (snapshot.required() == null) {
            throw new InvalidJobPostingQuestionException("Question required flag is required.");
        }
        if (snapshot.sortOrder() == null || snapshot.sortOrder() < 0) {
            throw new InvalidJobPostingQuestionException("Question sort order must be greater than or equal to 0.");
        }
        validateLength(snapshot.answerType(), snapshot.minLength(), snapshot.maxLength());
    }

    private void validateLength(QuestionAnswerType answerType, Integer minLength, Integer maxLength) {
        if (maxLength == null || maxLength < 1) {
            throw new InvalidJobPostingQuestionException("Max length must be greater than or equal to 1.");
        }
        if (minLength != null && minLength < 0) {
            throw new InvalidJobPostingQuestionException("Min length must be greater than or equal to 0.");
        }
        if (minLength != null && minLength > maxLength) {
            throw new InvalidJobPostingQuestionException("Min length cannot be greater than max length.");
        }
        if (answerType == QuestionAnswerType.SHORT_TEXT && maxLength > QuestionTemplateService.SHORT_TEXT_MAX_LENGTH) {
            throw new InvalidJobPostingQuestionException("SHORT_TEXT max length must be 500 or less.");
        }
        if (answerType == QuestionAnswerType.LONG_TEXT && maxLength > QuestionTemplateService.LONG_TEXT_MAX_LENGTH) {
            throw new InvalidJobPostingQuestionException("LONG_TEXT max length must be 5000 or less.");
        }
    }

    private void validateSortOrderForCreate(Long jobPostingId, Integer sortOrder) {
        if (jobPostingQuestionRepository.existsByJobPostingIdAndSortOrder(jobPostingId, sortOrder)) {
            throw new InvalidJobPostingQuestionException("Question sort order already exists.");
        }
    }

    private void validateSortOrderForUpdate(Long jobPostingId, Integer sortOrder, Long questionId) {
        if (jobPostingQuestionRepository.existsByJobPostingIdAndSortOrderAndIdNot(
                jobPostingId,
                sortOrder,
                questionId
        )) {
            throw new InvalidJobPostingQuestionException("Question sort order already exists.");
        }
    }

    private void validateReorderRequest(List<JobPostingQuestion> questions, JobPostingQuestionReorderRequest request) {
        if (request == null || request.questions() == null || request.questions().isEmpty()) {
            throw new InvalidJobPostingQuestionException("Question reorder items are required.");
        }

        Map<Long, JobPostingQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(JobPostingQuestion::getId, question -> question));
        Set<Long> requestedIds = new HashSet<>();
        Set<Integer> requestedOrders = new HashSet<>();
        Map<Long, Integer> idCounts = new HashMap<>();

        for (JobPostingQuestionOrderRequest item : request.questions()) {
            if (item == null || item.questionId() == null) {
                throw new InvalidJobPostingQuestionException("Question id is required.");
            }
            if (item.sortOrder() == null || item.sortOrder() < 0) {
                throw new InvalidJobPostingQuestionException("Question sort order must be greater than or equal to 0.");
            }
            idCounts.merge(item.questionId(), 1, Integer::sum);
            if (!requestedOrders.add(item.sortOrder())) {
                throw new InvalidJobPostingQuestionException("Question sort order is duplicated.");
            }
            if (!questionMap.containsKey(item.questionId())) {
                throw new JobPostingQuestionNotFoundException("JobPostingQuestion not found. id=" + item.questionId());
            }
            requestedIds.add(item.questionId());
        }

        boolean hasDuplicatedId = idCounts.values().stream().anyMatch(count -> count > 1);
        if (hasDuplicatedId) {
            throw new InvalidJobPostingQuestionException("Question id is duplicated.");
        }
        if (requestedIds.size() != questions.size()) {
            throw new InvalidJobPostingQuestionException("Reorder request must include all questions.");
        }
    }

    private record QuestionSnapshot(
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean required,
            Integer minLength,
            Integer maxLength,
            Integer sortOrder
    ) {
    }
}
