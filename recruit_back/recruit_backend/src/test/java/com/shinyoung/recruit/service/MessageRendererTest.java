package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.exception.InvalidMessageException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageRendererTest {

    private final MessageRenderer renderer = new MessageRenderer();

    @Test
    void 종류에_허용된_변수만_있으면_통과한다() {
        assertThatCode(() -> renderer.validateVariables(
                MessageType.INTERVIEW_SCHEDULE,
                "[신영증권] #{이름}님 #{전형명} 안내",
                "일시: #{면접일시}\n도착: #{도착시각}\n장소: #{면접장소}",
                null
        )).doesNotThrowAnyException();
    }

    @Test
    void 변수가_없는_본문은_통과한다() {
        assertThatCode(() -> renderer.validateVariables(MessageType.FREE, "채용 설명회에 초대합니다."))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른_종류의_변수가_있으면_거부한다() {
        assertThatThrownBy(() -> renderer.validateVariables(
                MessageType.RESULT_ANNOUNCEMENT,
                "#{이름}님 면접은 #{면접일시}입니다."
        ))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{면접일시}");
    }

    @Test
    void 없는_키는_중복없이_모두_알려준다() {
        assertThatThrownBy(() -> renderer.validateVariables(
                MessageType.FREE,
                "#{성명} #{이름} #{성명}",
                "#{마감일시}"
        ))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{성명}, #{마감일시}");
    }

    @Test
    void 변수를_값으로_한번만_치환하고_없는_값은_빈_문자열로_둔다() {
        Map<String, String> values = new HashMap<>();
        values.put("이름", "김#{공고명}");
        values.put("공고명", "2026 공채");
        values.put("전형명", null);

        String rendered = renderer.render("#{이름}님 #{공고명} #{전형명}결과 #{도착시각}", values);

        assertThat(rendered).isEqualTo("김#{공고명}님 2026 공채 결과 ");
    }

    @Test
    void 치환_전에_줄바꿈을_LF로_통일한다() {
        assertThat(renderer.render("a\r\nb\rc\nd", Map.of())).isEqualTo("a\nb\nc\nd");
    }

    @Test
    void SMS_byte는_코드포인트_기준으로_ASCII_1_그밖_2로_센다() {
        assertThat(renderer.smsByteLength("abc 123")).isEqualTo(7);
        assertThat(renderer.smsByteLength("신영")).isEqualTo(4);
        assertThat(renderer.smsByteLength("[신영증권] 안내")).isEqualTo(15);
        assertThat(renderer.smsByteLength("😀")).isEqualTo(2);
        assertThat(renderer.smsByteLength("")).isZero();
    }

    @Test
    void SMS_구분은_90byte_이하_SMS_2000byte_이하_LMS_초과는_없음() {
        assertThat(renderer.smsKindOf(90)).contains(SmsKind.SMS);
        assertThat(renderer.smsKindOf(91)).contains(SmsKind.LMS);
        assertThat(renderer.smsKindOf(2000)).contains(SmsKind.LMS);
        assertThat(renderer.smsKindOf(2001)).isEmpty();
    }

    @Test
    void null_본문은_빈_문자열로_치환한다() {
        assertThat(renderer.render(null, Map.of("이름", "김"))).isEmpty();
    }
}
