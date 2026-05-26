package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingPublicListProjection;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record JobPostingPublicListResponse(
        Long id,
        String title,
        JobPostingType postingType,
        String summary,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        ReceptionStatus receptionStatus,
        boolean accepting,
        boolean pinned,
        List<JobPositionPublicResponse> positions,
        ApplicationFormRequiredPolicyResponse applicationFormRequiredPolicy
) {
    public static JobPostingPublicListResponse from(
            JobPostingPublicListProjection jobPosting,
            List<JobPosition> positions,
            LocalDateTime now,
            JobPostingQuestionPolicyCount questionPolicyCount,
            JobPostingAttachmentRequirementPolicyCount attachmentPolicyCount
    ) {
        ReceptionStatus receptionStatus = ReceptionStatus.from(
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                now
        );
        return new JobPostingPublicListResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getPostingType(),
                jobPosting.getSummary(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                receptionStatus,
                isAccepting(receptionStatus),
                Boolean.TRUE.equals(jobPosting.getPinned()),
                positions.stream()
                        .map(JobPositionPublicResponse::from)
                        .toList(),
                ApplicationFormRequiredPolicyResponse.from(
                        jobPosting.getUseEducation(),
                        jobPosting.getRequireEducation(),
                        jobPosting.getUseCareer(),
                        jobPosting.getRequireCareer(),
                        jobPosting.getUseCertificate(),
                        jobPosting.getRequireCertificate(),
                        jobPosting.getUseLanguage(),
                        jobPosting.getRequireLanguage(),
                        jobPosting.getUseMilitary(),
                        jobPosting.getRequireMilitary(),
                        jobPosting.getUseAward(),
                        jobPosting.getRequireAward(),
                        jobPosting.getUseGapPeriod(),
                        jobPosting.getRequireGapPeriod(),
                        questionPolicyCount,
                        attachmentPolicyCount
                )
        );
    }

    static boolean isAccepting(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        return isAccepting(ReceptionStatus.from(start, end, now));
    }

    static boolean isAccepting(ReceptionStatus receptionStatus) {
        return receptionStatus == ReceptionStatus.ACCEPTING;
    }
}
