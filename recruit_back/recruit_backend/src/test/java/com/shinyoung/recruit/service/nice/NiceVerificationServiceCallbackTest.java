package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NiceVerificationServiceCallbackTest {

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

    /** NICE 가 돌려주는 성공 응답을 흉내낸 암호문. */
    private String niceSuccessResponse(String reqSeq) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        fields.put("DI", "DI-VALUE-1");
        return client.encode(codec.encode(fields));
    }

    private String issueRequest(NiceVerificationService service) {
        String encodeData = service.request(NiceVerificationPurpose.SIGNUP, "sess-1");
        return codec.decode(client.decode(encodeData).plaindata()).get("REQ_SEQ");
    }

    @Test
    void successfulCallbackMarksRecordVerifiedAndIssuesToken() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);

        String token = service.handleCallback(niceSuccessResponse(reqSeq));

        assertNotNull(token);
        NiceVerificationRecord record = store.find(reqSeq).orElseThrow();
        assertEquals(NiceVerificationStatus.VERIFIED, record.status());
        assertEquals("홍길동", record.name());
        assertEquals("01012345678", record.phoneNumber());
        assertEquals("CI-VALUE-1", record.ci());
        assertEquals(token, record.resultToken());
    }

    @Test
    void replayedCallbackIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);
        String response = niceSuccessResponse(reqSeq);
        service.handleCallback(response);

        // 같은 암호문을 다시 넣는다. 레코드가 PENDING 이 아니므로 거부돼야 한다.
        NiceVerificationException e =
                assertThrows(NiceVerificationException.class, () -> service.handleCallback(response));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void callbackAfterRequestTtlIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);
        String response = niceSuccessResponse(reqSeq);

        NiceVerificationService late = serviceAt(NOW.plus(Duration.ofMinutes(11)));

        NiceVerificationException e =
                assertThrows(NiceVerificationException.class, () -> late.handleCallback(response));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void callbackWithUnknownReqSeqIsRejected() {
        NiceVerificationService service = serviceAt(NOW);

        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> service.handleCallback(niceSuccessResponse("never-issued")));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void callbackWithStaleCipherTimeIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);

        // 암호문은 11분 전에 만들어졌는데 요청 레코드는 방금 발급된 상황.
        // Store 대조는 통과하지만 2차 게이트가 걸러야 한다.
        MockNiceClient staleClient =
                new MockNiceClient(Clock.fixed(NOW.minus(Duration.ofMinutes(11)), ZoneOffset.UTC), codec);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("CI", "CI-VALUE-1");
        String stale = staleClient.encode(codec.encode(fields));

        NiceVerificationException e =
                assertThrows(NiceVerificationException.class, () -> service.handleCallback(stale));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void callbackMissingRequiredFieldIsRejected() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);

        // CI 가 없는 응답. 키 이름 가정이 틀렸을 때 조용히 null 로 저장되면 안 된다.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        String incomplete = client.encode(codec.encode(fields));

        NiceVerificationException e =
                assertThrows(NiceVerificationException.class, () -> service.handleCallback(incomplete));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void errorCallbackMarksRecordFailedAndIssuesToken() {
        NiceVerificationService service = serviceAt(NOW);
        String reqSeq = issueRequest(service);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("ERR_CODE", "9999");
        String token = service.handleErrorCallback(client.encode(codec.encode(fields)));

        assertNotNull(token);
        assertEquals(NiceVerificationStatus.FAIL, store.find(reqSeq).orElseThrow().status());
    }
}
