package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        LocalDateTime decidedAt
) {

    public static AdminStageResultResponse from(StageResult result) {
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
                result.getDecidedAt()
        );
    }
}
