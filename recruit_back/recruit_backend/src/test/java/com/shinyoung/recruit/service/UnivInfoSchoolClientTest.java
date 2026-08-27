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
    void Encoding_표기_서비스키를_그대로_전송한다() {
        assertThat(requestUriOf("abc%2Bdef%2Fghi%3D%3D"))
                .isEqualTo(BASE_URL + "?serviceKey=abc%2Bdef%2Fghi%3D%3D"
                        + "&pageNo=1&numOfRows=50&type=json&schoolNm=%EC%84%9C%EC%9A%B8");
    }

    @Test
    void Decoding_표기_서비스키도_인코딩해서_전송한다() {
        assertThat(requestUriOf("abc+def/ghi=="))
                .contains("serviceKey=abc%2Bdef%2Fghi%3D%3D")
                .doesNotContain("serviceKey=abc+def/ghi");
    }

    private String requestUriOf(String serviceKey) {
        UnivInfoProperties properties = new UnivInfoProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceKey(serviceKey);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnivInfoSchoolClient client = new UnivInfoSchoolClient(builder.build(), properties);

        AtomicReference<String> captured = new AtomicReference<>();
        server.expect(request -> captured.set(request.getURI().toString()))
                .andRespond(MockRestResponseCreators.withSuccess(EMPTY_BODY, MediaType.APPLICATION_JSON));

        client.search("서울", null);
        return captured.get();
    }
}
