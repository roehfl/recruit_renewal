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

/**
 * 지원서 양식(applicationFormConfig)은 이 요청에 포함하지 않는다.
 * 공고 수정 화면이 낡은 값으로 설정을 덮어쓰는 것을 막기 위해
 * POST /admin/job-postings/{id}/application-form-config 를 단일 출처로 둔다.
 */
public record JobPostingUpdateRequest(
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
        @NotEmpty List<@Valid JobPositionRequest> jobPositions
) {
    public JobPostingUpdateRequest(
            String title,
            String contentHtml,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            List<JobPositionRequest> jobPositions
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
                jobPositions
        );
    }
}
