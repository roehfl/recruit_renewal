package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record JobPostingCreateRequest(
        @NotBlank String title,
        @NotBlank String contentHtml,
        @NotNull LocalDateTime receptionStartDateTime,
        @NotNull LocalDateTime receptionEndDateTime,
        @NotEmpty List<@Valid JobPositionRequest> jobPositions,
        @NotNull @Valid ApplicationFormConfigRequest applicationFormConfig
) {
}
