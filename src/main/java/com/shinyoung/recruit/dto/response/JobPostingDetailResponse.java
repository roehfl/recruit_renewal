package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record JobPostingDetailResponse(
        Long id,
        String title,
        String contentHtml,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        JobPostingStatus status,
        LocalDateTime publishedAt,
        LocalDateTime closedAt,
        List<JobPositionResponse> jobPositions,
        ApplicationFormConfigResponse applicationFormConfig
) {
    public static JobPostingDetailResponse from(JobPosting jobPosting) {
        return new JobPostingDetailResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getContentHtml(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                jobPosting.getStatus(),
                jobPosting.getPublishedAt(),
                jobPosting.getClosedAt(),
                jobPosting.getJobPositions() .stream()
                        .sorted(Comparator.comparing(it -> it.getSortOrder()))
                        .map(JobPositionResponse::from)
                        .toList(),
                ApplicationFormConfigResponse.from(jobPosting.getApplicationFormConfig())
        );
    }
}
