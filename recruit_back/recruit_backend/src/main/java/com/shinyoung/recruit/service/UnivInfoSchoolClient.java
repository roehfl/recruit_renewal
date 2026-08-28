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
import org.springframework.web.client.RestClientResponseException;

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
 * <p>요청/응답 항목명은 포털 API 명세 기준이다(대문자 스네이크). 학교명 검색은 {@code SCHL_NM} 파라미터로
 * 상위 API 에 맡기고, 응답에서 한 번 더 학교명 포함 여부를 확인한다.
 *
 * <p>표준데이터 규격({@code serviceKey/pageNo/numOfRows/type=json} 요청,
 * {@code response.header.resultCode} + {@code response.body.items} 응답)을 따른다.
 *
 * <p>이 데이터셋에는 학교 식별 코드가 없다(제공 항목은 제공기관코드뿐). 따라서 {@code schoolCode} 는
 * 학교명을 그대로 쓴다 — 학교별 통계 grouping 키가 학교명 문자열이 된다.
 */
@Component
public class UnivInfoSchoolClient {

    private static final Logger log = LoggerFactory.getLogger(UnivInfoSchoolClient.class);

    /** 정상 응답 코드. */
    private static final String SUCCESS_CODE = "00";
    /** 조건에 맞는 데이터 없음. 오류가 아니라 빈 결과로 취급한다. */
    private static final String NO_DATA_CODE = "03";

    /** 학교명. 요청 파라미터이자 응답 항목이다. */
    private static final String FIELD_SCHOOL_NAME = "SCHL_NM";
    /** 대학구분명(대학 / 대학원 / 전문대학). 학력 구분 필터에 쓴다. */
    private static final String FIELD_UNIV_KIND = "UNIV_SE_NM";
    /** 시도명. */
    private static final String FIELD_REGION = "CTPV_NM";

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
     * @param keyword  학교명(부분일치)
     * @param univKind 대학구분명({@code UNIV_SE_NM}). null 이면 구분 필터 없이 조회한다.
     * @throws SchoolSearchException 서비스키 미설정, 네트워크/타임아웃, 파싱 실패, 상위 API 오류코드
     */
    public List<SchoolSearchResponse> search(String keyword, String univKind) {
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
                    .uri(requestUri(serviceKey, keyword, univKind))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 상위 API 는 실패 사유를 본문에 담아준다(예: SERVICE_KEY_IS_NOT_REGISTERED_ERROR).
            log.warn("대학 학교정보 검색 호출 실패(keyword 길이={}): 상태={} 본문={}",
                    keyword.length(), e.getStatusCode(), snippet(e.getResponseBodyAsString()));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        } catch (RestClientException e) {
            log.warn("대학 학교정보 검색 호출 실패(keyword 길이={}): {}", keyword.length(), e.getMessage());
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        return parse(body, keyword, univKind);
    }

    /**
     * 요청 URI 를 직접 조립한다. Spring URI 빌더에 서비스키를 넘기면 {@code +} 를 그대로 두어
     * 쿼리스트링에서 공백으로 해석되므로(상위 API 403), 인코딩을 직접 통제한다.
     *
     * <p>파라미터명은 명세상 대문자 스네이크다. 잘못된 이름을 보내면
     * {@code INVALID_REQUEST_PARAMETER_ERROR}(코드 10)로 거절된다.
     */
    private URI requestUri(String serviceKey, String keyword, String univKind) {
        String uri = "%s?serviceKey=%s&pageNo=1&numOfRows=%d&type=json&%s=%s".formatted(
                properties.getBaseUrl(),
                PublicDataServiceKey.toQueryValue(serviceKey),
                properties.getPageSize(),
                FIELD_SCHOOL_NAME,
                URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        if (univKind != null) {
            uri += "&%s=%s".formatted(FIELD_UNIV_KIND, URLEncoder.encode(univKind, StandardCharsets.UTF_8));
        }
        return URI.create(uri);
    }

    private List<SchoolSearchResponse> parse(String body, String keyword, String univKind) {
        if (!StringUtils.hasText(body)) {
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("대학 학교정보 응답 파싱 실패: {}, 본문={}", e.getMessage(), snippet(body));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
        }

        String resultCode = root.path("header").path("resultCode").asText("");
        if (NO_DATA_CODE.equals(resultCode)) {
            // 검색 결과 없음은 정상 흐름이다(body 도 null 로 온다).
            return List.of();
        }
        if (!SUCCESS_CODE.equals(resultCode)) {
            // 서비스키 오류 등 상세는 로깅만 — 클라이언트에 원인/키 관련 메시지를 노출하지 않는다.
            log.warn("대학 학교정보 오류코드={}, message={}, 본문={}",
                    resultCode, root.path("header").path("resultMsg").asText(""), snippet(body));
            throw new SchoolSearchException("학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode items = root.path("body").path("items");
        if (!items.isArray()) {
            // 정상 코드인데 목록이 없으면 응답 구조가 바뀐 것이다. 검색을 막지는 않고 로그로 드러낸다.
            log.warn("대학 학교정보 응답에 items 배열이 없습니다. 본문={}", snippet(body));
            return List.of();
        }

        List<SchoolSearchResponse> schools = new ArrayList<>();
        for (JsonNode row : items) {
            // 상위 API 가 검색 조건을 어떻게 해석하든 결과가 어긋나지 않도록 응답에서 한 번 더 거른다.
            String schoolName = text(row, FIELD_SCHOOL_NAME);
            if (schoolName == null || !schoolName.contains(keyword)) {
                continue;
            }
            String kind = text(row, FIELD_UNIV_KIND);
            if (univKind != null && kind != null && !univKind.equals(kind)) {
                continue;
            }
            // 이 데이터셋에는 학교 식별 코드가 없어 학교명을 코드로 쓴다.
            schools.add(new SchoolSearchResponse(
                    schoolName,
                    schoolName,
                    SchoolSource.UNIV_INFO,
                    text(row, FIELD_REGION)
            ));
        }
        return schools;
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
