package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

/**
 * 치환이 끝난 일반 텍스트 본문을 고정 브랜드 레이아웃(templates/message-mail.html)에 넣는다.
 * 줄마다 th:text 로 이스케이프하고 줄바꿈은 br 로 바꾼다. 링크 자동 변환은 하지 않는다(설계서 6절).
 */
@Component
@RequiredArgsConstructor
public class MessageMailLayout {

    private static final String TEMPLATE_NAME = "message-mail";

    private final TemplateEngine templateEngine;

    public String render(String subject, String body) {
        Context context = new Context(Locale.KOREA);
        context.setVariable("subject", subject);
        context.setVariable("lines", List.of(body.split("\n", -1)));
        return templateEngine.process(TEMPLATE_NAME, context);
    }
}
