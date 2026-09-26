package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 세트의 질문 id 전체를 새 순서대로. */
public record InterviewSupplementQuestionReorderRequest(
        @NotEmpty List<@NotNull Long> questionIds
) {
}
