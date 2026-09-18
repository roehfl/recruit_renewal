package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Notice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeDetailResponseTest {

    @Test
    void 공지_본문의_스크립트와_이벤트_핸들러를_제거하고_서식은_유지한다() {
        Notice notice = Notice.create(
                "공지",
                "<p>안내 <b>본문</b></p><script>alert(1)</script>"
                        + "<img src=\"https://example.com/a.png\" onerror=\"alert(1)\">"
                        + "<a href=\"javascript:alert(1)\">링크</a>",
                false);

        String contentHtml = NoticeDetailResponse.from(notice).contentHtml();

        assertThat(contentHtml).contains("<p>안내 <b>본문</b></p>");
        assertThat(contentHtml).contains("<img src=\"https://example.com/a.png\">");
        assertThat(contentHtml).doesNotContain("<script", "onerror", "javascript:");
    }

    @Test
    void 공지_본문이_null이면_null을_반환한다() {
        Notice notice = Notice.create("공지", null, false);

        assertThat(NoticeDetailResponse.from(notice).contentHtml()).isNull();
    }
}
