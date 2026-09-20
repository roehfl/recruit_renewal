package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 자동 파기 설정 응답(Phase 10). {@code nextPurgeDate} 가 9999-12-31 이면 파기 예정이 없다는 뜻이다.
 */
public record RetentionScheduleResponse(
        boolean enabled,
        LocalDate nextPurgeDate,
        boolean hasPolicy,
        Integer retentionPeriodDays,
        LocalDateTime lastRunAt,
        RetentionScheduleRunResult lastRunResult,
        Long lastRunBatchId
) {
}
