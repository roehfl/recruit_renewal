package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.NiceVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiceVerificationServiceExchangeTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    /** 원인과 무관하게 사용자에게 나가는 문구. 원인을 구분해 보여주면 재전송·탈취 시도에 정보가 된다. */
    private static final String FAILURE_MESSAGE = "본인확인에 실패했습니다. 다시 시도해주세요.";

    private NiceVerificationStore store;
    private NicePlaindataCodec codec;
    private MockNiceClient client;
    private NiceProperties properties;

    @BeforeEach
    void setUp() {
        store = new NiceVerificationStore();
        codec = new NicePlaindataCodec();
        client = new MockNiceClient(Clock.fixed(NOW, ZoneOffset.UTC), codec);

        properties = new NiceProperties();
        properties.setSiteCode("SITECODE");
        properties.setSitePassword("SITEPASS");
        properties.setReturnUrl("https://example.test/api/auth/nice/callback");
        properties.setErrorUrl("https://example.test/api/auth/nice/callback/error");
    }

    private NiceVerificationService serviceAt(Instant now) {
        return new NiceVerificationService(
                client, codec, store, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private String reqSeqOf(NiceVerificationService service, String sessionId) {
        return codec.decode(client.decode(
                service.request(NiceVerificationPurpose.SIGNUP, sessionId)).plaindata()).get("REQ_SEQ");
    }

    private String verifiedToken(NiceVerificationService service) {
        String reqSeq = reqSeqOf(service, "sess-1");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        return service.handleCallback(client.encode(codec.encode(fields)));
    }

    @Test
    void exchangeReturnsIdentityForMatchingSession() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        NiceVerifiedIdentity identity = service.exchangeResult(token, "sess-1");

        assertEquals("홍길동", identity.name());
        assertEquals("01012345678", identity.phoneNumber());
        assertEquals("CI-VALUE-1", identity.ci());
        assertEquals(NiceVerificationPurpose.SIGNUP, identity.purpose());
    }

    @Test
    void exchangeRemovesRecordSoTokenCannotBeReused() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        service.exchangeResult(token, "sess-1");

        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> service.exchangeResult(token, "sess-1"));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void exchangeFromDifferentSessionIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> service.exchangeResult(token, "sess-OTHER"));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void exchangeFromDifferentSessionAlsoBurnsTheToken() {
        // 거부하더라도 레코드는 사라져야 한다. 남겨 두면 조건을 바꿔 가며 같은 토큰을 계속 시도할 수 있다.
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        assertThrows(NiceVerificationException.class, () -> service.exchangeResult(token, "sess-OTHER"));
        assertThrows(NiceVerificationException.class, () -> service.exchangeResult(token, "sess-1"));
    }

    @Test
    void exchangeAfterTokenTtlIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String token = verifiedToken(service);

        NiceVerificationService late = serviceAt(NOW.plus(Duration.ofSeconds(61)));

        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> late.exchangeResult(token, "sess-1"));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void exchangeOfFailedRecordThrowsFailure() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = reqSeqOf(service, "sess-1");

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("ERR_CODE", "9999");
        String token = service.handleErrorCallback(client.encode(codec.encode(fields)));

        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> service.exchangeResult(token, "sess-1"));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
        // 실패 레코드도 교환 시점에 제거돼야 메모리에 남지 않는다.
        assertTrue(store.find(reqSeq).isEmpty());
    }

    @Test
    void verifiedIdentityIsAcceptedWithinVerifiedTtl() {
        NiceVerificationService service = serviceAt(NOW);
        NiceVerifiedIdentity identity = service.exchangeResult(verifiedToken(service), "sess-1");

        NiceVerificationService later = serviceAt(NOW.plus(Duration.ofMinutes(29)));

        assertEquals(identity, later.requireFresh(identity, NiceVerificationPurpose.SIGNUP));
    }

    @Test
    void verifiedIdentityExpiresAfterVerifiedTtl() {
        NiceVerificationService service = serviceAt(NOW);
        NiceVerifiedIdentity identity = service.exchangeResult(verifiedToken(service), "sess-1");

        NiceVerificationService later = serviceAt(NOW.plus(Duration.ofMinutes(31)));

        assertThrows(NiceVerificationException.class,
                () -> later.requireFresh(identity, NiceVerificationPurpose.SIGNUP));
    }

    @Test
    void requireFreshRejectsNullIdentity() {
        NiceVerificationService service = serviceAt(NOW);

        assertThrows(NiceVerificationException.class,
                () -> service.requireFresh(null, NiceVerificationPurpose.SIGNUP));
    }
}
