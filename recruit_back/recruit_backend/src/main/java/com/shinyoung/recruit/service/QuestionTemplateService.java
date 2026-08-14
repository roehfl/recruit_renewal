package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.QuestionTemplate;
import com.shinyoung.recruit.domain.repository.QuestionTemplateRepository;
import com.shinyoung.recruit.dto.request.QuestionTemplateCreateRequest;
import com.shinyoung.recruit.dto.request.QuestionTemplateUpdateRequest;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.QuestionTemplateResponse;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.exception.InvalidQuestionTemplateException;
import com.shinyoung.recruit.exception.QuestionTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionTemplateService {

    static final int SHORT_TEXT_MAX_LENGTH = 500;
    static final int LONG_TEXT_MAX_LENGTH = 5000;

    private final QuestionTemplateRepository questionTemplateRepository;

    public PageResponse<QuestionTemplateResponse> getTemplates(Boolean active, int page, int size) {
        validatePageRequest(page, size);

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<QuestionTemplateResponse> templates = (active == null
                ? questionTemplateRepository.findAll(pageRequest)
                : questionTemplateRepository.findByActive(active, pageRequest))
                .map(QuestionTemplateResponse::from);
        return PageResponse.from(templates);
    }

    public QuestionTemplateResponse getTemplate(Long templateId) {
        return QuestionTemplateResponse.from(findTemplate(templateId));
    }

    @Transactional
    public QuestionTemplateResponse createTemplate(QuestionTemplateCreateRequest request) {
        validateRequestExists(request);
        validateTemplateRequest(
                request.title(),
                request.questionText(),
                request.helperText(),
                request.category(),
                request.answerType(),
                request.defaultRequired(),
                request.defaultMaxLength()
        );

        QuestionTemplate saved = questionTemplateRepository.save(QuestionTemplate.create(
                request.title(),
                request.questionText(),
                request.helperText(),
                request.category(),
                request.answerType(),
                request.defaultRequired(),
                request.defaultMaxLength()
        ));
        return QuestionTemplateResponse.from(saved);
    }

    @Transactional
    public QuestionTemplateResponse updateTemplate(Long templateId, QuestionTemplateUpdateRequest request) {
        validateRequestExists(request);
        QuestionTemplate template = findTemplate(templateId);
        validateTemplateRequest(
                request.title(),
                request.questionText(),
                request.helperText(),
                request.category(),
                request.answerType(),
                request.defaultRequired(),
                request.defaultMaxLength()
        );

        template.update(
                request.title(),
                request.questionText(),
                request.helperText(),
                request.category(),
                request.answerType(),
                request.defaultRequired(),
                request.defaultMaxLength()
        );
        return QuestionTemplateResponse.from(template);
    }

    @Transactional
    public QuestionTemplateResponse deactivateTemplate(Long templateId) {
        QuestionTemplate template = findTemplate(templateId);
        template.deactivate();
        return QuestionTemplateResponse.from(template);
    }

    private QuestionTemplate findTemplate(Long templateId) {
        return questionTemplateRepository.findById(templateId)
                .orElseThrow(() -> new QuestionTemplateNotFoundException("QuestionTemplate not found. id=" + templateId));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidQuestionTemplateException("Page must be greater than or equal to 0.");
        }
        if (size <= 0 || size > 100) {
            throw new InvalidQuestionTemplateException("Size must be between 1 and 100.");
        }
    }

    private void validateRequestExists(Object request) {
        if (request == null) {
            throw new InvalidQuestionTemplateException("Request is required.");
        }
    }

    private void validateTemplateRequest(
            String title,
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean defaultRequired,
            Integer defaultMaxLength
    ) {
        if (title == null || title.isBlank()) {
            throw new InvalidQuestionTemplateException("Template title is required.");
        }
        if (title.length() > 200) {
            throw new InvalidQuestionTemplateException("Template title must be 200 characters or less.");
        }
        if (questionText == null || questionText.isBlank()) {
            throw new InvalidQuestionTemplateException("Question text is required.");
        }
        if (questionText.length() > 2000) {
            throw new InvalidQuestionTemplateException("Question text must be 2000 characters or less.");
        }
        if (helperText != null && helperText.length() > 2000) {
            throw new InvalidQuestionTemplateException("Helper text must be 2000 characters or less.");
        }
        if (category == null) {
            throw new InvalidQuestionTemplateException("Question category is required.");
        }
        if (answerType == null) {
            throw new InvalidQuestionTemplateException("Question answer type is required.");
        }
        if (defaultRequired == null) {
            throw new InvalidQuestionTemplateException("Default required flag is required.");
        }
        validateMaxLength(answerType, defaultMaxLength);
    }

    static void validateMaxLength(QuestionAnswerType answerType, Integer maxLength) {
        if (maxLength == null || maxLength < 1) {
            throw new InvalidQuestionTemplateException("Max length must be greater than or equal to 1.");
        }
        if (answerType == QuestionAnswerType.SHORT_TEXT && maxLength > SHORT_TEXT_MAX_LENGTH) {
            throw new InvalidQuestionTemplateException("SHORT_TEXT max length must be 500 or less.");
        }
        if (answerType == QuestionAnswerType.LONG_TEXT && maxLength > LONG_TEXT_MAX_LENGTH) {
            throw new InvalidQuestionTemplateException("LONG_TEXT max length must be 5000 or less.");
        }
    }
}
