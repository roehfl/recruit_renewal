package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;

import java.time.LocalDateTime;

/**
 * Applications export 전용 projection row. JPA 생성자 표현식으로 직접 조회되며, JPA entity/lazy
 * association을 export writer에 넘기지 않기 위한 평탄한 DTO다.
 *
 * <p>지원사항 컬럼의 원천이다. {@code applicantName}/{@code phoneNumber}/{@code email} 은 기본정보가 없는
 * 지원서의 fallback(지원 당시 이름 snapshot·계정 연락처)이다 — 기본정보가 있으면 assembler 가 그 값을 쓴다.
 * {@code ci}/{@code ciHash}/{@code password}는 절대 포함하지 않는다.
 */
public record ApplicationExportRow(
        Long applicationId,
        String applicantName,
        String phoneNumber,
        String email,
        String jobPostingTitle,
        JobPositionApplicationType applicationType,
        String jobPositionName,
        String jobTitle,
        String workLocationName,
        JobApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
