package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.InterviewMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record InterviewCreateRequest(
        @NotNull(message = "Stage id is required.")
        Long stageId,

        @NotBlank(message = "Interview group name is required.")
        @Size(max = 100, message = "Interview group name must be 100 characters or less.")
        String groupName,

        @NotNull(message = "Interview startDateTime is required.")
        LocalDateTime startDateTime,

        @NotNull(message = "Interview endDateTime is required.")
        LocalDateTime endDateTime,

        @NotNull(message = "Interview method is required.")
        InterviewMethod method,

        @Size(max = 200, message = "Interview location name must be 200 characters or less.")
        String locationName,

        @Size(max = 100, message = "Interview room name must be 100 characters or less.")
        String roomName,

        @Size(max = 500, message = "Interview online meeting URL must be 500 characters or less.")
        String onlineMeetingUrl,

        @Size(max = 1000, message = "Interview memo must be 1000 characters or less.")
        String memo
) {
}
