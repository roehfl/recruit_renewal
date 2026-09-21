package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.exception.NiceVerificationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockNiceClientTest {

    /** 실구현과 같은 고정 문구. 원인(형식·Base64·길이 오류)은 로그에만 남는다. */
    private static final String FAILURE_MESSAGE = "본인확인에 실패했습니다. 다시 시도해주세요.";

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
    private final NicePlaindataCodec codec = new NicePlaindataCodec();
    private final MockNiceClient client = new MockNiceClient(clock, codec);

    @Test
    void encodeThenDecodeReturnsOriginalPlaindata() {
        String plaindata = "7:REQ_SEQ3:abc4:NAME6:홍길동";

        assertEquals(plaindata, client.decode(client.encode(plaindata)).plaindata());
    }

    @Test
    void encodeDoesNotLeakPlaindataDirectly() {
        assertTrue(client.encode("7:REQ_SEQ3:abc").indexOf("REQ_SEQ") < 0);
    }

    @Test
    void decodeCarriesEncodeTime() {
        assertEquals(1_700_000_000L, client.decode(client.encode("4:NAME1:a")).cipherEpochSeconds());
    }

    @Test
    void decodeRejectsGarbage() {
        NiceVerificationException e =
                assertThrows(NiceVerificationException.class, () -> client.decode("not-a-valid-payload"));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void generateRequestNoReturnsDistinctValues() {
        assertNotEquals(client.generateRequestNo(), client.generateRequestNo());
    }

    @Test
    void parseReadsFieldsFromPlaindata() {
        Map<String, String> parsed = client.parse("4:NAME6:홍길동9:MOBILE_NO11:01012345678");

        assertEquals("홍길동", parsed.get("NAME"));
        assertEquals("01012345678", parsed.get("MOBILE_NO"));
    }

    @Test
    void parseRejectsMalformedPlaindata() {
        NiceVerificationException e =
                assertThrows(NiceVerificationException.class, () -> client.parse("not-plaindata"));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }
}
