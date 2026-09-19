package com.shinyoung.recruit.service;

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
    void 접수_3초_뒤_성공_코드로_결과를_넘긴다() {
        scheduler.schedule("TX-1", List.of("kim@example.com"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(task.capture(), eq(NOW.plusSeconds(3)));
        verifyNoInteractions(handler);

        task.getValue().run();

        verify(handler).handle(new DeliveryReport("TX-1", "0000"));
    }

    @Test
    void 연락처에_fail이_있으면_실패_코드로_넘긴다() {
        scheduler.schedule("TX-2", List.of("ok@example.com", "FAIL.test@example.com"));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(task.capture(), any(Instant.class));
        task.getValue().run();

        verify(handler).handle(new DeliveryReport("TX-2", "9999"));
    }
}
