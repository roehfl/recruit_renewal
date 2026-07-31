package com.shinyoung.recruit.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * juso.go.kr 도로명주소 검색 API(resultType=json)의 원본 응답 파싱 모델(내부 전용).
 *
 * <p>juso는 숫자(totalCount 등)도 문자열로 내려주므로 String으로 받고, 상위에서 정제 DTO로 변환한다.
 * 알 수 없는 필드는 무시한다(스키마 확장 내성).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record JusoApiResponse(Results results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Results(Common common, List<Juso> juso) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Common(
            String totalCount,
            String currentPage,
            String countPerPage,
            String errorCode,
            String errorMessage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Juso(
            String roadAddr,
            String roadAddrPart1,
            String roadAddrPart2,
            String jibunAddr,
            String engAddr,
            String zipNo,
            String siNm,
            String sggNm,
            String emdNm,
            String bdNm
    ) {
    }
}
