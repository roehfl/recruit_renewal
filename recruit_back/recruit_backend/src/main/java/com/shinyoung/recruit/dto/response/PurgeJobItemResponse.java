package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.PurgeJobItem;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.PurgeItemStatus;

public record PurgeJobItemResponse(
        Long id,
        Long applicationId,
        Long jobPostingId,
        PurgeItemStatus status,
        AuditReasonCode reasonCode
) {
    public static PurgeJobItemResponse from(PurgeJobItem item) {
        return new PurgeJobItemResponse(
                item.getId(),
                item.getApplicationId(),
                item.getJobPostingId(),
                item.getStatus(),
                item.getReasonCode()
        );
    }
}
