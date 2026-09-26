package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 보낸 질문의 답만 덮어쓴다. 빈 답은 지운다. */
public record InterviewSupplementAnswerSaveRequest(
        @NotNull List<@Valid InterviewSupplementAnswerItemRequest> answers
) {
}
