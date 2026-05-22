package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record JobPostingDetailResponse(
        Long id,
        String title,
        JobPostingType postingType,
        String summary,
        String contentHtml,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        ReceptionStatus receptionStatus,
        boolean accepting,
        JobPostingStatus status,
        boolean visible,
        boolean pinned,
        Integer displayOrder,
        LocalDateTime displayStartDateTime,
        LocalDateTime displayEndDateTime,
        LocalDateTime publishedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int positionCount,
        List<JobPositionResponse> jobPositions,
        ApplicationFormConfigResponse applicationFormConfig
) {
    public static JobPostingDetailResponse from(JobPosting jobPosting, LocalDateTime now) {
        ReceptionStatus receptionStatus = ReceptionStatus.from(
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                now
        );
        return new JobPostingDetailResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getPostingType(),
                jobPosting.getSummary(),
                jobPosting.getContentHtml(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                receptionStatus,
                jobPosting.getStatus() == JobPostingStatus.PUBLISHED && receptionStatus == ReceptionStatus.ACCEPTING,
                jobPosting.getStatus(),
                jobPosting.isVisible(),
                jobPosting.isPinned(),
                jobPosting.getDisplayOrder(),
                jobPosting.getDisplayStartDateTime(),
                jobPosting.getDisplayEndDateTime(),
                jobPosting.getPublishedAt(),
                jobPosting.getClosedAt(),
                jobPosting.getCreatedAt(),
                jobPosting.getUpdatedAt(),
                jobPosting.getJobPositions().size(),
                jobPosting.getJobPositions().stream()
                        .sorted(Comparator.comparing(it -> it.getSortOrder()))
                        .map(JobPositionResponse::from)
                        .toList(),
                ApplicationFormConfigResponse.from(jobPosting.getApplicationFormConfig())
        );
    }
}
