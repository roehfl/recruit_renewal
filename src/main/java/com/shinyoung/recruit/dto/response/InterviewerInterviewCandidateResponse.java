package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;

public record InterviewerInterviewCandidateResponse(
        Long jobApplicationId,
        Long applicantId,
        String applicantName,
        Long positionId,
        String positionName,
        Integer sortOrder
) {

    public static InterviewerInterviewCandidateResponse from(InterviewParticipant participant) {
        JobApplication application = participant.getJobApplication();
        Applicant applicant = application.getApplicant();
        return new InterviewerInterviewCandidateResponse(
                application.getId(),
                applicant.getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                participant.getSortOrder()
        );
    }
}
