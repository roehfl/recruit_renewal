package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosting;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record JobPostingPublicDetailResponse(
        Long id,
        String title,
        String contentHtml,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        boolean accepting,
        List<JobPositionPublicResponse> jobPositions,
        ApplicationFormConfigPublicResponse applicationFormConfig
) {
    public static JobPostingPublicDetailResponse from(JobPosting jobPosting, LocalDateTime now) {
        return new JobPostingPublicDetailResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getContentHtml(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                JobPostingPublicListResponse.isAccepting(
                        jobPosting.getReceptionStartDateTime(),
                        jobPosting.getReceptionEndDateTime(),
                        now
                ),
                jobPosting.getJobPositions().stream()
                        .map(JobPositionPublicResponse::from)
                        .sorted(Comparator.comparing(
                                JobPositionPublicResponse::sortOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        ))
                        .toList(),
                ApplicationFormConfigPublicResponse.from(jobPosting.getApplicationFormConfig())
        );
    }
}
