package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** RetentionPolicy 생성/수정 요청(Phase 09c). {@code jobPostingId} null = 전역 기본 정책. */
public record RetentionPolicyRequest(
        Long jobPostingId,

        @NotNull(message = "retentionPeriodDays는 필수입니다.")
        @Min(value = 1, message = "retentionPeriodDays는 1 이상이어야 합니다.")
        Integer retentionPeriodDays,

        @NotNull(message = "baselineType은 필수입니다.")
        RetentionBaselineType baselineType,

        @NotNull(message = "enabled는 필수입니다.")
        Boolean enabled,

        LocalDateTime effectiveFrom,

        LocalDateTime effectiveTo
) {
}
