package com.shinyoung.recruit.service.nice;

import NiceID.Check.CPClient;
import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.exception.NiceVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * NICE 제공 모듈({@code NiceID.jar}) 호출 구현.
 *
 * <p>{@code CPClient} 는 결과를 인스턴스 상태로 들고 있다({@code getCipherData}·
 * {@code getPlainData}·{@code getCipherDateTime}). 호출마다 새로 만든다 —
 * 공유하면 동시 요청에서 결과가 섞인다.
 *
 * <p><b>이 클래스의 복호화·파싱은 로컬에서 검증할 수 없다.</b> 모듈은 자기가 만든 암호문을
 * 자기가 복호화하지 못한다({@code fnEncode} 뒤 {@code fnDecode} 는 항상 {@code -6}).
 * 요청 포맷과 응답 포맷이 다르기 때문이다. 첫 검증은 배포 후 실인증 1회다.
 *
 * <p>기동에 {@code --add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED} 가 필요하다.
 */
public class RealNiceClient implements NiceClient {

    private static final Logger log = LoggerFactory.getLogger(RealNiceClient.class);

    /**
     * 사용자 노출 문구. {@code NiceVerificationService} 의 파이프라인 실패 문구와 같다.
     * 모듈 오류코드(-5 해시·-6 데이터·-12 패스워드 불일치 등)는 복호화 실패 원인을 드러내므로 로그에만 남긴다.
     */
    private static final String FAILURE_MESSAGE = "본인확인에 실패했습니다. 다시 시도해주세요.";

    /** {@code getCipherDateTime()} 의 포맷 후보. 실응답으로 확인되지 않았다. */
    private static final DateTimeFormatter CIPHER_TIME =
            DateTimeFormatter.ofPattern("yyMMddHHmmss");

    /** NICE 서버 시각의 시간대. 서버 JVM 시간대(운영이 UTC 일 수 있다)와 무관하게 고정한다. */
    private static final ZoneId NICE_ZONE = ZoneId.of("Asia/Seoul");

    private final NiceProperties properties;
    private final NicePlaindataCodec codec;

    public RealNiceClient(NiceProperties properties, NicePlaindataCodec codec) {
        this.properties = properties;
        this.codec = codec;
    }

    @Override
    public String generateRequestNo() {
        return new CPClient().getRequestNO(properties.getSiteCode());
    }

    @Override
    public String encode(String plaindata) {
        CPClient client = new CPClient();
        int result = client.fnEncode(
                properties.getSiteCode(), properties.getSitePassword(), plaindata);
        if (result != 0) {
            log.warn("NICE 요청 암호화 실패. code={}", result);
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        return client.getCipherData();
    }

    @Override
    public NiceDecodeResult decode(String encodeData) {
        CPClient client = new CPClient();
        int result = client.fnDecode(
                properties.getSiteCode(), properties.getSitePassword(), encodeData);
        if (result != 0) {
            log.warn("NICE 응답 복호화 실패. code={}", result);
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        return new NiceDecodeResult(client.getPlainData(), parseCipherTime(client.getCipherDateTime()));
    }

    /**
     * 시각 문자열을 epoch 초로 바꾼다. <b>읽지 못하면 null 을 돌려준다</b> — 포맷을
     * 실응답으로 확인할 수단이 없어, 여기서 예외를 던지면 가정이 빗나갔을 때
     * 본인확인 전체가 멈춘다. 호출부가 경고만 남기고 2차 게이트를 건너뛴다.
     *
     * <p>테스트용으로 분리. NICE 서버 시각은 KST 다 — 서버 시간대와 무관하게 Asia/Seoul 로 해석한다.
     * {@code systemDefault()} 로 두면 UTC 로 뜬 JVM 에서 9시간 어긋나 모든 실인증이 만료로 거부된다.
     */
    static Long parseCipherTime(String raw) {
        try {
            return LocalDateTime.parse(raw, CIPHER_TIME)
                    .atZone(NICE_ZONE)
                    .toEpochSecond();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 응답 평문을 필드 맵으로 판다. <b>벤더 {@code fnParse} 를 쓰지 않는다.</b>
     *
     * <p>{@code fnParse} 는 파싱에 실패하면 입력 조각을 stdout/stderr 에 직접 찍는다 —
     * 실응답 형식이 어긋나면 이름·CI 조각이 콘솔 로그로 나간다. 우리 코덱은 실패 시
     * 예외만 던진다. 두 파서가 같은 결과를 낸다는 것은
     * {@code NicePlaindataCodecVendorCompatibilityTest} 가 검증한다.
     */
    @Override
    public Map<String, String> parse(String plaindata) {
        try {
            return codec.decode(plaindata);
        } catch (IllegalArgumentException e) {
            // 코덱 예외 메시지에는 평문 조각이 들어갈 수 있어 남기지 않는다. cause 도 넘기지 않는다 —
            // 상위에서 스택트레이스를 찍으면 메시지 안의 조각이 샌다.
            log.warn("NICE 응답 평문 파싱 실패. {}", e.getClass().getSimpleName());
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
    }
}
