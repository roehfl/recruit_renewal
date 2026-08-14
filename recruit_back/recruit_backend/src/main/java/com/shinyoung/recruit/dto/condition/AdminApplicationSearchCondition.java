package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;

public record AdminApplicationSearchCondition(
        Long jobPostingId,
        Long jobPositionId,
        JobApplicationStatus status
) {
}
