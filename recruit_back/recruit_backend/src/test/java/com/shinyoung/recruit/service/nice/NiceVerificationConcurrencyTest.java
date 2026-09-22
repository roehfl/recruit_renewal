package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.NiceVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 같은 요청을 두 스레드가 동시에 처리하는 경쟁 재현.
 *
 * <p>스레드 두 개를 그냥 띄우면 대부분 순차로 실행돼 우연히 통과한다. 그래서 Store 의 읽기 메서드 안에서
 * {@link CyclicBarrier} 로 두 스레드를 만나게 한다 — 둘 다 읽기를 마친 뒤에야 쓰기로 넘어가므로
 * check-then-act 경쟁이 매번 재현된다.
 */
class NiceVerificationConcurrencyTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    /** 원인과 무관하게 사용자에게 나가는 문구. 원인을 구분해 보여주면 재전송·탈취 시도에 정보가 된다. */
    private static final String FAILURE_MESSAGE = "본인확인에 실패했습니다. 다시 시도해주세요.";

    /** 배리어가 풀리지 않을 때 테스트가 멈추지 않게 하는 상한. */
    private static final long BARRIER_TIMEOUT_SECONDS = 5;
    private static final long FUTURE_TIMEOUT_SECONDS = 10;

    private NicePlaindataCodec codec;
    private MockNiceClient mockClient;
    private NiceProperties properties;

    @BeforeEach
    void setUp() {
        codec = new NicePlaindataCodec();
        mockClient = new MockNiceClient(Clock.fixed(NOW, ZoneOffset.UTC), codec);

        properties = new NiceProperties();
        properties.setSiteCode("SITECODE");
        properties.setSitePassword("SITEPASS");
        properties.setReturnUrl("https://example.test/api/auth/nice/callback");
        properties.setErrorUrl("https://example.test/api/auth/nice/callback/error");
    }

    private NiceVerificationService serviceWith(NiceClient client, NiceVerificationStore store) {
        return new NiceVerificationService(
                client, codec, store, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String reqSeqOf(NiceClient client, String encodeData) {
        return codec.decode(client.decode(encodeData).plaindata()).get("REQ_SEQ");
    }

    private String niceSuccessResponse(String reqSeq) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01012345678");
        fields.put("BIRTHDATE", "19900101");
        fields.put("GENDER", "1");
        return mockClient.encode(codec.encode(fields));
    }

    private String niceErrorResponse(String reqSeq) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("ERR_CODE", "9999");
        return mockClient.encode(codec.encode(fields));
    }

    @Test
    void concurrentCallbacksWithSameReqSeqIssueOnlyOneToken() throws Exception {
        BarrierOnFindStore store = new BarrierOnFindStore();
        NiceVerificationService service = serviceWith(mockClient, store);
        String reqSeq = reqSeqOf(mockClient, service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));
        String response = niceSuccessResponse(reqSeq);

        store.arm();
        List<Object> outcomes = runTwiceConcurrently(() -> service.handleCallback(response));
        store.disarm();

        assertEquals(1, countSuccesses(outcomes), "토큰은 하나만 발급돼야 한다: " + outcomes);
        assertEquals(1, countRejections(outcomes), "나머지 하나는 거부돼야 한다: " + outcomes);
        assertEquals(FAILURE_MESSAGE, rejectionOf(outcomes).getMessage());
        // 남은 레코드의 토큰이 이긴 쪽 토큰이어야 한다. 진 쪽 토큰이 저장돼 있으면 발급된 토큰과 어긋난다.
        assertEquals(successOf(outcomes), store.find(reqSeq).orElseThrow().resultToken());
    }

    @Test
    void concurrentErrorCallbacksWithSameReqSeqIssueOnlyOneToken() throws Exception {
        BarrierOnFindStore store = new BarrierOnFindStore();
        NiceVerificationService service = serviceWith(mockClient, store);
        String reqSeq = reqSeqOf(mockClient, service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));
        String response = niceErrorResponse(reqSeq);

        store.arm();
        List<Object> outcomes = runTwiceConcurrently(() -> service.handleErrorCallback(response));
        store.disarm();

        assertEquals(1, countSuccesses(outcomes), "토큰은 하나만 발급돼야 한다: " + outcomes);
        assertEquals(1, countRejections(outcomes), "나머지 하나는 거부돼야 한다: " + outcomes);
        assertEquals(FAILURE_MESSAGE, rejectionOf(outcomes).getMessage());
        assertEquals(successOf(outcomes), store.find(reqSeq).orElseThrow().resultToken());
    }

    @Test
    void concurrentExchangesOfSameTokenSucceedOnlyOnce() throws Exception {
        BarrierOnFindByResultTokenStore store = new BarrierOnFindByResultTokenStore();
        NiceVerificationService service = serviceWith(mockClient, store);
        String reqSeq = reqSeqOf(mockClient, service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));
        String token = service.handleCallback(niceSuccessResponse(reqSeq));

        store.arm();
        List<Object> outcomes = runTwiceConcurrently(() -> service.exchangeResult(token, "sess-1"));

        assertEquals(1, countSuccesses(outcomes), "교환은 한 번만 성공해야 한다: " + outcomes);
        assertEquals(1, countRejections(outcomes), "나머지 하나는 거부돼야 한다: " + outcomes);
        assertTrue(store.find(reqSeq).isEmpty());
    }

    @Test
    void collidingReqSeqIsReissuedWithoutOverwritingEarlierRecord() {
        // 모듈의 REQ_SEQ 는 밀리초 + random%100 이라 같은 값이 나온다. 앞의 2회는 "DUP" 를 돌려준다.
        CollidingNiceClient client = new CollidingNiceClient(mockClient, 2);
        NiceVerificationStore store = new NiceVerificationStore();
        NiceVerificationService service = serviceWith(client, store);

        String first = reqSeqOf(client, service.request(NiceVerificationPurpose.SIGNUP, "sess-A"));
        String second = reqSeqOf(client, service.request(NiceVerificationPurpose.SIGNUP, "sess-B"));

        assertEquals("DUP", first);
        assertNotEquals(first, second);
        assertEquals("sess-A", store.find(first).orElseThrow().sessionId());
        assertEquals("sess-B", store.find(second).orElseThrow().sessionId());
    }

    @Test
    @Timeout(10) // 재시도 상한이 없으면 무한 루프가 된다. 멈추지 않고 실패하게 한다.
    void requestFailsWhenReqSeqKeepsCollidingBeyondRetryLimit() {
        CollidingNiceClient client = new CollidingNiceClient(mockClient, Integer.MAX_VALUE);
        NiceVerificationStore store = new NiceVerificationStore();
        NiceVerificationService service = serviceWith(client, store);
        service.request(NiceVerificationPurpose.SIGNUP, "sess-A");

        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> service.request(NiceVerificationPurpose.SIGNUP, "sess-B"));
        assertEquals(FAILURE_MESSAGE, e.getMessage());
        // 포기하더라도 앞사람의 레코드는 그대로여야 한다.
        assertEquals("sess-A", store.find("DUP").orElseThrow().sessionId());
    }

    /** 같은 작업을 두 스레드에서 동시에 돌려 결과(반환값 또는 던져진 예외)를 모은다. */
    private List<Object> runTwiceConcurrently(Callable<?> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(task);
            Future<?> second = executor.submit(task);
            return List.of(outcomeOf(first), outcomeOf(second));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object outcomeOf(Future<?> future) throws Exception {
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private long countSuccesses(List<Object> outcomes) {
        return outcomes.stream().filter(outcome -> !(outcome instanceof Throwable)).count();
    }

    private long countRejections(List<Object> outcomes) {
        return outcomes.stream().filter(NiceVerificationException.class::isInstance).count();
    }

    private Throwable rejectionOf(List<Object> outcomes) {
        return (Throwable) outcomes.stream()
                .filter(NiceVerificationException.class::isInstance).findFirst().orElseThrow();
    }

    private Object successOf(List<Object> outcomes) {
        return outcomes.stream().filter(outcome -> !(outcome instanceof Throwable)).findFirst().orElseThrow();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("두 스레드가 읽기 지점에서 만나지 못했습니다.", e);
        }
    }

    /** find 에서 두 스레드를 만나게 해 둘 다 PENDING 을 읽은 뒤 쓰기로 넘어가게 한다. */
    private static final class BarrierOnFindStore extends NiceVerificationStore {
        private final CyclicBarrier barrier = new CyclicBarrier(2);
        private volatile boolean armed = false;

        void arm() {
            armed = true;
        }

        /** 사후 검증의 find 가 배리어에 걸리지 않게 동시 구간이 끝나면 푼다. */
        void disarm() {
            armed = false;
        }

        @Override
        public Optional<NiceVerificationRecord> find(String reqSeq) {
            Optional<NiceVerificationRecord> found = super.find(reqSeq);
            if (armed) {
                await(barrier);
            }
            return found;
        }
    }

    /** findByResultToken 에서 두 스레드를 만나게 해 둘 다 레코드를 찾은 뒤 제거로 넘어가게 한다. */
    private static final class BarrierOnFindByResultTokenStore extends NiceVerificationStore {
        private final CyclicBarrier barrier = new CyclicBarrier(2);
        private volatile boolean armed = false;

        void arm() {
            armed = true;
        }

        @Override
        public Optional<NiceVerificationRecord> findByResultToken(String resultToken) {
            Optional<NiceVerificationRecord> found = super.findByResultToken(resultToken);
            if (armed) {
                await(barrier);
            }
            return found;
        }
    }

    /** 앞의 {@code duplicates} 회는 "DUP", 그다음은 매번 새 REQ_SEQ 를 준다. 인코딩은 Mock 에 위임한다. */
    private static final class CollidingNiceClient implements NiceClient {
        private final MockNiceClient delegate;
        private final int duplicates;
        private final AtomicInteger calls = new AtomicInteger();

        CollidingNiceClient(MockNiceClient delegate, int duplicates) {
            this.delegate = delegate;
            this.duplicates = duplicates;
        }

        @Override
        public String generateRequestNo() {
            int call = calls.incrementAndGet();
            return call <= duplicates ? "DUP" : "SEQ-" + call;
        }

        @Override
        public String encode(String plaindata) {
            return delegate.encode(plaindata);
        }

        @Override
        public NiceDecodeResult decode(String encodeData) {
            return delegate.decode(encodeData);
        }

        @Override
        public Map<String, String> parse(String plaindata) {
            return delegate.parse(plaindata);
        }
    }
}
