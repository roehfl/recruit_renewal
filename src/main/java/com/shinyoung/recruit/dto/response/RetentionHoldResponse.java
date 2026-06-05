package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.RetentionHold;

import java.time.LocalDateTime;

/** hold 사유는 운영 판단 텍스트(지원자 PII 미포함 운영 규약) — 관리자 조회 응답에만 노출한다. */
public record RetentionHoldResponse(
        Long id,
        Long applicationId,
        String reason,
        String heldBy,
        LocalDateTime createdAt,
        LocalDateTime releasedAt,
        String releasedBy,
        boolean active
) {
    public static RetentionHoldResponse from(RetentionHold hold) {
        return new RetentionHoldResponse(
                hold.getId(),
                hold.getApplicationId(),
                hold.getReason(),
                hold.getHeldBy(),
                hold.getCreatedAt(),
                hold.getReleasedAt(),
                hold.getReleasedBy(),
                hold.isActive()
        );
    }
}
