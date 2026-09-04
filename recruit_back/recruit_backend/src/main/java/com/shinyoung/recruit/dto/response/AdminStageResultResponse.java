package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 전형 결과 행. 뒤쪽 6개 필드는 전형결과 관리 화면 그리드 열(2026-09-04)로, 학력·직전 단계 결과는
 * {@code AdminStageResultEnricher}가 배치 조회해 채운다. 지원자용 응답과 공유하지 않는다.
 *
 * @param decidedBy                 판정자(관리자 로그인 id). 미판정이면 null
 * @param workLocation              지원자가 선택한 근무지 표시명. 없으면 null
 * @param applicationType           모집분야 지원구분
 * @param finalEducationLevel       최고 학력 행의 학력(지원현황 조회와 같은 판정). 학력 없으면 null
 * @param finalSchoolName           최고 학력 행의 학교명
 * @param previousStageResultStatus 같은 공고에서 stageOrder가 바로 앞인 단계의 결과. 첫 단계·결과 없음이면 null
 */
public record AdminStageResultResponse(
        Long stageResultId,
        Long stageId,
        Long applicationId,
        String applicantName,
        Long jobPositionId,
        String jobPositionName,
        JobApplicationStatus applicationStatus,
        StageResultStatus resultStatus,
        BigDecimal score,
        String comment,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        String decidedBy,
        String workLocation,
        JobPositionApplicationType applicationType,
        EducationLevel finalEducationLevel,
        String finalSchoolName,
        StageResultStatus previousStageResultStatus
) {

    /** 배치 조회로 채우는 파생값. 값이 없는 필드는 null 이다. */
    public record Enrichment(
            EducationLevel finalEducationLevel,
            String finalSchoolName,
            StageResultStatus previousStageResultStatus
    ) {
    }

    public static AdminStageResultResponse from(StageResult result, Enrichment enrichment) {
        JobApplication application = result.getJobApplication();
        return new AdminStageResultResponse(
                result.getId(),
                result.getStage().getId(),
                application.getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                application.getStatus(),
                result.getResultStatus(),
                result.getScore(),
                result.getComment(),
                application.getSubmittedAt(),
                result.getDecidedAt(),
                result.getDecidedBy(),
                application.getWorkLocationNameSnapshot(),
                application.getJobPosition().getApplicationType(),
                enrichment.finalEducationLevel(),
                enrichment.finalSchoolName(),
                enrichment.previousStageResultStatus()
        );
    }
}
