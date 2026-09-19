package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.request.MessageContentRequest;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.request.MessageTestSendRequest;
import com.shinyoung.recruit.dto.request.MessageTesterRequest;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.dto.response.MessageTargetRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResultResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 테스트 발송(동기 접수)과 실제 발송 접수(비동기 디스패치)를 처리한다(설계서 7절).
 * 둘 다 응답은 솔루션 접수 결과이고 최종 결과는 발송 결과로 나중에 온다(7.4).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MessageSendService {

    private static final String TEST_PREFIX = "[테스트] ";
    private static final int CONDITION_SUMMARY_MAX_LENGTH = 200;
    private static final int OVER_LIMIT_IDS_SHOWN = 5;

    private final MessageTargetService messageTargetService;
    private final MessageRenderer messageRenderer;
    private final MessageDispatcher messageDispatcher;
    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageSendRepository messageSendRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final CurrentEmployeeService currentEmployeeService;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageProperties messageProperties;
    private final Clock clock;

    public MessageSendResultResponse send(MessageSendRequest request, CustomUserDetails userDetails) {
        String senderLoginId = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        MessageContentRequest content = request.content();
        validateContent(request.type(), content);
        MessageTargetCondition condition = request.toCondition();

        Set<Long> requested = new LinkedHashSet<>(request.applicationIds());
        List<MessageTargetRecipientResponse> recipients = messageTargetService.getTargets(condition).recipients().stream()
                .filter(recipient -> requested.contains(recipient.applicationId()))
                .toList();
        if (recipients.isEmpty()) {
            throw new InvalidMessageException("보낼 수신자가 없습니다. 대상자를 다시 조회하세요.");
        }
        if (recipients.size() > messageProperties.getMaxRecipients()) {
            throw new InvalidMessageException(
                    String.format("한 번에 최대 %,d명까지 보낼 수 있습니다.", messageProperties.getMaxRecipients()));
        }

        List<Plan> plans = recipients.stream()
                .map(recipient -> plan(content, recipient.variables(), "", recipient.email(), recipient.phone(),
                        recipient.mailAvailable(), recipient.smsAvailable()))
                .toList();
        requireSmsWithinLimit(plans, recipients.stream().map(recipient -> String.valueOf(recipient.applicationId())).toList());
        requireDeliverable(plans);

        MessageSend send = messageSendRepository.save(
                newSend(condition, false, content, senderLoginId, userDetails.getName(), recipients.size()));
        List<DeliveryItem> items = new ArrayList<>();
        for (int index = 0; index < recipients.size(); index++) {
            MessageTargetRecipientResponse target = recipients.get(index);
            Plan plan = plans.get(index);
            MessageRecipient recipient = messageRecipientRepository.save(plan.toRecipient(
                    send, jobApplicationRepository.getReferenceById(target.applicationId()),
                    target.name(), target.email(), target.phone()));
            items.addAll(plan.items(recipient.getId()));
        }
        eventPublisher.publishEvent(new MessageSendRequestedEvent(send.getId(), items));
        return new MessageSendResultResponse(send.getId(), MessageSendStatus.SENDING, recipients.size(),
                requested.size() - recipients.size());
    }

    public MessageTestSendResponse testSend(MessageTestSendRequest request, CustomUserDetails userDetails) {
        String senderLoginId = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        MessageContentRequest content = request.content();
        validateContent(request.type(), content);
        validateTesters(request.testers());
        MessageTargetCondition condition = request.toCondition();

        MessageTargetRecipientResponse preview = messageTargetService.getTargets(condition).recipients().stream()
                .filter(recipient -> recipient.applicationId().equals(request.previewApplicationId()))
                .findFirst()
                .orElseThrow(() -> new InvalidMessageException("미리보기 대상이 현재 조건의 대상자가 아닙니다. 대상자를 다시 조회하세요."));

        List<MessageTesterRequest> testers = request.testers();
        List<Plan> plans = testers.stream()
                .map(tester -> plan(content, preview.variables(), TEST_PREFIX, tester.email(), tester.phone(),
                        MessageContacts.isValidEmail(tester.email()), MessageContacts.isValidPhone(tester.phone())))
                .toList();
        requireTestSmsWithinLimit(plans, preview.applicationId());
        requireDeliverable(plans);

        MessageSend send = messageSendRepository.save(
                newSend(condition, true, content, senderLoginId, userDetails.getName(), testers.size()));
        List<DeliveryItem> items = new ArrayList<>();
        for (int index = 0; index < testers.size(); index++) {
            MessageTesterRequest tester = testers.get(index);
            MessageRecipient recipient = messageRecipientRepository.save(
                    plans.get(index).toRecipient(send, null, tester.name(), tester.email(), tester.phone()));
            items.addAll(plans.get(index).items(recipient.getId()));
        }
        messageDispatcher.dispatch(items);

        // 먼저 도착한 발송 결과를 반영하면(bulk update) 영속성 컨텍스트가 비워지므로 응답은 수신자를 다시 읽어 만든다.
        List<MessageTestSendResultResponse> results = new ArrayList<>();
        for (MessageRecipient recipient : messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId())) {
            results.add(new MessageTestSendResultResponse(recipient.getRecipientName(), MessageChannel.MAIL,
                    recipient.getMailStatus(), recipient.getMailFailureReason()));
            results.add(new MessageTestSendResultResponse(recipient.getRecipientName(), MessageChannel.SMS,
                    recipient.getSmsStatus(), recipient.getSmsFailureReason()));
        }
        return new MessageTestSendResponse(send.getId(), results);
    }

    private void validateContent(MessageType type, MessageContentRequest content) {
        boolean mail = Boolean.TRUE.equals(content.mailEnabled());
        boolean sms = Boolean.TRUE.equals(content.smsEnabled());
        if (!mail && !sms) {
            throw new InvalidMessageException("메일이나 SMS 중 하나 이상 켜야 합니다.");
        }
        if (mail && (isBlank(content.mailSubject()) || isBlank(content.mailBody()))) {
            throw new InvalidMessageException("메일 제목과 본문을 입력해야 합니다.");
        }
        if (sms && isBlank(content.smsBody())) {
            throw new InvalidMessageException("SMS 내용을 입력해야 합니다.");
        }
        messageRenderer.validateVariables(type,
                mail ? content.mailSubject() : null,
                mail ? content.mailBody() : null,
                sms ? content.smsBody() : null);
    }

    private static void validateTesters(List<MessageTesterRequest> testers) {
        for (MessageTesterRequest tester : testers) {
            boolean hasEmail = !isBlank(tester.email());
            boolean hasPhone = !isBlank(tester.phone());
            if (!hasEmail && !hasPhone) {
                throw new InvalidMessageException("테스트 수신자는 이메일이나 휴대폰 중 하나 이상 입력해야 합니다.");
            }
            if ((hasEmail && !MessageContacts.isValidEmail(tester.email()))
                    || (hasPhone && !MessageContacts.isValidPhone(tester.phone()))) {
                throw new InvalidMessageException("테스트 수신자 연락처 형식이 올바르지 않습니다.");
            }
        }
    }

    private Plan plan(MessageContentRequest content, Map<String, String> variables, String prefix,
                      String email, String phone, boolean mailAvailable, boolean smsAvailable) {
        MessageDeliveryStatus mailStatus;
        String mailReason = null;
        String subject = null;
        String mailBody = null;
        if (!Boolean.TRUE.equals(content.mailEnabled())) {
            mailStatus = MessageDeliveryStatus.SKIPPED;
            mailReason = MessageContacts.CHANNEL_OFF;
        } else if (!mailAvailable) {
            mailStatus = MessageDeliveryStatus.SKIPPED;
            mailReason = MessageContacts.skipReason(email);
        } else {
            mailStatus = MessageDeliveryStatus.PENDING;
            subject = prefix + messageRenderer.render(content.mailSubject(), variables);
            mailBody = messageRenderer.render(content.mailBody(), variables);
        }

        MessageDeliveryStatus smsStatus;
        String smsReason = null;
        String smsBody = null;
        SmsKind smsKind = null;
        boolean smsTooLong = false;
        if (!Boolean.TRUE.equals(content.smsEnabled())) {
            smsStatus = MessageDeliveryStatus.SKIPPED;
            smsReason = MessageContacts.CHANNEL_OFF;
        } else if (!smsAvailable) {
            smsStatus = MessageDeliveryStatus.SKIPPED;
            smsReason = MessageContacts.skipReason(phone);
        } else {
            smsStatus = MessageDeliveryStatus.PENDING;
            smsBody = prefix + messageRenderer.render(content.smsBody(), variables);
            smsKind = messageRenderer.smsKindOf(messageRenderer.smsByteLength(smsBody)).orElse(null);
            smsTooLong = smsKind == null;
        }
        return new Plan(mailStatus, mailReason, subject, mailBody, MessageContacts.normalizeEmail(email),
                smsStatus, smsReason, smsBody, smsKind, MessageContacts.normalizePhone(phone), smsTooLong);
    }

    private static void requireSmsWithinLimit(List<Plan> plans, List<String> labels) {
        List<String> overLimit = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            if (plans.get(index).smsTooLong()) {
                overLimit.add(labels.get(index));
            }
        }
        if (overLimit.isEmpty()) {
            return;
        }
        String shown = overLimit.stream().limit(OVER_LIMIT_IDS_SHOWN).collect(Collectors.joining(", "));
        String more = overLimit.size() > OVER_LIMIT_IDS_SHOWN ? " 등" : "";
        throw new InvalidMessageException(String.format(
                "SMS가 2,000byte를 넘는 수신자가 %d명 있습니다(수험번호 %s%s).", overLimit.size(), shown, more));
    }

    /** 테스터는 모두 같은 미리보기 대상의 내용을 받으므로 초과 원인은 미리보기 대상이다. 테스터 이름은 메시지에 넣지 않는다. */
    private static void requireTestSmsWithinLimit(List<Plan> plans, Long previewApplicationId) {
        if (plans.stream().anyMatch(Plan::smsTooLong)) {
            throw new InvalidMessageException("SMS가 2,000byte를 넘습니다(미리보기 수험번호 " + previewApplicationId + ").");
        }
    }

    private static void requireDeliverable(List<Plan> plans) {
        if (plans.stream().noneMatch(Plan::deliverable)) {
            throw new InvalidMessageException("보낼 수 있는 연락처가 없습니다.");
        }
    }

    private MessageSend newSend(MessageTargetCondition condition, boolean test, MessageContentRequest content,
                                String senderLoginId, String senderName, int recipientCount) {
        boolean mail = Boolean.TRUE.equals(content.mailEnabled());
        boolean sms = Boolean.TRUE.equals(content.smsEnabled());
        Stage stage = condition.stageId() == null || condition.type() == MessageType.DEADLINE_REMINDER
                ? null
                : stageRepository.findById(condition.stageId()).orElse(null);
        String templateName = content.templateId() == null
                ? null
                : messageTemplateRepository.findById(content.templateId()).map(MessageTemplate::getName).orElse(null);
        return MessageSend.create(
                condition.type(), test, jobPostingRepository.getReferenceById(condition.jobPostingId()), stage,
                conditionSummary(condition, stage), content.templateId(), templateName,
                mail, sms,
                mail ? content.mailSubject() : null, mail ? content.mailBody() : null, sms ? content.smsBody() : null,
                senderLoginId, senderName, recipientCount, LocalDateTime.now(clock));
    }

    private static String conditionSummary(MessageTargetCondition condition, Stage stage) {
        List<String> parts = new ArrayList<>();
        if (stage != null) {
            parts.add(stage.getStageName());
        }
        switch (condition.type()) {
            case RESULT_ANNOUNCEMENT -> parts.add(resultLabel(condition));
            case DEADLINE_REMINDER -> parts.add("작성 중 지원서");
            case INTERVIEW_SCHEDULE, INTERVIEW_NOTICE -> parts.add(
                    isBlank(condition.interviewGroup()) ? "전체 조" : MessageVariableFormatter.groupLabel(condition.interviewGroup().trim()));
            case FREE -> {
                if (stage != null) {
                    parts.add(resultLabel(condition));
                }
                parts.add(applicationStatusLabel(condition.applicationStatus()));
            }
        }
        String summary = String.join(" · ", parts);
        return summary.length() <= CONDITION_SUMMARY_MAX_LENGTH ? summary : summary.substring(0, CONDITION_SUMMARY_MAX_LENGTH);
    }

    private static String resultLabel(MessageTargetCondition condition) {
        return condition.resultStatus() == null ? "결과 전체" : StageResultStatusLabels.label(condition.resultStatus());
    }

    private static String applicationStatusLabel(JobApplicationStatus status) {
        if (status == null) {
            return "작성 중+제출";
        }
        return status == JobApplicationStatus.SUBMITTED ? "제출 완료" : "작성 중";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 수신자 1명의 채널별 판정과 치환 결과. */
    private record Plan(
            MessageDeliveryStatus mailStatus, String mailReason, String mailSubject, String mailBody, String mailTo,
            MessageDeliveryStatus smsStatus, String smsReason, String smsBody, SmsKind smsKind, String smsTo,
            boolean smsTooLong
    ) {
        boolean deliverable() {
            return mailStatus == MessageDeliveryStatus.PENDING || smsStatus == MessageDeliveryStatus.PENDING;
        }

        MessageRecipient toRecipient(MessageSend send, JobApplication application,
                                     String name, String email, String phone) {
            return MessageRecipient.create(send, application, name, email, phone,
                    mailStatus, mailReason, smsStatus, smsReason, smsKind);
        }

        List<DeliveryItem> items(Long recipientId) {
            List<DeliveryItem> items = new ArrayList<>(2);
            if (mailStatus == MessageDeliveryStatus.PENDING) {
                items.add(new DeliveryItem(recipientId, MessageChannel.MAIL, mailTo, mailSubject, mailBody, null));
            }
            if (smsStatus == MessageDeliveryStatus.PENDING) {
                items.add(new DeliveryItem(recipientId, MessageChannel.SMS, smsTo, null, smsBody, smsKind));
            }
            return items;
        }
    }
}
