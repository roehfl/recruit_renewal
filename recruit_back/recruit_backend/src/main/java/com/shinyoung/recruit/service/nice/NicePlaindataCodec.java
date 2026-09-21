package com.shinyoung.recruit.service.nice;

import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NICE 체크플러스 평문 직렬화 규격 코덱.
 *
 * <p>형식은 {@code 키바이트길이:키 값바이트길이:값} 을 구분자 없이 이어 붙인 것이다.
 * 예: {@code 7:REQ_SEQ3:abc}
 *
 * <p><b>길이는 문자 수가 아니라 EUC-KR 바이트 길이다.</b> 한글 값에서 갈리며(홍길동 = 6바이트,
 * UTF-8 이면 9), 틀리면 NICE 는 원인을 알려주지 않고 실패 코드만 돌려준다. 벤더 모듈
 * {@code CPClient} 가 문자셋을 {@code "euc-kr"} 로 하드코딩하고 있다(바이트코드 확인, 2026-09-21).
 * 레거시의 {@code getBytes()}(문자셋 미지정)가 동작한 것은 한국어 Windows 서버의 플랫폼 기본값이
 * EUC-KR 계열(MS949)이었기 때문이다. 호환성은 {@code NicePlaindataCodecVendorCompatibilityTest} 가 본다.
 *
 * <p>순서도 규격의 일부라 {@link LinkedHashMap} 으로 보존한다.
 */
@Component
public class NicePlaindataCodec {

    /** 벤더 모듈과 같은 문자셋. 플랫폼 기본값에 기대지 않는다. */
    private static final Charset CHARSET = Charset.forName("EUC-KR");

    public String encode(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            appendToken(sb, entry.getKey());
            appendToken(sb, entry.getValue());
        }
        return sb.toString();
    }

    public Map<String, String> decode(String plaindata) {
        Map<String, String> fields = new LinkedHashMap<>();
        Cursor cursor = new Cursor(plaindata);
        while (cursor.hasRemaining()) {
            String key = cursor.readToken();
            String value = cursor.readToken();
            fields.put(key, value);
        }
        return fields;
    }

    private void appendToken(StringBuilder sb, String value) {
        sb.append(value.getBytes(CHARSET).length).append(':').append(value);
    }

    /**
     * 바이트 길이로 잘라내는 커서. 값에 ':' 가 들어가도(RTN_URL 등) 안전하다.
     * 문자 단위로 자르면 한글에서 어긋나므로 바이트 배열 위에서 처리한다.
     */
    private static final class Cursor {
        private final byte[] bytes;
        private int position;

        private Cursor(String plaindata) {
            this.bytes = plaindata.getBytes(CHARSET);
        }

        private boolean hasRemaining() {
            return position < bytes.length;
        }

        private String readToken() {
            int delimiter = indexOfColon();
            int length = parseLength(delimiter);
            int valueStart = delimiter + 1;
            if (valueStart + length > bytes.length) {
                throw new IllegalArgumentException("평문이 선언된 길이보다 짧습니다.");
            }
            String value = new String(bytes, valueStart, length, CHARSET);
            position = valueStart + length;
            return value;
        }

        private int indexOfColon() {
            for (int i = position; i < bytes.length; i++) {
                if (bytes[i] == ':') {
                    return i;
                }
            }
            throw new IllegalArgumentException("길이 구분자(':')가 없습니다.");
        }

        private int parseLength(int delimiter) {
            String raw = new String(bytes, position, delimiter - position, CHARSET);
            try {
                int length = Integer.parseInt(raw);
                if (length < 0) {
                    throw new IllegalArgumentException("길이가 음수입니다: " + raw);
                }
                return length;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("길이를 숫자로 읽을 수 없습니다: " + raw, e);
            }
        }
    }
}
