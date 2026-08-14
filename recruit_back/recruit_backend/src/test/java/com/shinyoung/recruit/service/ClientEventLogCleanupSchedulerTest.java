package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientEventLogCleanupSchedulerTest {

    @Test
    void 정상_실행시_cleanup을_위임한다() {
        ClientEventLogCleanupService cleanupService = mock(ClientEventLogCleanupService.class);
        when(cleanupService.cleanup()).thenReturn(3);

        new ClientEventLogCleanupScheduler(cleanupService).runCleanup();

        verify(cleanupService).cleanup();
    }

    @Test
    void cleanup_실패는_로그만_남기고_전파하지_않는다() {
        ClientEventLogCleanupService cleanupService = mock(ClientEventLogCleanupService.class);
        when(cleanupService.cleanup()).thenThrow(new IllegalStateException("DB down"));

        assertThatCode(() -> new ClientEventLogCleanupScheduler(cleanupService).runCleanup())
                .doesNotThrowAnyException();
    }
}
