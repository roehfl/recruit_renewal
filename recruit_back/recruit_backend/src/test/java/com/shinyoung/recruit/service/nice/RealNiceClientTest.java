package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.exception.NiceVerificationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모듈이 이 JVM 에서 로딩·동작하는지만 본다.
 *
 * <p><b>encode 뒤 decode 왕복은 검증할 수 없다.</b> 모듈이 자기 암호문을 복호화하지 못한다
 * (항상 {@code -6}). 요청 포맷과 응답 포맷이 다르기 때문이다. 복호화·파싱의 실검증은
 * 배포 후 실인증 1회뿐이다.
 *
 * <p>이 테스트는 {@code --add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED}
 * 플래그가 빠지면 {@code IllegalAccessError} 로 실패한다. 그것이 이 테스트의 주 목적이다.
 */
class RealNiceClientTest {

    /** 사용자에게 나가는 고정 문구. 모듈 오류코드(-4, -9 등)는 로그에만 남아야 한다. */
    private static final String FAILURE_MESSAGE = "본인확인에 실패했습니다. 다시 시도해주세요.";

    private RealNiceClient client() {
        return client("0123456789ABCDEF");
    }

    private RealNiceClient client(String sitePassword) {
        NiceProperties properties = new NiceProperties();
        properties.setSiteCode("EXAMPLE");
        properties.setSitePassword(sitePassword);
        return new RealNiceClient(properties, new NicePlaindataCodec());
    }

    @Test
    void generateRequestNoReturnsValueFromModule() {
        String requestNo = client().generateRequestNo();

        assertNotNull(requestNo);
        assertTrue(requestNo.startsWith("EXAMPLE"));
    }

    @Test
    void encodeProducesCipherText() {
        String cipher = client().encode("7:REQ_SEQ3:abc");

        assertNotNull(cipher);
        assertTrue(cipher.length() > 100);
    }

    @Test
    void encodeFailureDoesNotExposeModuleErrorCode() {
        // 빈 패스워드면 모듈이 -9 를 돌려준다. 코드는 응답 본문으로 나가면 안 된다.
        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> client("").encode("7:REQ_SEQ3:abc"));

        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void decodeFailureDoesNotExposeModuleErrorCode() {
        // 형식이 틀린 암호문이면 모듈이 -4 를 돌려준다. -6·-12 등은 복호화 실패 원인을 드러낸다.
        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> client().decode("garbage"));

        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void cipherTimeIsReadAsKoreaStandardTime() {
        // NICE 서버 시각은 KST 다. 서버 JVM 이 UTC 로 떠도 같은 값이어야 한다 —
        // 9시간 어긋나면 모든 실인증이 만료로 거부된다(파싱은 성공하므로 fail-open 경로도 안 탄다).
        assertEquals(Instant.parse("2026-09-21T02:00:00Z").getEpochSecond(),
                RealNiceClient.parseCipherTime("260921110000"));
    }

    @Test
    void unreadableCipherTimeReturnsNull() {
        assertNull(RealNiceClient.parseCipherTime(null));
        assertNull(RealNiceClient.parseCipherTime("garbage"));
        assertNull(RealNiceClient.parseCipherTime(""));
    }

    @Test
    void parseFailureUsesSameFixedMessage() {
        NiceVerificationException e = assertThrows(NiceVerificationException.class,
                () -> client().parse("not-plaindata"));

        assertEquals(FAILURE_MESSAGE, e.getMessage());
    }

    @Test
    void parseFailureDoesNotLeakPlaindataFragmentsToStdoutOrStderr() {
        // 벤더 fnParse 는 길이 규칙이 어긋나면 입력 조각을 stdout/stderr 에 직접 찍는다.
        // "4:NAME9:홍길동" 은 값 길이를 9(UTF-8 바이트)로 선언했지만 EUC-KR 로는 6바이트라 어긋난다.
        //
        // 캡처가 완전히 빈 문자열이길 기대하지는 않는다 — 이 클래스 자신의 log.warn 도 테스트
        // 로깅 설정에 따라 콘솔(stdout)로 나간다(실측: "NICE 응답 평문 파싱 실패. IllegalArgumentException").
        // 관심사는 "평문 조각이 새지 않는다"이지 "아무것도 안 찍힌다"가 아니다.
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            assertThrows(NiceVerificationException.class, () -> client().parse("4:NAME9:홍길동"));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        String output = captured.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("홍길동"), output);
        assertFalse(output.contains("NAME"), output);
    }
}
