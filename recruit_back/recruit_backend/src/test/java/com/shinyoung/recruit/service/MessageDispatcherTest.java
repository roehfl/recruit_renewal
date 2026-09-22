package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDispatcherTest {

    private final MessageDeliveryService deliveryService = mock(MessageDeliveryService.class);
    private final MessageDispatchRecorder recorder = mock(MessageDispatchRecorder.class);
    private final DeliveryReportHandler reportHandler = mock(DeliveryReportHandler.class);
    private final MessageDispatcher dispatcher = new MessageDispatcher(deliveryService, recorder, reportHandler);

    @Test
    void 발송_단위마다_전달하고_접수_결과를_기록한_뒤_먼저_온_결과를_반영한다() {
        List<DeliveryItem> items = new ArrayList<>();
        for (long id = 1; id <= 11; id++) {
            items.add(new DeliveryItem(id, MessageChannel.MAIL, "수신자" + id, "u" + id + "@example.com", "공지", "같은 본문", null));
        }
        items.add(new DeliveryItem(1L, MessageChannel.SMS, "수신자1", "01000000001", null, "문자", SmsKind.SMS));
        when(deliveryService.deliver(any())).thenReturn(
                GatewayResult.accepted("TX-1"), GatewayResult.failure("X"), GatewayResult.accepted("TX-3"));
        ch.qos.logback.classic.Logger dispatcherLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MessageDispatcher.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logAppender =
                new ch.qos.logback.core.read.ListAppender<>();
        logAppender.start();
        dispatcherLogger.addAppender(logAppender);

        try {
            dispatcher.dispatch(7L, items);
        } finally {
            dispatcherLogger.detachAppender(logAppender);
        }

        assertThat(logAppender.list).singleElement().extracting(event -> event.getFormattedMessage()).asString()
                .contains("sendId=7 units=3 accepted=2 failed=1 elapsedMs=");
        ArgumentCaptor<DeliveryUnit> units = ArgumentCaptor.forClass(DeliveryUnit.class);
        verify(deliveryService, times(3)).deliver(units.capture());
        assertThat(units.getAllValues()).extracting(DeliveryUnit::channel)
                .containsExactly(MessageChannel.MAIL, MessageChannel.MAIL, MessageChannel.SMS);
        DeliveryUnit unit0 = units.getAllValues().get(0);
        DeliveryUnit unit1 = units.getAllValues().get(1);
        DeliveryUnit unit2 = units.getAllValues().get(2);
        InOrder order = inOrder(deliveryService, recorder, reportHandler);
        order.verify(deliveryService).deliver(unit0);
        order.verify(recorder).recordUnit(unit0, GatewayResult.accepted("TX-1"));
        order.verify(reportHandler).applyBuffered("TX-1");
        order.verify(deliveryService).deliver(unit1);
        order.verify(recorder).recordUnit(unit1, GatewayResult.failure("X"));
        order.verify(deliveryService).deliver(unit2);
        order.verify(recorder).recordUnit(unit2, GatewayResult.accepted("TX-3"));
        order.verify(reportHandler).applyBuffered("TX-3");
        verify(reportHandler, times(2)).applyBuffered(anyString());
    }

    @Test
    void 이벤트를_받으면_같은_흐름으로_보낸다() {
        when(deliveryService.deliver(any())).thenReturn(GatewayResult.accepted("TX-1"));

        dispatcher.onSendRequested(new MessageSendRequestedEvent(3L, List.of(
                new DeliveryItem(1L, MessageChannel.SMS, "수신자1", "01000000001", null, "문자", SmsKind.SMS))));

        verify(recorder).recordUnit(any(), eq(GatewayResult.accepted("TX-1")));
        verify(reportHandler).applyBuffered("TX-1");
    }

    @Test
    void 기록이_예외로_끝나도_다음_단위를_계속_처리한다() {
        List<DeliveryItem> items = List.of(
                new DeliveryItem(1L, MessageChannel.MAIL, "수신자1", "u1@example.com", "공지1", "본문1", null),
                new DeliveryItem(2L, MessageChannel.MAIL, "수신자2", "u2@example.com", "공지2", "본문2", null));
        when(deliveryService.deliver(any())).thenReturn(
                GatewayResult.accepted("TX-1"), GatewayResult.accepted("TX-2"));
        doThrow(new RuntimeException("lock timeout")).doNothing().when(recorder).recordUnit(any(), any());

        dispatcher.dispatch(7L, items);

        verify(deliveryService, times(2)).deliver(any());
        verify(recorder, times(2)).recordUnit(any(), any());
        verify(reportHandler, times(1)).applyBuffered(anyString());
        verify(reportHandler).applyBuffered("TX-2");
    }
}
