package com.shinyoung.recruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.config.UnivInfoProperties;
import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.enumeration.SchoolSource;
import com.shinyoung.recruit.exception.SchoolSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 "전국대학및전문대학정보표준데이터" OpenAPI 호출 클라이언트
 * (전문대학·대학교·대학원 학교 검색).
 *
 * <p>행 하나가 학교 하나이므로 학과 단위인 {@link UnivDeptSchoolClient} 와 달리 중복 제거가 필요 없다.
 *
 * <p>표준데이터 규격({@code serviceKey/pageNo/numOfRows/type=json} 요청,
 * {@code response.header.resultCode} + {@code response.body.items} 응답)을 따른다.
 *
 * <p>이 데이터셋은 학교 식별 코드를 제공하지 않을 가능성이 크다. 코드가 없으면 학교명을 코드로 쓴다
 * (학교별 통계 grouping 키가 학교명 문자열이 된다).
 *
 * <p>아래 필드명 상수와 학교구분 값은 서비스키 발급 후 실제 응답으로 확정해야 한다. 필드명이 어긋나
 * 결과가 통째로 사라지는 일을 막기 위해, 학교구분 필터는 해당 필드가 <b>있을 때만</b> 적용한다.
 */
@Component
public class UnivInfoSchoolClient {

    private static final Logger log = LoggerFactory.getLogger(UnivInfoSchoolClient.class);

    /** 정상 응답 코드. */
    private static final String SUCCESS_CODE = "00";

    /** 표준데이터 행의 학교명 필드. */
    private static final String FIELD_SCHOOL_NAME = "schoolNm";
    /** 표준데이터 행의 학교구분명 필드(대학교/전문대학 등). */
    private static final String FIELD_SCHOOL_KIND = "schoolGbnNm";
    /** 표준데이터 행의 학교 식별 코드 필드. 없으면 학교명을 코드로 대체한다. */
    private static final String FIELD_SCHOOL_CODE = "schoolCd";
    /** 표준데이터 행의 시도명 필드. */
    private static final String FIELD_REGION = "ctprvnNm";

    private final RestClient univInfoRestClient;
    private final UnivInfoProperties properties;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public UnivInfoSchoolClient(RestClient univInfoRestClient, UnivInfoProperties properties) {
        this.univInfoRestClient = univInfoRestClient;
        this.properties = properties;
    }

    /**
     * 학교명으로 검색한다. 호출자는 공백이 아닌 keyword 를 넘긴다.
     *
     * @param keyword    학교명(부분일치)
     * @param schoolKind 학교구분명. null 이면 구분 필터 없이 조회한다.
     * @throws SchoolSearchException 서비스키 미설정, 네트워크/타임아웃, 파싱 실패, 상위 API 오류코드
     */
    public List<SchoolSearchResponse> search(String keyword, String schoolKind) {
        String serviceKey = properties.getServiceKey();
        if (!StringUtils.hasText(serviceKey)) {
            // 설정 누락은 서버 문제 — 상세는 로깅만, 클라이언트에는 일반 메시지.
            log.error("대학 학교정보 OpenAPI 서비스키가 설정되지 않았습니다. "
                    + "recruit.univ-info.service-key(UNIV_INFO_API_SERVICE_KEY)를 확인하세요.");
            throw new SchoolSearchException("학교 검색 서비스가 설정되지 않았습니다.");
        }

        String body;
        try {
            body = univInfoRestClient.get()
                    .uri(requestUri(serviceKey, keyword))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("대학 학교정보 검색 호출 실패(keyword 길이={}): {}", keyword.length(), e.getMessage());
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        return parse(body, keyword, schoolKind);
    }

    /**
     * 요청 URI 를 직접 조립한다. Spring URI 빌더에 서비스키를 넘기면 {@code +} 를 그대로 두어
     * 쿼리스트링에서 공백으로 해석되므로(상위 API 403), 인코딩을 직접 통제한다.
     */
    private URI requestUri(String serviceKey, String keyword) {
        return URI.create("%s?serviceKey=%s&pageNo=1&numOfRows=%d&type=json&%s=%s".formatted(
                properties.getBaseUrl(),
                PublicDataServiceKey.toQueryValue(serviceKey),
                properties.getPageSize(),
                FIELD_SCHOOL_NAME,
                URLEncoder.encode(keyword, StandardCharsets.UTF_8)));
    }

    private List<SchoolSearchResponse> parse(String body, String keyword, String schoolKind) {
        if (!StringUtils.hasText(body)) {
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("대학 학교정보 응답 파싱 실패: {}", e.getMessage());
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText("");
        if (!SUCCESS_CODE.equals(resultCode)) {
            // 서비스키 오류 등 상세는 로깅만 — 클라이언트에 원인/키 관련 메시지를 노출하지 않는다.
            log.warn("대학 학교정보 오류코드={}, message={}",
                    resultCode, response.path("header").path("resultMsg").asText(""));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode items = response.path("body").path("items");
        if (!items.isArray()) {
            log.warn("대학 학교정보 응답에 items 배열이 없습니다.");
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        List<SchoolSearchResponse> schools = new ArrayList<>();
        for (JsonNode row : items) {
            // 검색 파라미터를 상위 API 가 무시할 수 있어 학교명 포함 여부를 한 번 더 거른다.
            String schoolName = text(row, FIELD_SCHOOL_NAME);
            if (schoolName == null || !schoolName.contains(keyword)) {
                continue;
            }
            String kind = text(row, FIELD_SCHOOL_KIND);
            if (schoolKind != null && kind != null && !schoolKind.equals(kind)) {
                continue;
            }
            String schoolCode = text(row, FIELD_SCHOOL_CODE);
            schools.add(new SchoolSearchResponse(
                    schoolCode == null ? schoolName : schoolCode,
                    schoolName,
                    SchoolSource.UNIV_INFO,
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
