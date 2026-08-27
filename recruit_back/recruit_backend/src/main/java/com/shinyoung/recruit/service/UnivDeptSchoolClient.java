package com.shinyoung.recruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.config.UnivDeptProperties;
import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.enumeration.SchoolSource;
import com.shinyoung.recruit.exception.SchoolSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 "전국대학별학과정보표준데이터" OpenAPI 호출 클라이언트
 * (전문대학·대학교·대학원 검색).
 *
 * <p>서비스키는 서버 설정({@link UnivDeptProperties#getServiceKey()})에서 주입하며 클라이언트에는 노출하지 않는다.
 *
 * <p><b>학과 단위</b> 데이터라 같은 학교가 학과 수만큼 반복된다. 학교 목록으로 쓰려면 호출자가
 * 학교 기준으로 중복을 제거해야 한다({@link SchoolSearchService}).
 *
 * <p>공공데이터포털 표준데이터 규격({@code api.data.go.kr/openapi/tn_pubr_*})을 따른다:
 * {@code serviceKey/pageNo/numOfRows/type=json} 요청, {@code response.header.resultCode} + {@code response.body.items} 응답.
 *
 * <p>응답 행의 필드명은 {@link JsonNode} 로 직접 읽는다. 아래 필드명 상수와 학교구분 값은
 * 인증키 발급 후 실제 응답으로 확정해야 한다. 검색 파라미터를 상위 API 가 무시할 수 있으므로
 * 학교명 포함 여부는 응답에서 한 번 더 거른다.
 */
@Component
public class UnivDeptSchoolClient {

    private static final Logger log = LoggerFactory.getLogger(UnivDeptSchoolClient.class);

    /** 정상 응답 코드. */
    private static final String SUCCESS_CODE = "00";

    /** 표준데이터 행의 학교명 필드. */
    private static final String FIELD_SCHOOL_NAME = "schoolNm";
    /** 표준데이터 행의 학교구분 필드(대학교/전문대학/대학원 등). */
    private static final String FIELD_SCHOOL_KIND = "schoolGbn";
    /** 표준데이터 행의 학교코드 필드. 없으면 학교명을 코드로 대체한다. */
    private static final String FIELD_SCHOOL_CODE = "schoolCd";
    /** 표준데이터 행의 소재지 필드. */
    private static final String FIELD_REGION = "ctprvnNm";

    private final RestClient univDeptRestClient;
    private final UnivDeptProperties properties;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public UnivDeptSchoolClient(RestClient univDeptRestClient, UnivDeptProperties properties) {
        this.univDeptRestClient = univDeptRestClient;
        this.properties = properties;
    }

    /**
     * 학교명으로 검색한다. 호출자는 공백이 아닌 keyword 를 넘긴다.
     *
     * @param keyword    학교명(부분일치)
     * @param schoolKind 학교구분. null 이면 구분 필터 없이 조회한다.
     * @throws SchoolSearchException baseUrl/서비스키 미설정, 네트워크/타임아웃, 파싱 실패
     */
    public List<SchoolSearchResponse> search(String keyword, String schoolKind) {
        String serviceKey = properties.getServiceKey();
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(serviceKey)) {
            // 설정 누락은 서버 문제 — 상세는 로깅만, 클라이언트에는 일반 메시지.
            log.error("대학 학과정보 OpenAPI 설정이 비어 있습니다. "
                    + "recruit.univ-dept.base-url(UNIV_DEPT_API_BASE_URL), "
                    + "recruit.univ-dept.service-key(UNIV_DEPT_API_SERVICE_KEY)를 확인하세요.");
            throw new SchoolSearchException("학교 검색 서비스가 설정되지 않았습니다.");
        }

        String body;
        try {
            body = univDeptRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", properties.getPageSize())
                            .queryParam("type", "json")
                            .queryParam(FIELD_SCHOOL_NAME, keyword)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("대학 학과정보 검색 호출 실패(keyword 길이={}): {}", keyword.length(), e.getMessage());
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        return parse(body, keyword, schoolKind);
    }

    private List<SchoolSearchResponse> parse(String body, String keyword, String schoolKind) {
        if (!StringUtils.hasText(body)) {
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("대학 학과정보 응답 파싱 실패: {}", e.getMessage());
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText("");
        if (!SUCCESS_CODE.equals(resultCode)) {
            // 서비스키 오류 등 상세는 로깅만 — 클라이언트에 원인/키 관련 메시지를 노출하지 않는다.
            log.warn("대학 학과정보 오류코드={}, message={}",
                    resultCode, response.path("header").path("resultMsg").asText(""));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode items = response.path("body").path("items");
        if (!items.isArray()) {
            log.warn("대학 학과정보 응답에 items 배열이 없습니다.");
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        List<SchoolSearchResponse> schools = new ArrayList<>();
        for (JsonNode row : items) {
            String schoolName = text(row, FIELD_SCHOOL_NAME);
            if (schoolName == null || !schoolName.contains(keyword)) {
                continue;
            }
            String kind = text(row, FIELD_SCHOOL_KIND);
            if (schoolKind != null && !schoolKind.equals(kind)) {
                continue;
            }
            String schoolCode = text(row, FIELD_SCHOOL_CODE);
            schools.add(new SchoolSearchResponse(
                    schoolCode == null ? schoolName : schoolCode,
                    schoolName,
                    SchoolSource.UNIV_DEPT,
                    text(row, FIELD_REGION)
            ));
        }
        return schools;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value.trim();
    }
}
