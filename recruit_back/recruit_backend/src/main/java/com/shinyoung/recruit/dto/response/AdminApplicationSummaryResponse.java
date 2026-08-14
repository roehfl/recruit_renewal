package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminApplicationSummaryResponse(
        Long applicationId,
        Long applicantId,
        String applicantNameSnapshot,
        Long jobPostingId,
        String jobPostingTitleSnapshot,
        Long jobPositionId,
        String jobPositionNameSnapshot,
        JobApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String jobGroup,
        String jobTitle,
        String workLocation,
        LocalDate birthDate,
        Integer age,
        EducationLevel finalEducationLevel,
        String finalSchoolName,
        StageType stageType,
        StageResultStatus stageResultStatus,
        String careerDescriptionDownloadUrl
) {

    /**
     * 목록 화면 파생 필드 묶음(서비스가 배치 조회로 채운다). {@code age} 는 조회 시점(오늘) 기준 만 나이,
     * {@code stageType}/{@code stageResultStatus} 는 검색 조건과 동일한 값 체계로 최신(stageOrder 최대) 전형 결과다.
     */
    public record Enrichment(
            LocalDate birthDate,
            Integer age,
            EducationLevel finalEducationLevel,
            String finalSchoolName,
            StageType stageType,
            StageResultStatus stageResultStatus,
            String careerDescriptionDownloadUrl
    ) {
        public static Enrichment empty() {
            return new Enrichment(null, null, null, null, null, null, null);
        }
    }

    public static AdminApplicationSummaryResponse from(JobApplication application, Enrichment enrichment) {
        return new AdminApplicationSummaryResponse(
                application.getId(),
                application.getApplicant().getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosting().getId(),
                application.getJobPostingTitleSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getWithdrawnAt(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.getJobPosition().getJobGroup(),
                application.getJobPosition().getJobTitle(),
                application.getJobPosition().getWorkLocation(),
                enrichment.birthDate(),
                enrichment.age(),
                enrichment.finalEducationLevel(),
                enrichment.finalSchoolName(),
                enrichment.stageType(),
                enrichment.stageResultStatus(),
                enrichment.careerDescriptionDownloadUrl()
        );
    }
}
