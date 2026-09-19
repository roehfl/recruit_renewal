package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 메시지 디스패처(@Async)를 켠다. 실행기는 스프링 부트 기본 applicationTaskExecutor 를 쓴다.
 * 별도 Executor 빈을 만들면 부트 기본 실행기가 빠져 스트리밍 응답 등에 영향이 있으니 만들지 않는다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
