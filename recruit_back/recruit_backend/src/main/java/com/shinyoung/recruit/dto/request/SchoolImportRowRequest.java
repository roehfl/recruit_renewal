package com.shinyoung.recruit.dto.request;

/**
 * School import xlsx 의 한 행(파싱 raw). 모든 셀은 문자열로 읽는다. 형식/upsert 는 service 가 수행한다.
 *
 * @param rowNumber          스프레드시트 행 번호(1-based, header=1)
 * @param formulaCellPresent 행 내 formula 셀이 있으면 true(거부)
 */
public record SchoolImportRowRequest(
        int rowNumber,
        String schoolName,
        String schoolType,
        String schoolCategory,
        String educationMode,
        String region,
        String address,
        String countryCode,
        boolean formulaCellPresent
) {
}
