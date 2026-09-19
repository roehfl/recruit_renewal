package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class MessageMailLayoutTest {

    @Autowired
    private MessageMailLayout messageMailLayout;

    @Test
    void 본문을_이스케이프하고_줄바꿈을_br로_바꿔_고정_레이아웃에_넣는다() {
        String html = messageMailLayout.render("[신영증권] 안내", "김민준님 <b>안녕</b>\n둘째 줄");

        assertThat(html).contains("신영증권 채용");
        assertThat(html).contains("김민준님 &lt;b&gt;안녕&lt;/b&gt;");
        assertThat(html).contains("둘째 줄");
        assertThat(html).contains("<br");
        assertThat(html).contains("본 메일은 발신 전용입니다.");
        assertThat(html).doesNotContain("<b>안녕</b>");
    }
}
