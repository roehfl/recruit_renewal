package com.shinyoung.recruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.config.NeisProperties;
import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.enumeration.SchoolSource;
import com.shinyoung.recruit.exception.SchoolSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * NEIS 학교기본정보 OpenAPI 호출 클라이언트(고등학교 검색).
 *
 * <p>인증키는 서버 설정({@link NeisProperties#getApiKey()})에서 주입하며 클라이언트에는 노출하지 않는다.
 *
 * <p>NEIS 응답은 정상일 때 {@code {"schoolInfo":[{"head":[...]},{"row":[...]}]}} 형태이고,
 * 결과가 없거나 오류일 때는 최상위에 {@code RESULT} 만 담겨 형태가 달라진다. 고정 스키마 record 로는
 * 두 형태를 함께 받기 어려워 {@link JsonNode} 로 직접 순회한다.
 *
 * <p>ObjectMapper 는 DI 로 주입받지 않고 자체 인스턴스를 만든다({@link JusoAddressClient} 선례).
 */
@Component
public class NeisSchoolClient {

    private static final Logger log = LoggerFactory.getLogger(NeisSchoolClient.class);

    /** 정상 응답 코드. */
    private static final String SUCCESS_CODE = "INFO-000";
    /** 조건에 맞는 데이터 없음. 오류가 아니라 빈 결과로 취급한다. */
    private static final String NO_DATA_CODE = "INFO-200";

    private final RestClient neisRestClient;
    private final NeisProperties properties;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public NeisSchoolClient(RestClient neisRestClient, NeisProperties properties) {
        this.neisRestClient = neisRestClient;
        this.properties = properties;
    }

    /**
     * 학교명으로 검색한다. 호출자는 공백이 아닌 keyword 를 넘긴다.
     *
     * @param keyword    학교명(부분일치)
     * @param schoolKind 학교종류명({@code SCHUL_KND_SC_NM}). 예: 고등학교
     * @throws SchoolSearchException 인증키 미설정, 네트워크/타임아웃, 파싱 실패, NEIS 오류코드
     */
    public List<SchoolSearchResponse> search(String keyword, String schoolKind) {
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            // 설정 누락은 서버 문제 — 상세는 로깅만, 클라이언트에는 일반 메시지.
            log.error("NEIS 인증키가 설정되지 않았습니다. recruit.neis.api-key(NEIS_API_KEY)를 확인하세요.");
            throw new SchoolSearchException("학교 검색 서비스가 설정되지 않았습니다.");
        }

        String body;
        try {
            body = neisRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("KEY", apiKey)
                            .queryParam("Type", "json")
                            .queryParam("pIndex", 1)
                            .queryParam("pSize", properties.getPageSize())
                            .queryParam("SCHUL_NM", keyword)
                            .queryParam("SCHUL_KND_SC_NM", schoolKind)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 상위 API 는 실패 사유를 본문에 담아준다(예: SERVICE_KEY_IS_NOT_REGISTERED_ERROR).
            log.warn("NEIS 학교 검색 호출 실패(keyword 길이={}): 상태={} 본문={}",
                    keyword.length(), e.getStatusCode(), snippet(e.getResponseBodyAsString()));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        } catch (RestClientException e) {
            log.warn("NEIS 학교 검색 호출 실패(keyword 길이={}): {}", keyword.length(), e.getMessage());
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        return parse(body);
    }

    private List<SchoolSearchResponse> parse(String body) {
        if (!StringUtils.hasText(body)) {
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("NEIS 응답 파싱 실패: {}, 본문={}", e.getMessage(), snippet(body));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        // 결과 없음/오류는 최상위 RESULT 로만 온다.
        JsonNode topResult = root.path("RESULT");
        if (!topResult.isMissingNode()) {
            checkResultCode(topResult);
            return List.of();
        }

        JsonNode schoolInfo = root.path("schoolInfo");
        if (!schoolInfo.isArray()) {
            log.warn("NEIS 응답에 schoolInfo 배열이 없습니다. 본문={}", snippet(body));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        List<SchoolSearchResponse> schools = new ArrayList<>();
        for (JsonNode section : schoolInfo) {
            // head 안에도 RESULT 가 들어온다. 정상 코드가 아니면 여기서 걸러진다.
            for (JsonNode head : section.path("head")) {
                JsonNode headResult = head.path("RESULT");
                if (!headResult.isMissingNode()) {
                    checkResultCode(headResult);
                }
            }
            for (JsonNode row : section.path("row")) {
                schools.add(new SchoolSearchResponse(
                        text(row, "SD_SCHUL_CODE"),
                        text(row, "SCHUL_NM"),
                        SchoolSource.NEIS,
                        text(row, "LCTN_SC_NM")
                ));
            }
        }
        return schools;
    }

    /**
     * RESULT 코드를 검사한다. 정상/데이터 없음이면 그대로 통과시키고,
     * 그 밖의 코드(인증키 거부 등)는 502 로 감춘다.
     */
    private void checkResultCode(JsonNode result) {
        String code = result.path("CODE").asText("");
        if (SUCCESS_CODE.equals(code) || NO_DATA_CODE.equals(code)) {
            return;
        }
        // 인증키 오류 등 상세는 로깅만 — 클라이언트에 원인/키 관련 메시지를 노출하지 않는다.
        log.warn("NEIS 오류코드={}, message={}", code, result.path("MESSAGE").asText(""));
        throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }

    /** 로그용 응답 본문 앞부분. 본문에는 인증키가 들어가지 않는다. */
    private static String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "(빈 본문)";
        }
        String trimmed = body.strip();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...(생략)";
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value.trim();
    }
}
