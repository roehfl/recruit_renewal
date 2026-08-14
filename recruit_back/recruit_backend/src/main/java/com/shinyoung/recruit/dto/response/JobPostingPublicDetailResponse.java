package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record JobPostingPublicDetailResponse(
        Long id,
        String title,
        JobPostingType postingType,
        String summary,
        String contentHtml,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        ReceptionStatus receptionStatus,
        boolean accepting,
        boolean pinned,
        List<JobPositionPublicResponse> jobPositions,
        ApplicationFormConfigPublicResponse applicationFormConfig,
        ApplicationFormRequiredPolicyResponse applicationFormRequiredPolicy,
        List<AttachmentRequirementPublicResponse> attachmentRequirements,
        List<JobPostingImageResponse> images
) {
    public static JobPostingPublicDetailResponse from(
            JobPosting jobPosting,
            LocalDateTime now,
            JobPostingQuestionPolicyCount questionPolicyCount,
            JobPostingAttachmentRequirementPolicyCount attachmentPolicyCount,
            List<JobPostingAttachmentRequirement> attachmentRequirements,
            List<JobPostingImageResponse> images
    ) {
        ReceptionStatus receptionStatus = ReceptionStatus.from(
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                now
        );
        return new JobPostingPublicDetailResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getPostingType(),
                jobPosting.getSummary(),
                jobPosting.getContentHtml(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                receptionStatus,
                JobPostingPublicListResponse.isAccepting(receptionStatus),
                jobPosting.isPinned(),
                jobPosting.getJobPositions().stream()
                        .map(JobPositionPublicResponse::from)
                        .sorted(Comparator.comparing(
                                JobPositionPublicResponse::sortOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        ))
                        .toList(),
                ApplicationFormConfigPublicResponse.from(jobPosting.getApplicationFormConfig()),
                ApplicationFormRequiredPolicyResponse.from(
                        jobPosting.getApplicationFormConfig(),
                        questionPolicyCount,
                        attachmentPolicyCount
                ),
                attachmentRequirements.stream()
                        .map(AttachmentRequirementPublicResponse::from)
                        .toList(),
                images
        );
    }
}
