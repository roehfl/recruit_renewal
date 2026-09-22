package com.shinyoung.recruit.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** 로컬 HTTP 서버를 노드 대역으로 띄워 요청 모양·UTF-8·2xx 판정·연결 재사용을 확인한다. */
class TRNodeEngineTest {

    private record Received(String method, String contentType, String body, int remotePort) {
    }

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody = "";

    @BeforeEach
    void startNode() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", exchange -> {
            received.add(new Received(exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRemoteAddress().getPort()));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopNode() {
        server.stop(0);
    }

    private TRNodeEngine engine() {
        return new TRNodeEngine("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void node_url_뒤_query로_헤더_바디를_UTF8_JSON으로_보내고_2xx_본문을_그대로_돌려준다() throws Exception {
        responseBody = "{\"body\":{\"OutBlock1\":[{\"CNFR_YN\":\"Y\",\"UUID_ID\":\"U-1\",\"MSG\":\"정상\"}]}}";

        String response = engine().setNodeEngine("oseai_isms_001a", Map.of("InBlock1", List.of(Map.of("TEXT", "안녕하세요"))));

        assertThat(response).isEqualTo(responseBody);
        assertThat(received).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.contentType()).isEqualToIgnoringCase("text/plain;charset=UTF-8");
            JsonNode json = JsonMapper.builder().build().readTree(request.body());
            assertThat(json.path("header").path("queryName").asText()).isEqualTo("oseai_isms_001a");
            assertThat(json.path("body").path("InBlock1").path(0).path("TEXT").asText()).isEqualTo("안녕하세요");
            assertThat(json.path("queryDataHeader").has("SYS_USER_ID")).isTrue();
        });
    }

    @Test
    void 응답이_2xx가_아니면_빈_문자열() throws Exception {
        status = 500;
        responseBody = "error";

        assertThat(engine().setNodeEngine("oseai_isms_001a", Map.of())).isEmpty();
    }

    @Test
    void 호출마다_클라이언트를_새로_만들지_않고_연결을_재사용한다() throws Exception {
        responseBody = "{}";
        TRNodeEngine engine = engine();

        engine.setNodeEngine("oseai_isms_001a", Map.of());
        engine.setNodeEngine("oseai_isms_001a", Map.of());
        engine.setNodeEngine("oseai_isms_001a", Map.of());

        assertThat(received).extracting(Received::remotePort).containsOnly(received.get(0).remotePort());
    }
}
