package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.exception.InvalidMessageException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 메시지 본문 규칙. 프론트 views/admin/message/messageRender.ts 와 같은 규칙·같은 테스트 예시를 유지한다.
 */
@Component
public class MessageRenderer {

    public static final int SMS_MAX_BYTES = 90;
    public static final int LMS_MAX_BYTES = 2000;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("#\\{([^}]+)\\}");

    /** 종류에 허용되지 않았거나 없는 변수가 하나라도 있으면 거부한다. null 본문은 건너뛴다. */
    public void validateVariables(MessageType type, String... texts) {
        Set<String> disallowed = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = VARIABLE_PATTERN.matcher(text);
            while (matcher.find()) {
                String key = matcher.group(1);
                boolean allowed = MessageVariable.fromKey(key)
                        .map(variable -> variable.isAllowedFor(type))
                        .orElse(false);
                if (!allowed) {
                    disallowed.add("#{" + key + "}");
                }
            }
        }
        if (!disallowed.isEmpty()) {
            throw new InvalidMessageException("사용할 수 없는 변수: " + String.join(", ", disallowed));
        }
    }

    /** 줄바꿈을 LF로 통일한 뒤 #{키}를 한 번만 치환한다. 값이 없으면 빈 문자열. */
    public String render(String text, Map<String, String> values) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        Matcher matcher = VARIABLE_PATTERN.matcher(normalized);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** 코드포인트 단위로 127 이하 1byte, 그 밖 2byte. `[Web발신]` 머리말은 세지 않는다. */
    public int smsByteLength(String text) {
        return text.codePoints().map(codePoint -> codePoint <= 127 ? 1 : 2).sum();
    }

    /** 90byte 이하 SMS, 2000byte 이하 LMS, 그보다 길면 보낼 수 없어 빈 값. */
    public Optional<SmsKind> smsKindOf(int bytes) {
        if (bytes <= SMS_MAX_BYTES) {
            return Optional.of(SmsKind.SMS);
        }
        if (bytes <= LMS_MAX_BYTES) {
            return Optional.of(SmsKind.LMS);
        }
        return Optional.empty();
    }
}
