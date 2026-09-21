package com.shinyoung.recruit.service.nice;

import java.util.Map;

/**
 * NICE 벤더 모듈 경계.
 *
 * <p>구현은 둘이다. {@code RealNiceClient} 는 {@code NiceID.jar} 의 {@code CPClient} 를 부르고,
 * {@link MockNiceClient} 는 jar 없이 같은 계약을 만족한다. 계약은 "{@code encode} 가 불투명
 * 문자열을 주고 {@code decode} 가 원래 평문을 돌려준다"뿐이라 Mock 으로도 우리 로직 전부를
 * 검증할 수 있다.
 */
public interface NiceClient {

    /**
     * 요청번호(REQ_SEQ) 발급. 실제 구현은 모듈의 {@code getRequestNO()} 를 부른다(대문자 NO, 2026-09-21 실측).
     * 길이·문자 규칙은 NICE 가 정하므로 우리가 만들지 않는다.
     */
    String generateRequestNo();

    /** 평문 → 암호문(EncodeData). */
    String encode(String plaindata);

    /**
     * 암호문 → 평문과 생성 시각. 실패하면
     * {@link com.shinyoung.recruit.exception.NiceVerificationException}.
     */
    NiceDecodeResult decode(String encodeData);

    /**
     * 응답 평문을 필드 맵으로 판다. 실제 구현은 모듈의 {@code fnParse} 에 위임한다
     * (레거시도 그렇게 쓴다 — 2026-09-21 확인). 응답 쪽 파싱 규격을 우리가 떠안지 않는다.
     *
     * <p>요청 조립은 이 인터페이스가 아니라 {@link NicePlaindataCodec#encode} 가 한다.
     * 모듈에는 조립 함수가 없다.
     */
    Map<String, String> parse(String plaindata);
}
