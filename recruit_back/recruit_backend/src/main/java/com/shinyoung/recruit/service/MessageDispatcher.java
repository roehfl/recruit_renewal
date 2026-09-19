package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 발송 요청이 커밋되면 비동기로 발송 단위마다 게이트웨이를 호출하고 접수 결과를 기록한다(설계서 7.1).
 * 접수된 단위는 기록 직후 먼저 도착해 보관 중인 발송 결과를 반영한다(7.4).
 * 단위 기록(recordUnit·applyBuffered)이 예외로 끝나면 그 단위만 PENDING 으로 남기고 경고 로그를 남긴 뒤
 * 다음 단위를 계속 처리한다(S4. 게이트웨이 호출 자체의 예외는 MessageDeliveryService 가 이미 처리한다).
 */
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    private final MessageDeliveryService messageDeliveryService;
    private final MessageDispatchRecorder messageDispatchRecorder;
    private final DeliveryReportHandler deliveryReportHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSendRequested(MessageSendRequestedEvent event) {
        dispatch(event.items());
    }

    /** 테스트 발송은 이 메서드를 요청 트랜잭션 안에서 동기로 부른다. */
    public void dispatch(List<DeliveryItem> items) {
        for (DeliveryUnit unit : DeliveryUnit.group(items)) {
            GatewayResult result = messageDeliveryService.deliver(unit);
            try {
                messageDispatchRecorder.recordUnit(unit, result);
                if (result.accepted()) {
                    deliveryReportHandler.applyBuffered(result.transactionId());
                }
            } catch (RuntimeException e) {
                log.warn("발송 단위 기록 실패: channel={}, recipients={}, error={}",
                        unit.channel(), unit.recipientIds().size(), e.getClass().getSimpleName());
            }
        }
    }
}
