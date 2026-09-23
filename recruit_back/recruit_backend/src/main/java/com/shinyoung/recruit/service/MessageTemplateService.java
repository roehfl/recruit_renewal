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
    private static final String VERIFICATION_CODE_TOKEN = "#{" + MessageVariable.VERIFICATION_CODE.getKey() + "}";
    private static final String SYSTEM_DEFAULT_LOCKED = "시스템 기본 템플릿은 기본을 해제할 수 없습니다.";

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
        // 시스템 기본 템플릿은 자동발송이 쓰므로 스스로 기본에서 빠지면 안 된다. 다른 템플릿을 기본으로 지정하면 아래 clearDefault 로 바뀐다.
        if (isSystemDefault(template) && (!request.defaultTemplate() || request.type() != template.getType())) {
            throw new InvalidMessageException(SYSTEM_DEFAULT_LOCKED);
        }
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
        MessageTemplate template = findTemplate(templateId);
        if (isSystemDefault(template)) {
            throw new InvalidMessageException("시스템 기본 템플릿은 삭제할 수 없습니다.");
        }
        messageTemplateRepository.delete(template);
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

    private static boolean isSystemDefault(MessageTemplate template) {
        return template.isDefaultTemplate() && template.getType().isSystem();
    }

    private Content validate(MessageTemplateSaveRequest request) {
        boolean system = request.type().isSystem();
        Content content = new Content(
                blankToNull(request.mailSubject()),
                blankToNull(request.mailBody()),
                system ? null : blankToNull(request.smsBody())
        );
        if (system && (content.mailSubject() == null || content.mailBody() == null)) {
            throw new InvalidMessageException("시스템 자동발송 템플릿은 메일 제목과 본문을 입력해야 합니다.");
        }
        if ((content.mailSubject() == null) != (content.mailBody() == null)) {
            throw new InvalidMessageException("메일은 제목과 본문을 함께 입력해야 합니다.");
        }
        if (content.mailSubject() == null && content.smsBody() == null) {
            throw new InvalidMessageException("메일 또는 SMS 내용을 입력해야 합니다.");
        }
        messageRenderer.validateVariables(request.type(), content.mailSubject(), content.mailBody(), content.smsBody());
        if (MessageVariable.VERIFICATION_CODE.isAllowedFor(request.type())
                && !content.mailSubject().contains(VERIFICATION_CODE_TOKEN)
                && !content.mailBody().contains(VERIFICATION_CODE_TOKEN)) {
            throw new InvalidMessageException("인증 메일에는 #{인증번호}가 있어야 합니다.");
        }
        return content;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Content(String mailSubject, String mailBody, String smsBody) {
    }
}
