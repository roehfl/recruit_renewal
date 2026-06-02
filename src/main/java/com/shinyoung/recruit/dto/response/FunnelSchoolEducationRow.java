package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.EducationLevel;

/**
 * SCHOOL dimension 산출 입력(projection): 제출 이력 코호트의 학력 한 건(applicationId, 학력수준, schoolId).
 * 응답 DTO가 아니라 집계 입력이며, JPA 생성자 표현식으로 직접 조회된다. 지원자별로 가장 높은 {@code educationLevel}의
 * {@code schoolId}를 "최종학력 1교"로 사용한다(없으면 미매칭).
 */
public record FunnelSchoolEducationRow(
        Long applicationId,
        EducationLevel educationLevel,
        Long schoolId
) {
}
