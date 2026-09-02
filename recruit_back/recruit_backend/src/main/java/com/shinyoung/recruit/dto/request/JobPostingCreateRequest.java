package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.JobPostingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record JobPostingCreateRequest(
        @NotBlank String title,
        JobPostingType postingType,
        @Size(max = 500) String summary,
        String contentHtml,
        @NotNull LocalDateTime receptionStartDateTime,
        @NotNull LocalDateTime receptionEndDateTime,
        LocalDateTime displayStartDateTime,
        LocalDateTime displayEndDateTime,
        Boolean visible,
        Boolean pinned,
        @Min(0) Integer displayOrder,
        @NotEmpty List<@Valid JobPositionRequest> jobPositions,
        /** 생략 가능. 없으면 서버가 기본 설정을 만든다. 지원서 양식은 전용 API가 단일 출처다. */
        @Valid ApplicationFormConfigRequest applicationFormConfig
) {
    public JobPostingCreateRequest(
            String title,
            String contentHtml,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            List<JobPositionRequest> jobPositions,
            ApplicationFormConfigRequest applicationFormConfig
    ) {
        this(
                title,
                null,
                null,
                contentHtml,
                receptionStartDateTime,
                receptionEndDateTime,
                null,
                null,
                null,
                null,
                null,
                jobPositions,
                applicationFormConfig
        );
    }
}
