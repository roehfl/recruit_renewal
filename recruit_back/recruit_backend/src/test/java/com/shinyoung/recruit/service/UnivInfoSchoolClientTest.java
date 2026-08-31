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
    /** 실제 응답은 최상위에 바로 header/body 가 오고(response 래퍼 없음), 목록은 body.items.item 이다. */
    private static final String EMPTY_BODY =
            "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":[]}}}";
    /** 검색 결과 없음. body 가 null 로 온다. */
    private static final String NO_DATA_BODY =
            "{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NODATA_ERROR\"},\"body\":null}";
    /** 실제 응답 형태. 항목명은 요청 파라미터(대문자 스네이크)와 달리 lowerCamel 이다. */
    private static final String TWO_ROW_BODY = """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":{"item":[
               {"schlNm":"평택대학교 물류·정보·경영대학원","univSeNm":"대학원","ctpvNm":"경기도"},
               {"schlNm":"평택대학교","univSeNm":"대학","ctpvNm":"경기도"}]}},
             "numOfRows":2,"pageNo":1,"totalCount":2}
            """;
    /** 행이 하나면 item 이 배열이 아니라 객체로 온다. */
    private static final String SINGLE_ROW_BODY = """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":{"item":
               {"schlNm":"평택대학교","univSeNm":"대학","ctpvNm":"경기도"}}},
             "numOfRows":1,"pageNo":1,"totalCount":1}
            """;

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

    @Test
    void 데이터_없음_응답은_빈_목록으로_처리한다() {
        // resultCode=03(NODATA_ERROR)은 오류가 아니라 검색 결과 없음이다. 502로 올리면 안 된다.
        UnivInfoProperties properties = new UnivInfoProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceKey("dummyKey");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnivInfoSchoolClient client = new UnivInfoSchoolClient(builder.build(), properties);
        server.expect(request -> { })
                .andRespond(MockRestResponseCreators.withSuccess(NO_DATA_BODY, MediaType.APPLICATION_JSON));

        assertThat(client.search("없는학교", "대학")).isEmpty();
    }

    @Test
    void body_items_item_의_lowerCamel_항목을_읽는다() {
        // 목록은 body.items.item 이고 항목명은 schlNm/univSeNm/ctpvNm 이다.
        var schools = searchWith(TWO_ROW_BODY, "평택", "대학");

        assertThat(schools).hasSize(1);
        assertThat(schools.get(0).schoolName()).isEqualTo("평택대학교");
        assertThat(schools.get(0).schoolCode()).isEqualTo("평택대학교");
        assertThat(schools.get(0).region()).isEqualTo("경기도");
    }

    @Test
    void 대학구분_필터가_없으면_모든_구분을_돌려준다() {
        assertThat(searchWith(TWO_ROW_BODY, "평택", null)).hasSize(2);
    }

    @Test
    void 행이_하나면_item_이_객체로_와도_읽는다() {
        assertThat(searchWith(SINGLE_ROW_BODY, "평택", "대학")).hasSize(1);
    }

    private java.util.List<com.shinyoung.recruit.dto.response.SchoolSearchResponse> searchWith(
            String responseBody, String keyword, String univKind) {
        UnivInfoProperties properties = new UnivInfoProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceKey("dummyKey");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnivInfoSchoolClient client = new UnivInfoSchoolClient(builder.build(), properties);
        server.expect(request -> { })
                .andRespond(MockRestResponseCreators.withSuccess(responseBody, MediaType.APPLICATION_JSON));

        return client.search(keyword, univKind);
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
