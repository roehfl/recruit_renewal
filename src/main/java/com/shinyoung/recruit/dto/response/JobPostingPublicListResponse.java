package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.repository.JobPostingPublicListProjection;

import java.time.LocalDateTime;

public record JobPostingPublicListResponse(
        Long id,
        String title,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        boolean accepting
) {
    public static JobPostingPublicListResponse from(JobPostingPublicListProjection jobPosting, LocalDateTime now) {
        return new JobPostingPublicListResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                isAccepting(jobPosting.getReceptionStartDateTime(), jobPosting.getReceptionEndDateTime(), now)
        );
    }

    static boolean isAccepting(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        return !now.isBefore(start) && !now.isAfter(end);
    }
}
