package com.shinyoung.recruit.service.nice;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NicePlaindataCodecTest {

    private final NicePlaindataCodec codec = new NicePlaindataCodec();

    @Test
    void encodeWritesKeyThenByteLengthThenValue() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", "abc");

        assertEquals("7:REQ_SEQ3:abc", codec.encode(fields));
    }

    @Test
    void encodeUsesByteLengthNotCharacterLength() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("NAME", "홍길동");

        // "홍길동" 은 3글자지만 EUC-KR 로 6바이트다(UTF-8 이면 9 — 그러면 NICE 가 거절한다).
        // 문자 수(3)로 세도 거절한다.
        assertEquals("4:NAME6:홍길동", codec.encode(fields));
    }

    @Test
    void encodePreservesInsertionOrder() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", "a");
        fields.put("SITECODE", "b");

        assertEquals("7:REQ_SEQ1:a8:SITECODE1:b", codec.encode(fields));
    }

    @Test
    void decodeReturnsFieldsInOrder() {
        Map<String, String> decoded = codec.decode("7:REQ_SEQ3:abc4:NAME6:홍길동");

        assertEquals("abc", decoded.get("REQ_SEQ"));
        assertEquals("홍길동", decoded.get("NAME"));
        assertEquals(2, decoded.size());
    }

    @Test
    void decodeHandlesValueContainingColon() {
        // RTN_URL 에는 반드시 ':' 가 들어간다. 길이 기반으로 잘라야 깨지지 않는다.
        String url = "https://example.test/api/auth/nice/callback";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("RTN_URL", url);

        assertEquals(url, codec.decode(codec.encode(fields)).get("RTN_URL"));
    }

    @Test
    void decodeRejectsTruncatedInput() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("7:REQ_SEQ9:abc"));
    }

    @Test
    void decodeRejectsMissingLengthDelimiter() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("7:REQ_SEQabc"));
    }
}
