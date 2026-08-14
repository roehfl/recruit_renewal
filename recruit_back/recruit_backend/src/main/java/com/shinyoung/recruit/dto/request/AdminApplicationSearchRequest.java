package com.shinyoung.recruit.dto.request;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 관리자 지원현황 조회 검색 조건(query string 바인딩). 모든 필드 optional 이며,
 * enum 계열은 기존 status 파라미터와 동일하게 String 으로 받아 서비스에서 파싱한다(대소문자/공백 허용, 오류 시 400).
 */
public record AdminApplicationSearchRequest(
        Long jobPositionId,
        String status,
        String applicationType,
        String jobGroup,
        String workLocation,
        String name,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthDateTo,
        String finalEducationLevel,
        String schoolName,
        String graduationStatus,
        String finalSchoolCondition,
        String certificateName,
        String languageName,
        String languageLevel,
        String stageType,
        String stageResultStatus
) {
}
