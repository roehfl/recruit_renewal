package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;
import com.shinyoung.recruit.exception.NiceVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 본인확인 요청 발급 · 콜백 검증 · 결과 교환.
 *
 * <p>세션을 직접 만지지 않는다. 세션 id 는 인자로 받고, 세션에 값을 심는 일은
 * 컨트롤러가 한다 — 서비스가 {@code HttpSession} 을 알면 단위 테스트가 서블릿
 * 컨테이너에 묶인다.
 */
@Service
public class NiceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(NiceVerificationService.class);

    /** 파이프라인 실패의 사용자 노출 문구. 원인은 로그에만 남긴다 — 구분해 보여주면 공격자에게 정보가 된다. */
    private static final String FAILURE_MESSAGE = "본인확인에 실패했습니다. 다시 시도해주세요.";

    /** resultToken 유효시간. 팝업이 리다이렉트 직후 교환하므로 짧게 둔다. */
    private static final Duration RESULT_TOKEN_TTL = Duration.ofMinutes(1);

    /**
     * REQ_SEQ 충돌 시 발급 시도 상한(첫 시도 포함). 모듈 값은 밀리초 + random%100 이라 같은 밀리초에
     * 몰릴 때만 겹친다 — 몇 번 안에 풀린다. 상한은 무한 루프 방지용이다.
     */
    private static final int MAX_REQUEST_NO_ATTEMPTS = 5;

    private final NiceClient niceClient;
    private final NicePlaindataCodec codec;
    private final NiceVerificationStore store;
    private final NiceProperties properties;
    private final Clock clock;

    public NiceVerificationService(
            NiceClient niceClient,
            NicePlaindataCodec codec,
            NiceVerificationStore store,
            NiceProperties properties,
            Clock clock) {
        this.niceClient = niceClient;
        this.codec = codec;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 요청번호를 발급해 보관하고 팝업이 NICE 로 보낼 EncodeData 를 돌려준다.
     *
     * <p>키 이름·순서·값은 레거시 인코딩부에서 확인한 것이다(2026-09-21). 바꾸지 않는다.
     * AUTH_TYPE 과 CUSTOMIZE 는 빈 값이다 — 빼는 것과 다르다. 키는 있고 길이가 0 이다.
     */
    public String request(NiceVerificationPurpose purpose, String sessionId) {
        String reqSeq = savePending(purpose, sessionId);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", reqSeq);
        fields.put("SITECODE", properties.getSiteCode());
        fields.put("AUTH_TYPE", "");
        fields.put("RTN_URL", properties.getReturnUrl());
        fields.put("ERR_URL", properties.getErrorUrl());
        fields.put("POPUP_GUBUN", "N");
        fields.put("CUSTOMIZE", "");

        return niceClient.encode(codec.encode(fields));
    }

    /**
     * NICE 성공 콜백 처리. 1회용 resultToken 을 돌려준다.
     *
     * <p>이 요청에는 세션 쿠키가 실리지 않는다(cross-site POST, SameSite=Lax).
     * 그래서 세션 대조는 여기서 못 하고 결과 교환 단계로 미룬다.
     */
    public String handleCallback(String encodeData) {
        NiceDecodeResult decoded = niceClient.decode(encodeData);
        Map<String, String> fields = niceClient.parse(decoded.plaindata());
        NiceVerificationRecord record = consumePending(fields.get("REQ_SEQ"));
        verifyCipherTime(record.reqSeq(), decoded.cipherEpochSeconds());

        String token = newResultToken();
        NiceVerificationRecord verified = record.verified(
                token,
                clock.instant(),
                required(fields, "NAME"),
                required(fields, "MOBILE_NO"),
                required(fields, "BIRTHDATE"),
                required(fields, "GENDER"));
        if (!store.compareAndSet(record, verified)) {
            log.info("본인확인 콜백 거부: 동시 처리에서 먼저 처리된 요청. reqSeq={}", record.reqSeq());
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        return token;
    }

    /** NICE 실패·취소 콜백 처리. 성공 경로와 같은 결과 화면으로 보내기 위해 token 을 발급한다. */
    public String handleErrorCallback(String encodeData) {
        Map<String, String> fields = niceClient.parse(niceClient.decode(encodeData).plaindata());
        NiceVerificationRecord record = consumePending(fields.get("REQ_SEQ"));

        String token = newResultToken();
        if (!store.compareAndSet(record, record.failed(token, clock.instant()))) {
            log.info("본인확인 실패 콜백 거부: 동시 처리에서 먼저 처리된 요청. reqSeq={}", record.reqSeq());
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        return token;
    }

    /**
     * resultToken 을 인증 결과로 교환한다. <b>1회용이다</b> — 성공·실패 모두 레코드를 제거한다.
     *
     * <p>이 요청은 same-site 라 세션 쿠키가 실린다. 그래서 여기서 세션 대조를 한다.
     * 토큰이 유출돼도 다른 세션에서는 쓸 수 없다.
     *
     * <p>레코드를 검사보다 <b>먼저</b> 꺼내는 것이 의도다. {@link NiceVerificationStore#takeByResultToken}
     * 은 조회와 제거를 원자적으로 하므로, 꺼내는 순간 레코드는 이미 제거돼 있다. 세션 불일치·만료로
     * 거부하더라도 레코드는 사라져야 한다 — 남겨 두면 조건을 바꿔 가며 같은 토큰을 계속 시도할 수 있다.
     * 같은 토큰으로 동시에 두 번 불려도 한쪽만 레코드를 받는다.
     */
    public NiceVerifiedIdentity exchangeResult(String resultToken, String sessionId) {
        NiceVerificationRecord record = store.takeByResultToken(resultToken).orElseThrow(() -> {
            log.info("본인확인 결과 교환 거부: 없거나 이미 사용된 토큰.");
            return new NiceVerificationException(FAILURE_MESSAGE);
        });

        if (!record.sessionId().equals(sessionId)) {
            log.warn("본인확인 결과 교환 세션 불일치. reqSeq={}", record.reqSeq());
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        if (record.resultTokenIssuedAt().isBefore(clock.instant().minus(RESULT_TOKEN_TTL))) {
            log.info("본인확인 결과 교환 거부: 토큰 만료. reqSeq={}", record.reqSeq());
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        if (record.status() != NiceVerificationStatus.VERIFIED) {
            log.info("본인확인 결과 교환: NICE 인증 실패·취소 건. reqSeq={}", record.reqSeq());
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }

        return new NiceVerifiedIdentity(
                record.purpose(), record.name(), record.phoneNumber(),
                record.birthDate(), record.gender(), clock.instant());
    }

    /**
     * 세션에 들어 있는 인증 결과가 아직 쓸 수 있는지 확인한다. 가입 제출 시점에 부른다.
     *
     * <p>용도를 대조하지 않으면 가입용 인증을 다른 흐름에 밀어 넣을 수 있다.
     *
     * <p>메시지를 {@link #FAILURE_MESSAGE} 로 통일하지 않는다. 가입 제출 시점에 정상 사용자가 자기 세션에
     * 대해 보는 안내라 공격 가치가 없고, 사용자는 무엇을 해야 할지 알아야 한다.
     */
    public NiceVerifiedIdentity requireFresh(
            NiceVerifiedIdentity identity, NiceVerificationPurpose expectedPurpose) {
        if (identity == null) {
            throw new NiceVerificationException("본인인증을 먼저 진행해주세요.");
        }
        if (identity.purpose() != expectedPurpose) {
            throw new NiceVerificationException("본인인증 용도가 일치하지 않습니다.");
        }
        if (isExpired(identity.verifiedAt(), properties.getVerifiedTtlMinutes())) {
            throw new NiceVerificationException("본인인증이 만료되었습니다. 다시 진행해주세요.");
        }
        return identity;
    }

    /**
     * 요청번호를 발급해 PENDING 레코드를 저장하고 그 요청번호를 돌려준다.
     *
     * <p>모듈의 REQ_SEQ 는 동시 발급 시 겹친다. 형식은 NICE 규격이라 바꾸지 않고, 이미 있는
     * 번호면 덮어쓰지 않고 새로 발급받는다 — 덮어쓰면 앞사람의 콜백이 실패한다.
     */
    private String savePending(NiceVerificationPurpose purpose, String sessionId) {
        Instant now = clock.instant();
        for (int attempt = 1; attempt <= MAX_REQUEST_NO_ATTEMPTS; attempt++) {
            String reqSeq = niceClient.generateRequestNo();
            if (store.saveIfAbsent(NiceVerificationRecord.pending(reqSeq, purpose, sessionId, now))) {
                return reqSeq;
            }
        }
        log.warn("본인확인 요청번호가 {}회 연속 충돌했습니다.", MAX_REQUEST_NO_ATTEMPTS);
        throw new NiceVerificationException(FAILURE_MESSAGE);
    }

    /**
     * 요청번호로 레코드를 찾아 재사용 가능한 상태인지 확인한다.
     *
     * <p>이 검사가 재전송 방어의 본체다. 캡처된 응답을 다시 넣으면 레코드가 이미
     * {@code PENDING} 이 아니라 여기서 걸린다. 동시에 온 재전송은 둘 다 여기를 통과할 수 있으므로,
     * 호출부가 돌려받은 레코드를 기대값으로 {@link NiceVerificationStore#compareAndSet} 해 한쪽만 이기게 한다.
     */
    private NiceVerificationRecord consumePending(String reqSeq) {
        NiceVerificationRecord record = store.find(reqSeq).orElseThrow(() -> {
            log.info("본인확인 콜백 거부: 발급 기록이 없는 요청. reqSeq={}", reqSeq);
            return new NiceVerificationException(FAILURE_MESSAGE);
        });

        if (record.status() != NiceVerificationStatus.PENDING) {
            log.info("본인확인 콜백 거부: 이미 처리된 요청. reqSeq={}", reqSeq);
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        if (isExpired(record.issuedAt(), properties.getRequestTtlMinutes())) {
            log.info("본인확인 콜백 거부: 요청 만료. reqSeq={}", reqSeq);
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        return record;
    }

    /**
     * 암호문 생성 시각 검사. Store 대조와 독립된 2차 게이트다.
     *
     * <p><b>읽지 못하면 거부하지 않고 경고만 남긴다.</b> 시각 포맷을 실제 NICE 응답으로
     * 확인할 수단이 없기 때문이다 — 모듈이 자기 암호문을 복호화하지 못해 왕복 테스트가
     * 불가능하고, 운영 사이트코드도 1개뿐이라 배포 전 확인이 안 된다. 여기서 fail-closed 로
     * 두면 포맷 가정이 빗나갔을 때 본인확인 전체가 멈춘다. 주 방어선인 Store 대조는 그대로다.
     */
    private void verifyCipherTime(String reqSeq, Long cipherEpochSeconds) {
        if (cipherEpochSeconds == null) {
            log.warn("암호문 생성 시각을 읽지 못했습니다. 2차 게이트를 건너뜁니다.");
            return;
        }
        if (isExpired(Instant.ofEpochSecond(cipherEpochSeconds), properties.getRequestTtlMinutes())) {
            log.info("본인확인 콜백 거부: 암호문 생성 시각 만료. reqSeq={}", reqSeq);
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
    }

    private boolean isExpired(Instant issuedAt, int ttlMinutes) {
        return issuedAt.isBefore(clock.instant().minus(Duration.ofMinutes(ttlMinutes)));
    }

    /**
     * 응답에서 필수 필드를 꺼낸다.
     *
     * <p>키 이름은 NICE 응답 규격이다. 실응답(2026-09-22)은 {@code REQ_SEQ, RES_SEQ, AUTH_TYPE, NAME,
     * BIRTHDATE, GENDER, NATIONALINFO, MOBILE_NO, UTF8_NAME} — <b>CI·DI 는 없다</b>(이 사이트코드 계약에
     * 제공이 없다). 없을 때 <b>실제로 온 키 목록을 로그로 남긴다</b> — 이 로그로 CI 부재를 찾았다.
     *
     * <p>키 목록은 로그에만 남긴다. 예외 메시지는 사용자 응답으로 나가므로 넣지 않는다.
     */
    private String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            log.warn("본인확인 응답에 필수 키 {} 가 없습니다. 수신된 키: {}", key, fields.keySet());
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        return value;
    }

    private String newResultToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
