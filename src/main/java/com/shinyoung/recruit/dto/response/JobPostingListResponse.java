package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;

import java.time.LocalDateTime;

public record JobPostingListResponse(
        Long id,
        String title,
        JobPostingStatus status,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        LocalDateTime publishedAt,
        LocalDateTime closedAt
) {
    public static JobPostingListResponse from(JobPosting jobPosting) {
        return new JobPostingListResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getStatus(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                jobPosting.getPublishedAt(),
                jobPosting.getClosedAt()
        );
    }
}
