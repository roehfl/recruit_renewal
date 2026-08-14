package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosting;

import java.time.LocalDateTime;

public record RetentionAnchorResponse(
        Long jobPostingId,
        LocalDateTime hiringEndedAt
) {
    public static RetentionAnchorResponse from(JobPosting jobPosting) {
        return new RetentionAnchorResponse(jobPosting.getId(), jobPosting.getHiringEndedAt());
    }
}
