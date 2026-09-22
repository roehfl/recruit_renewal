package com.shinyoung.recruit.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 사내 TR 노드 호출. 폐쇄망 레거시의 WebClient 버전과 같게 동작한다: 클라이언트 하나를 공유해 연결을 재사용하고,
 * {node.url}/query 로 {header, body, queryDataHeader} JSON 을 POST 해 2xx 면 응답 본문을, 아니면 빈 문자열을 돌려준다.
 * 레거시와 달리 새 의존성(webflux) 없이 RestClient 를 쓰고, 연결·읽기 타임아웃을 둔다(없으면 발송 스레드가 무기한 대기).
 * 요청·응답은 UTF-8 로 주고받는다.
 */
@Component
public class TRNodeEngine {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final RestClient restClient;

    public TRNodeEngine(@Value("${node.url}") String nodeUrl) {
        // JDK HttpClient 기본값은 HTTP/2 업그레이드 시도라 레거시 노드와 어긋날 수 있어 HTTP/1.1 로 고정한다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (!nodeUrl.isBlank()) {
            builder.baseUrl(nodeUrl);
        }
        this.restClient = builder.build();
    }

    public String setNodeEngine(String trName, Map<String, Object> body) throws Exception {
        Map<String, Object> oltpParams = new HashMap<>();
        Map<String, Object> header = new HashMap<>();
        Map<String, Object> queryDataHeader = new HashMap<>();
        //헤더 데이터 정의
        header.put("queryName", trName);
        queryDataHeader.put("FRQRP_CHANNEL_MEDIA_TYPE", "XXX");
        queryDataHeader.put("SYS_USER_ID", "WWWWWW");

        //헤더_바디 데이터 조합
        oltpParams.put("header", header);
        oltpParams.put("body", body);
        oltpParams.put("queryDataHeader", queryDataHeader);

        String json = mapper.writeValueAsString(oltpParams);
        logger.info("*** TR_" + trName + " START ***");

        return restClient.post()
                .uri("/query")
                .contentType(TEXT_PLAIN_UTF8)
                .body(json.getBytes(StandardCharsets.UTF_8))
                .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()
                        ? new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)
                        : "");
    }
}
