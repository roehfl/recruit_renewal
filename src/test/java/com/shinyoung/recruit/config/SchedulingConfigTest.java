package com.shinyoung.recruit.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄링 활성화 검증(2차 리뷰 Major 1). {@code @EnableScheduling}이 빠지면
 * {@code @Scheduled} cron이 조용히 미동작하므로 컨텍스트 수준에서 등록 여부를 고정한다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class SchedulingConfigTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor processor;

    @Test
    void 스케줄링이_활성화되어_있다() {
        assertThat(processor).isNotNull();
    }

    @Test
    void cleanup_스케줄러의_cron_작업이_등록되어_있다() {
        // ScheduledMethodRunnable.toString() = "선언클래스FQN.메서드명" — 래핑 구현이 바뀌어도
        // 실패 메시지에 실제 등록 목록이 드러나도록 문자열 기술로 검증한다.
        List<String> descriptions = processor.getScheduledTasks().stream()
                .map(scheduledTask -> scheduledTask.getTask().getRunnable())
                .map(runnable -> runnable.getClass().getName() + " :: " + runnable)
                .toList();
        assertThat(descriptions).anySatisfy(description ->
                assertThat(description).contains("ClientEventLogCleanupScheduler"));
    }
}
