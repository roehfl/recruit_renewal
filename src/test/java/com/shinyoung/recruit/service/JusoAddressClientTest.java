package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JusoProperties;
import com.shinyoung.recruit.exception.AddressSearchException;
import com.shinyoung.recruit.exception.InvalidAddressSearchRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link JusoAddressClient} 단위 테스트. juso 호출을 {@link MockRestServiceServer}로 스텁한다.
 */
class JusoAddressClientTest {

    private JusoProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private JusoAddressClient client;

    @BeforeEach
    void setUp() {
        properties = new JusoProperties();
        properties.setBaseUrl("https://business.juso.go.kr/addrlink/addrLinkApi.do");
        properties.setConfmKey("test-confm-key");

        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new JusoAddressClient(restClient, properties);
    }

    @Test
    void 정상_응답을_파싱하고_레거시_파라미터에_resultType_json을_더해_GET_호출한다() {
        String json = """
                {"results":{"common":{"errorCode":"0","errorMessage":"정상","totalCount":"1","currentPage":"1","countPerPage":"10"},
                "juso":[{"roadAddr":"서울특별시 영등포구 여의대로 24","jibunAddr":"서울특별시 영등포구 여의도동 44","zipNo":"07320",
                "siNm":"서울특별시","sggNm":"영등포구","emdNm":"여의도동","bdNm":"전경련회관","engAddr":"24 Yeoui-daero"}]}}
                """;
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(containsString("confmKey=test-confm-key")))
                .andExpect(requestTo(containsString("currentPage=1")))
                .andExpect(requestTo(containsString("countPerPage=10")))
                .andExpect(requestTo(containsString("keyword=teheran")))
                .andExpect(requestTo(containsString("resultType=json")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        JusoApiResponse response = client.search("teheran", 1, 10);

        assertThat(response.results().common().errorCode()).isEqualTo("0");
        assertThat(response.results().juso()).hasSize(1);
        assertThat(response.results().juso().get(0).roadAddr()).isEqualTo("서울특별시 영등포구 여의대로 24");
        server.verify();
    }

    @Test
    void 승인키가_비어있으면_호출하지_않고_예외를_던진다() {
        properties.setConfmKey("");

        assertThatThrownBy(() -> client.search("teheran", 1, 10))
                .isInstanceOf(AddressSearchException.class);
        // 외부 호출이 발생하지 않았으므로 기대 없이 verify 통과.
        server.verify();
    }

    @Test
    void 서버측_juso_오류코드는_502_예외이고_juso_메시지를_노출하지_않는다() {
        String json = """
                {"results":{"common":{"errorCode":"E0005","errorMessage":"승인되지 않은 KEY 입니다.","totalCount":"0",
                "currentPage":"1","countPerPage":"10"},"juso":null}}
                """;
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("teheran", 1, 10))
                .isInstanceOf(AddressSearchException.class)
                .hasMessageNotContaining("KEY");   // 승인키 관련 원인을 클라이언트에 노출하지 않는다
    }

    @Test
    void 알_수_없는_juso_오류코드는_502로_보낸다() {
        // 사용자 탓으로 잘못 분류해 승인키 문제를 노출하는 것보다 502가 안전하다.
        String json = """
                {"results":{"common":{"errorCode":"E9999","errorMessage":"미확인 오류","totalCount":"0",
                "currentPage":"1","countPerPage":"10"},"juso":null}}
                """;
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("teheran", 1, 10))
                .isInstanceOf(AddressSearchException.class);
    }

    @Test
    void 검색어_거부_오류코드는_400_예외이고_juso_안내메시지를_그대로_노출한다() {
        // 실측: keyword=영등포구 처럼 행정구역명만 넣으면 juso가 E0006으로 거절한다.
        // 사용자가 검색어만 고치면 되는 상황이므로 502(서버 장애)로 보이면 안 된다.
        String json = """
                {"results":{"common":{"errorCode":"E0006","errorMessage":"주소를 상세히 입력해 주시기 바랍니다.",
                "totalCount":"0","currentPage":"1","countPerPage":"10"},"juso":null}}
                """;
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("영등포구", 1, 10))
                .isInstanceOf(InvalidAddressSearchRequestException.class)
                .hasMessage("주소를 상세히 입력해 주시기 바랍니다.");
    }

    @Test
    void E0015는_안전망으로_400_처리한다() {
        // AddressSearchService가 선차단하지만 juso가 상한을 낮추면 여기로 들어온다.
        String json = """
                {"results":{"common":{"errorCode":"E0015","errorMessage":"검색 범위를 초과하였습니다.",
                "totalCount":"0","currentPage":"1","countPerPage":"10"},"juso":null}}
                """;
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("중앙로", 500, 10))
                .isInstanceOf(InvalidAddressSearchRequestException.class);
    }

    @Test
    void 상위_API가_5xx면_예외를_던진다() {
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());

        assertThatThrownBy(() -> client.search("teheran", 1, 10))
                .isInstanceOf(AddressSearchException.class);
    }
}
