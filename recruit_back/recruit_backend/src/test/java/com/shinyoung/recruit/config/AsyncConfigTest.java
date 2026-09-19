package com.shinyoung.recruit.config;

import com.shinyoung.recruit.service.MessageDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비동기 실행 활성화 검증. {@code @EnableAsync}가 빠지면 {@code MessageDispatcher.onSendRequested}가
 * 프록시 없이 동기 호출돼 커밋 후 비동기 디스패치가 조용히 무효화되므로 컨텍스트 수준에서 등록 여부를 고정한다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class AsyncConfigTest {

    @Autowired
    private AsyncAnnotationBeanPostProcessor processor;

    @Autowired
    private MessageDispatcher messageDispatcher;

    @Test
    void 비동기_실행이_활성화되어_있다() {
        assertThat(processor).isNotNull();
    }

    @Test
    void 디스패처는_Async_프록시로_주입된다() {
        assertThat(AopUtils.isAopProxy(messageDispatcher)).isTrue();
    }
}
