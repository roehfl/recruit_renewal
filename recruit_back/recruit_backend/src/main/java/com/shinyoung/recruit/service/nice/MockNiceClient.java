package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.exception.NiceVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 개발·테스트용 {@link NiceClient}. <b>{@code NiceID.jar} 에 의존하지 않는다.</b>
 *
 * <p>jar 는 공개 저장소에 없고 확보 전에도 나머지 전부를 만들고 검증할 수 있어야 하므로,
 * 계약("encode 가 불투명 문자열을 주고 decode 가 원래 평문을 돌려준다")만 가역 인코딩으로
 * 충족한다. 암호학적 강도는 없다 — 그래서 운영 사용은 {@code NiceClientConfig} 가 막는다.
 *
 * <p>형식: {@code MOCK.<epoch초>.<Base64(평문)>}
 */
public class MockNiceClient implements NiceClient {

    private static final Logger log = LoggerFactory.getLogger(MockNiceClient.class);

    private static final String PREFIX = "MOCK.";

    /** 사용자 노출 문구. 실구현({@link RealNiceClient})과 같다 — 원인은 로그에만 남긴다. */
    private static final String FAILURE_MESSAGE = "본인확인에 실패했습니다. 다시 시도해주세요.";

    private final Clock clock;
    private final NicePlaindataCodec codec;

    public MockNiceClient(Clock clock, NicePlaindataCodec codec) {
        this.clock = clock;
        this.codec = codec;
    }

    @Override
    public String generateRequestNo() {
        return "MOCK" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String encode(String plaindata) {
        String payload = Base64.getEncoder()
                .encodeToString(plaindata.getBytes(StandardCharsets.UTF_8));
        return PREFIX + clock.instant().getEpochSecond() + "." + payload;
    }

    @Override
    public NiceDecodeResult decode(String encodeData) {
        String[] parts = parts(encodeData);
        try {
            return new NiceDecodeResult(
                    new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8),
                    Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            log.warn("Mock 응답 복호화 실패. {}", e.getClass().getSimpleName());
            throw new NiceVerificationException(FAILURE_MESSAGE, e);
        }
    }

    /** 실제 구현은 모듈의 {@code fnParse} 를 쓴다. Mock 은 우리 코덱에 위임한다. */
    @Override
    public Map<String, String> parse(String plaindata) {
        try {
            return codec.decode(plaindata);
        } catch (IllegalArgumentException e) {
            // 코덱 예외 메시지에는 평문 조각이 들어갈 수 있어 남기지 않는다.
            log.warn("Mock 응답 평문 파싱 실패. {}", e.getClass().getSimpleName());
            throw new NiceVerificationException(FAILURE_MESSAGE, e);
        }
    }

    private String[] parts(String encodeData) {
        if (encodeData == null || !encodeData.startsWith(PREFIX)) {
            log.warn("Mock 응답 복호화 실패. 접두어 불일치");
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        String[] parts = encodeData.split("\\.", 3);
        if (parts.length != 3) {
            log.warn("Mock 응답 복호화 실패. 구성 요소 수 불일치");
            throw new NiceVerificationException(FAILURE_MESSAGE);
        }
        return parts;
    }
}
