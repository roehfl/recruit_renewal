package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.dto.response.MessageVariableResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageTemplateService {

    private static final Comparator<MessageTemplate> LIST_ORDER = Comparator
            .comparing(MessageTemplate::getType)
            .thenComparing(MessageTemplate::isDefaultTemplate, Comparator.reverseOrder())
            .thenComparing(MessageTemplate::getName);

    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageRenderer messageRenderer;

    public List<MessageTemplateResponse> getTemplates(MessageType type) {
        List<MessageTemplate> templates = type == null
                ? messageTemplateRepository.findAll()
                : messageTemplateRepository.findByType(type);
        return templates.stream()
                .sorted(LIST_ORDER)
                .map(MessageTemplateResponse::from)
                .toList();
    }

    public MessageTemplateResponse getTemplate(Long templateId) {
        return MessageTemplateResponse.from(findTemplate(templateId));
    }

    public List<MessageVariableResponse> getVariables() {
        return Arrays.stream(MessageVariable.values())
                .map(MessageVariableResponse::from)
                .toList();
    }

    @Transactional
    public MessageTemplateResponse createTemplate(MessageTemplateSaveRequest request) {
        Content content = validate(request);
        if (request.defaultTemplate()) {
            clearDefault(request.type(), null);
        }
        MessageTemplate saved = messageTemplateRepository.save(MessageTemplate.create(
                request.type(), request.name().trim(), request.defaultTemplate(),
                content.mailSubject(), content.mailBody(), content.smsBody()
        ));
        return MessageTemplateResponse.from(saved);
    }

    @Transactional
    public MessageTemplateResponse updateTemplate(Long templateId, MessageTemplateSaveRequest request) {
        MessageTemplate template = findTemplate(templateId);
        Content content = validate(request);
        if (request.defaultTemplate()) {
            clearDefault(request.type(), templateId);
        }
        template.update(
                request.type(), request.name().trim(), request.defaultTemplate(),
                content.mailSubject(), content.mailBody(), content.smsBody()
        );
        // @LastModifiedDate 는 flush 시점에 채워지므로 응답 생성 전에 명시적으로 flush 한다.
        messageTemplateRepository.flush();
        return MessageTemplateResponse.from(template);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        messageTemplateRepository.delete(findTemplate(templateId));
    }

    private MessageTemplate findTemplate(Long templateId) {
        return messageTemplateRepository.findById(templateId)
                .orElseThrow(() -> new MessageTemplateNotFoundException("메시지 템플릿을 찾을 수 없습니다."));
    }

    private void clearDefault(MessageType type, Long exceptTemplateId) {
        messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type).stream()
                .filter(template -> !template.getId().equals(exceptTemplateId))
                .forEach(MessageTemplate::unmarkDefault);
    }

    private Content validate(MessageTemplateSaveRequest request) {
        Content content = new Content(
                blankToNull(request.mailSubject()),
                blankToNull(request.mailBody()),
                blankToNull(request.smsBody())
        );
        if ((content.mailSubject() == null) != (content.mailBody() == null)) {
            throw new InvalidMessageException("메일은 제목과 본문을 함께 입력해야 합니다.");
        }
        if (content.mailSubject() == null && content.smsBody() == null) {
            throw new InvalidMessageException("메일 또는 SMS 내용을 입력해야 합니다.");
        }
        messageRenderer.validateVariables(request.type(), content.mailSubject(), content.mailBody(), content.smsBody());
        return content;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Content(String mailSubject, String mailBody, String smsBody) {
    }
}
