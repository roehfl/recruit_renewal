package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;

import java.time.LocalDateTime;

/**
 * Applications export 전용 projection row. JPA 생성자 표현식으로 직접 조회되며, JPA entity/lazy
 * association을 export writer에 넘기지 않기 위한 평탄한 DTO다.
 *
 * <p>연락처(phoneNumber/email)는 목록 응답({@code AdminApplicationSummaryResponse})에 없으므로
 * export에서 {@code Applicant}로부터 추가 조회한다. {@code ci}/{@code ciHash}/{@code password}는
 * 절대 포함하지 않는다.
 */
public record ApplicationExportRow(
        Long applicationId,
        String applicantName,
        String phoneNumber,
        String email,
        String jobPostingTitle,
        String jobPositionName,
        JobApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
