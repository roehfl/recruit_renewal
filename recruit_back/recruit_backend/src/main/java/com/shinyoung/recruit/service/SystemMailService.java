package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 시스템 자동발송 메일 1통(설계서 6.1). 그 종류의 기본 템플릿으로 이력(치환 전 원문)을 저장해 커밋한 뒤
 * {@link MessageDispatcher#dispatch}를 동기로 부르고, 수신자의 메일 상태로 결과를 판정한다.
 *
 * <p>호출자(가입·재발급 요청 스레드, 제출 리스너의 비동기 스레드)는 트랜잭션 밖에서 부른다. 그래서 저장 트랜잭션은
 * 여기서 커밋되고 게이트웨이 호출은 커밋 뒤에 일어난다. 트랜잭션 안에서 부르면(테스트) 그 트랜잭션에 합류한다.
 * 치환 결과(인증번호 등)는 {@link DeliveryItem}에만 담기고 DB·로그에 남지 않는다(설계서 4절).
 */
@Service
@RequiredArgsConstructor
public class SystemMailService {

    private static final Logger log = LoggerFactory.getLogger(SystemMailService.class);

    private final MessageTemplateRepository messageTemplateRepository;
    private final MessageSendRepository messageSendRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final MessageRenderer messageRenderer;
    private final MessageDispatcher messageDispatcher;
    private final MessageProperties messageProperties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * @param variables 종류 전용 변수 값(예: 인증번호, 공고명·제출일시). #{이름}·#{채용사이트}는 비어 있으면 name·설정값으로 채운다.
     * @param jobPosting 공고 없는 발송이면 null
     * @param application 지원서 없는 발송이면 null
     */
    public SystemMailOutcome send(MessageType type, String email, String name, Map<String, String> variables,
                                  JobPosting jobPosting, JobApplication application) {
        Prepared prepared = new TransactionTemplate(transactionManager)
                .execute(status -> prepare(type, email, name, variables, jobPosting, application));
        if (prepared == null) {
            log.warn("시스템 메일 기본 템플릿이 없어 보내지 않습니다: type={}", type);
            return SystemMailOutcome.NO_TEMPLATE;
        }
        messageDispatcher.dispatch(prepared.sendId(), List.of(prepared.item()));
        MessageDeliveryStatus status = messageRecipientRepository.findById(prepared.item().recipientId())
                .map(MessageRecipient::getMailStatus)
                .orElse(MessageDeliveryStatus.FAILED);
        SystemMailOutcome outcome = status == MessageDeliveryStatus.REQUESTED || status == MessageDeliveryStatus.SENT
                ? SystemMailOutcome.ACCEPTED
                : SystemMailOutcome.FAILED;
        log.info("시스템 메일 발송: type={}, to={}, outcome={}", type, MessageContacts.maskEmail(email), outcome);
        return outcome;
    }

    private Prepared prepare(MessageType type, String email, String name, Map<String, String> variables,
                             JobPosting jobPosting, JobApplication application) {
        MessageTemplate template = messageTemplateRepository.findByTypeAndDefaultTemplateTrue(type).stream()
                .findFirst()
                .orElse(null);
        if (template == null) {
            return null;
        }
        MessageSend send = messageSendRepository.save(
                MessageSend.createSystem(type, jobPosting, template, LocalDateTime.now(clock)));
        MessageRecipient recipient = messageRecipientRepository.save(MessageRecipient.create(
                send, application, name, email, null,
                MessageDeliveryStatus.PENDING, null,
                MessageDeliveryStatus.SKIPPED, MessageContacts.CHANNEL_OFF, null));

        Map<String, String> values = new HashMap<>(variables);
        values.putIfAbsent(MessageVariable.NAME.getKey(), Objects.toString(name, ""));
        values.putIfAbsent(MessageVariable.SITE_URL.getKey(), messageProperties.getSiteUrl());
        DeliveryItem item = new DeliveryItem(recipient.getId(), MessageChannel.MAIL, name,
                MessageContacts.normalizeEmail(email),
                messageRenderer.render(template.getMailSubject(), values),
                messageRenderer.render(template.getMailBody(), values),
                null);
        return new Prepared(send.getId(), item);
    }

    private record Prepared(Long sendId, DeliveryItem item) {
    }
}
