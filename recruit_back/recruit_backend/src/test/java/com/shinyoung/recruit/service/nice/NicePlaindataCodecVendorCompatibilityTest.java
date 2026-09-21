package com.shinyoung.recruit.service.nice;

import NiceID.Check.CPClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 우리 코덱의 직렬화가 NICE 벤더 모듈의 파서({@code CPClient.fnParse})와 호환되는지 본다.
 *
 * <p><b>{@link RealNiceClient} 는 더 이상 {@code fnParse} 를 쓰지 않는다</b> — 그쪽은 파싱 실패 시
 * 입력 조각을 stdout/stderr 에 직접 찍어서, 예외만 던지는 {@link NicePlaindataCodec} 으로 바꿨다.
 * 그래서 이 테스트의 역할도 바뀐다: 전에는 "Mock 이 실모듈과 같게 파싱하는지" 를 봤다면, 이제는
 * <b>"운영 파서(우리 코덱)가 NICE 규격과 호환되는지 지키는 가드"</b> 다 — 우리 코덱이 곧 운영 경로이므로,
 * 여기서 어긋나면 실인증이 조용히 실패한다.
 *
 * <p><b>복호화는 로컬 검증이 불가능하다</b> — 모듈이 자기 암호문을 복호화하지 못한다({@link RealNiceClientTest}).
 * <b>직렬화 호환성만은 여기서 검증한다.</b> 길이 규칙(문자 수·UTF-8·EUC-KR)이 벤더와 어긋나면
 * {@code fnParse} 가 null 을 돌려준다. 벤더 모듈은 {@code euc-kr} 을 하드코딩한다.
 *
 * <p>모듈 로딩에 {@code --add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED} 가 필요하다.
 * {@code build.gradle} 의 test 블록에 있다.
 *
 * <p>EUC-KR(KS X 1001 완성형 2,350자) 밖의 희귀 한글은 벤더 모듈에서 '?' 로 깨진다. 벤더 한계라
 * 여기서 단언하지 않는다.
 */
class NicePlaindataCodecVendorCompatibilityTest {

    private final NicePlaindataCodec codec = new NicePlaindataCodec();

    @SuppressWarnings("unchecked")
    private Map<String, String> vendorParse(String plaindata) {
        return new CPClient().fnParse(plaindata);
    }

    @Test
    void vendorParserReadsAsciiFieldsWeEncode() {
        // 요청 평문과 같은 구성. 빈 값, ':' 가 든 URL 을 포함한다.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", "EXAMPLE_20260921110000000_42");
        fields.put("SITECODE", "EXAMPLE");
        fields.put("AUTH_TYPE", "");
        fields.put("RTN_URL", "https://example.test/api/auth/nice/callback");
        fields.put("ERR_URL", "https://example.test/api/auth/nice/callback/error");
        fields.put("POPUP_GUBUN", "N");
        fields.put("CUSTOMIZE", "");

        assertEquals(fields, vendorParse(codec.encode(fields)));
    }

    @Test
    void vendorParserReadsKoreanFieldsWeEncode() {
        // 한글 값에서 길이 규칙이 갈린다. UTF-8 길이(9)로 쓰면 벤더 파서가 null 을 돌려준다.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("REQ_SEQ", "EXAMPLE_20260921110000000_42");
        fields.put("NAME", "홍길동");
        fields.put("MOBILE_NO", "01000000000");
        fields.put("CI", "test-ci-value");

        assertEquals(fields, vendorParse(codec.encode(fields)));
    }
}
