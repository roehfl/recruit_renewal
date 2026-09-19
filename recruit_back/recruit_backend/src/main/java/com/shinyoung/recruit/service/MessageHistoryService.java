package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientStatusCount;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.condition.MessageHistoryCondition;
import com.shinyoung.recruit.dto.response.MessageChannelCountResponse;
import com.shinyoung.recruit.dto.response.MessageRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageSendNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 발송 이력 조회(설계서 3.2·7.4). 발송 상태·채널별 건수는 저장하지 않고 수신자 채널 상태를 세어 계산한다.
 * 지연(delayed) = 완료가 아니고 요청 후 recruit.message.result-wait-minutes 가 지남. DB 값은 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageHistoryService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_RANGE_DAYS = 30;

    private final MessageSendRepository messageSendRepository;
    private final MessageRecipientRepository messageRecipientRepository;
    private final MessageProperties messageProperties;
    private final Clock clock;

    /** 발송 이력 목록. 기간(발송일, 양끝 포함)을 비우면 종료일 = 오늘, 시작일 = 종료일 - 29일. 최신순. */
    public PageResponse<MessageSendSummaryResponse> search(MessageHistoryCondition condition, int page, int size) {
        validatePaging(page, size);
        LocalDate to = condition.to() != null ? condition.to() : LocalDate.now(clock);
        LocalDate from = condition.from() != null ? condition.from() : to.minusDays(DEFAULT_RANGE_DAYS - 1);
        if (from.isAfter(to)) {
            throw new InvalidMessageException("조회 시작일이 종료일보다 늦습니다.");
        }
        Page<MessageSend> sends = messageSendRepository.search(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                condition.type(), condition.jobPostingId(), condition.test(), PageRequest.of(page, size));
        Map<Long, ChannelTally> tallies = tally(sends.getContent().stream().map(MessageSend::getId).toList());
        LocalDateTime now = LocalDateTime.now(clock);
        return PageResponse.from(sends.map(send ->
                summary(send, tallies.getOrDefault(send.getId(), new ChannelTally()), now)));
    }

    /** 발송 1회의 원문·집계·수신자별 결과(id 순). 연락처는 가리지 않는다. 파기된 수신자는 이름·연락처가 null. */
    public MessageSendDetailResponse detail(Long sendId) {
        MessageSend send = messageSendRepository.findById(sendId)
                .orElseThrow(() -> new MessageSendNotFoundException("발송 기록을 찾을 수 없습니다."));
        List<MessageRecipient> recipients = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(sendId);
        ChannelTally tally = new ChannelTally();
        recipients.forEach(recipient -> tally.add(recipient.getMailStatus(), recipient.getSmsStatus(), 1));
        return MessageSendDetailResponse.of(
                summary(send, tally, LocalDateTime.now(clock)),
                send,
                recipients.stream().map(MessageRecipientResponse::from).toList());
    }

    private MessageSendSummaryResponse summary(MessageSend send, ChannelTally tally, LocalDateTime now) {
        MessageChannelCountResponse mail = tally.mail();
        MessageChannelCountResponse sms = tally.sms();
        MessageSendStatus status = MessageSendStatus.of(
                mail.pending() + sms.pending(), mail.requested() + sms.requested());
        boolean delayed = status != MessageSendStatus.COMPLETED
                && send.getRequestedAt().isBefore(now.minusMinutes(messageProperties.getResultWaitMinutes()));
        return MessageSendSummaryResponse.of(send, mail, sms, status, delayed);
    }

    private Map<Long, ChannelTally> tally(List<Long> sendIds) {
        Map<Long, ChannelTally> tallies = new HashMap<>();
        if (sendIds.isEmpty()) {
            return tallies;
        }
        for (MessageRecipientStatusCount row : messageRecipientRepository.countStatusesByMessageSendIds(sendIds)) {
            tallies.computeIfAbsent(row.messageSendId(), ignored -> new ChannelTally())
                    .add(row.mailStatus(), row.smsStatus(), row.count());
        }
        return tallies;
    }

    private static void validatePaging(int page, int size) {
        if (page < 0) {
            throw new InvalidMessageException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidMessageException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
    }

    /** 발송 1회의 채널별 상태 건수. */
    private static final class ChannelTally {

        private final Map<MessageDeliveryStatus, Long> mailCounts = new EnumMap<>(MessageDeliveryStatus.class);
        private final Map<MessageDeliveryStatus, Long> smsCounts = new EnumMap<>(MessageDeliveryStatus.class);

        void add(MessageDeliveryStatus mailStatus, MessageDeliveryStatus smsStatus, long count) {
            mailCounts.merge(mailStatus, count, Long::sum);
            smsCounts.merge(smsStatus, count, Long::sum);
        }

        MessageChannelCountResponse mail() {
            return MessageChannelCountResponse.of(mailCounts);
        }

        MessageChannelCountResponse sms() {
            return MessageChannelCountResponse.of(smsCounts);
        }
    }
}
