package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.UnivInfoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제로 전송되는 요청 URI 를 고정한다.
 *
 * <p>서비스키의 {@code +}/{@code /} 가 인코딩되지 않은 채 나가면 상위 API 가 403 을 준다.
 * 값은 실제 키가 아닌 dummy 다.
 */
class UnivInfoSchoolClientTest {

    private static final String BASE_URL = "http://juso.go.kr/gov/openapi/tn_pubr_public_univ_info_api";
    private static final String EMPTY_BODY =
            "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":[]}}}";

    @Test
    void 명세대로_대문자_스네이크_파라미터를_전송한다() {
        // 파라미터명이 틀리면 상위 API 가 INVALID_REQUEST_PARAMETER_ERROR(코드 10)로 거절한다.
        assertThat(requestUriOf("abc%2Bdef%2Fghi%3D%3D", "대학"))
                .isEqualTo(BASE_URL + "?serviceKey=abc%2Bdef%2Fghi%3D%3D&pageNo=1&numOfRows=100&type=json"
                        + "&SCHL_NM=%EC%84%9C%EC%9A%B8&UNIV_SE_NM=%EB%8C%80%ED%95%99");
    }

    @Test
    void 대학구분이_없으면_해당_파라미터를_빼고_전송한다() {
        assertThat(requestUriOf("dummyKey", null))
                .contains("SCHL_NM=%EC%84%9C%EC%9A%B8")
                .doesNotContain("UNIV_SE_NM");
    }

    @Test
    void Decoding_표기_서비스키도_인코딩해서_전송한다() {
        assertThat(requestUriOf("abc+def/ghi==", null))
                .contains("serviceKey=abc%2Bdef%2Fghi%3D%3D")
                .doesNotContain("serviceKey=abc+def/ghi");
    }

    private String requestUriOf(String serviceKey, String univKind) {
        UnivInfoProperties properties = new UnivInfoProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceKey(serviceKey);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnivInfoSchoolClient client = new UnivInfoSchoolClient(builder.build(), properties);

        AtomicReference<String> captured = new AtomicReference<>();
        server.expect(request -> captured.set(request.getURI().toString()))
                .andRespond(MockRestResponseCreators.withSuccess(EMPTY_BODY, MediaType.APPLICATION_JSON));

        client.search("서울", univKind);
        return captured.get();
    }
}
