package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 결과 수신 처리부: 보관 → 반영 시도, 접수 기록 직후 반영, 1분 재시도, 10분 폐기(설계서 7.4). */
class DeliveryReportHandlerTest {

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-19T01:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private final MutableClock clock = new MutableClock();
    private final MessageDispatchRecorder recorder = mock(MessageDispatchRecorder.class);
    private final DeliveryReportBuffer buffer = new DeliveryReportBuffer(clock);
    private final DeliveryReportHandler handler = new DeliveryReportHandler(recorder, buffer, clock);
    private final DeliveryReport report = new DeliveryReport("TX-1", "0000");

    @Test
    void 반영되면_보관하지_않는다() {
        when(recorder.applyReport(report)).thenReturn(true);

        handler.handle(report);

        assertThat(buffer.size()).isZero();
        verify(recorder).applyReport(report);
    }

    @Test
    void 짝이_없으면_보관했다가_접수_기록_직후_반영한다() {
        when(recorder.applyReport(report)).thenReturn(false, true);

        handler.handle(report);
        assertThat(buffer.find("TX-1")).contains(report);

        handler.applyBuffered("TX-1");

        assertThat(buffer.size()).isZero();
        verify(recorder, times(2)).applyReport(report);
    }

    @Test
    void 보관된_결과가_없으면_접수_기록_직후에_아무것도_하지_않는다() {
        handler.applyBuffered("TX-9");

        verifyNoInteractions(recorder);
    }

    @Test
    void 주기_재시도로_반영되면_보관에서_뺀다() {
        when(recorder.applyReport(report)).thenReturn(false, true);
        handler.handle(report);

        handler.retryBuffered();

        assertThat(buffer.size()).isZero();
    }

    @Test
    void 받은_지_10분이_지나도_짝이_없으면_버린다() {
        when(recorder.applyReport(report)).thenReturn(false);
        handler.handle(report);

        clock.advance(Duration.ofMinutes(9));
        handler.retryBuffered();
        assertThat(buffer.size()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(2));
        handler.retryBuffered();
        assertThat(buffer.size()).isZero();
        verify(recorder, times(3)).applyReport(report);
    }

    @Test
    void 반영_중_예외가_나도_던지지_않고_보관해_둔다() {
        when(recorder.applyReport(report)).thenThrow(new IllegalStateException("db down"));

        handler.handle(report);

        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    void 거래_ID가_없는_결과는_무시한다() {
        handler.handle(new DeliveryReport(" ", "0000"));
        handler.handle(null);

        assertThat(buffer.size()).isZero();
        verifyNoInteractions(recorder);
    }
}
