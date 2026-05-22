package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;

import java.time.LocalDateTime;

public record JobPostingListResponse(
        Long id,
        String title,
        JobPostingType postingType,
        String summary,
        JobPostingStatus status,
        boolean visible,
        boolean pinned,
        Integer displayOrder,
        LocalDateTime displayStartDateTime,
        LocalDateTime displayEndDateTime,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        ReceptionStatus receptionStatus,
        boolean accepting,
        LocalDateTime publishedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int positionCount
) {
    public static JobPostingListResponse from(JobPosting jobPosting, LocalDateTime now) {
        return from(jobPosting, now, jobPosting.getJobPositions().size());
    }

    public static JobPostingListResponse from(JobPosting jobPosting, LocalDateTime now, int positionCount) {
        ReceptionStatus receptionStatus = ReceptionStatus.from(
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                now
        );
        return new JobPostingListResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getPostingType(),
                jobPosting.getSummary(),
                jobPosting.getStatus(),
                jobPosting.isVisible(),
                jobPosting.isPinned(),
                jobPosting.getDisplayOrder(),
                jobPosting.getDisplayStartDateTime(),
                jobPosting.getDisplayEndDateTime(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                receptionStatus,
                jobPosting.getStatus() == JobPostingStatus.PUBLISHED && receptionStatus == ReceptionStatus.ACCEPTING,
                jobPosting.getPublishedAt(),
                jobPosting.getClosedAt(),
                jobPosting.getCreatedAt(),
                jobPosting.getUpdatedAt(),
                positionCount
        );
    }
}
