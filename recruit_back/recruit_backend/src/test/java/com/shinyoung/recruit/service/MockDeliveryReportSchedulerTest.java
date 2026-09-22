package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MockDeliveryReportSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final DeliveryReportHandler handler = mock(DeliveryReportHandler.class);
    private final MockDeliveryReportScheduler scheduler =
            new MockDeliveryReportScheduler(taskScheduler, handler, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 접수_3초_뒤_수신자마다_성공_코드로_결과를_넘긴다() {
        scheduler.schedule(MessageChannel.MAIL, "TX-1", List.of("kim@example.com", "lee@example.com"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(task.capture(), eq(NOW.plusSeconds(3)));
        verifyNoInteractions(handler);

        task.getValue().run();

        verify(handler).handle(new DeliveryReport(MessageChannel.MAIL, "TX-1", "kim@example.com", "00"));
        verify(handler).handle(new DeliveryReport(MessageChannel.MAIL, "TX-1", "lee@example.com", "00"));
    }

    @Test
    void 연락처에_fail이_있는_수신자만_실패_코드로_넘긴다() {
        scheduler.schedule(MessageChannel.SMS, "TX-2", List.of("01000000000", "010fail0000"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(task.capture(), any(Instant.class));
        task.getValue().run();

        verify(handler).handle(new DeliveryReport(MessageChannel.SMS, "TX-2", "01000000000", "00"));
        verify(handler).handle(new DeliveryReport(MessageChannel.SMS, "TX-2", "010fail0000", "99"));
    }
}
