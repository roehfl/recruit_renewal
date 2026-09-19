package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 테스트 발송. previewApplicationId 의 변수 값으로 치환해 testers 에게 보낸다. */
public record MessageTestSendRequest(
        @NotNull MessageType type,
        @NotNull Long jobPostingId,
        Long stageId,
        StageResultStatus resultStatus,
        String interviewGroup,
        JobApplicationStatus applicationStatus,
        @NotNull Long previewApplicationId,
        @NotEmpty @Size(max = 5) List<@NotNull @Valid MessageTesterRequest> testers,
        @NotNull @Valid MessageContentRequest content
) {
    public MessageTargetCondition toCondition() {
        return new MessageTargetCondition(type, jobPostingId, stageId, resultStatus, interviewGroup, applicationStatus);
    }
}
