package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 실제 발송. 조건 필드는 대상자 조회(GET /admin/messages/targets)와 같다. */
public record MessageSendRequest(
        @NotNull MessageType type,
        @NotNull Long jobPostingId,
        Long stageId,
        StageResultStatus resultStatus,
        String interviewGroup,
        JobApplicationStatus applicationStatus,
        @NotEmpty List<@NotNull Long> applicationIds,
        @NotNull @Valid MessageContentRequest content
) {
    public MessageTargetCondition toCondition() {
        return new MessageTargetCondition(type, jobPostingId, stageId, resultStatus, interviewGroup, applicationStatus);
    }
}
