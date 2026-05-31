package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 모든 애플리케이션 컨트롤러 엔드포인트에 공통 {@code /api} prefix를 부여한다.
 *
 * <p>컨트롤러마다 매핑을 수정하지 않고 {@code com.shinyoung.recruit.controller} 패키지에 한해
 * prefix를 적용하므로, springdoc(swagger, api-docs)과 H2 콘솔 같은 비-컨트롤러 엔드포인트는
 * 영향을 받지 않는다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api",
                HandlerTypePredicate.forBasePackage("com.shinyoung.recruit.controller")
        );
    }
}
