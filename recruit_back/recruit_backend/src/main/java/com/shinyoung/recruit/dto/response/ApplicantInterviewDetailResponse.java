package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDateTime;

public record ApplicantInterviewDetailResponse(
        Long interviewId,
        Long applicationId,
        Long jobPostingId,
        String jobPostingTitle,
        Long positionId,
        String positionName,
        Long stageId,
        String stageName,
        StageType stageType,
        String groupName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        InterviewMethod method,
        String locationName,
        String roomName,
        String onlineMeetingUrl,
        InterviewStatus status,
        boolean cancelled,
        String guideMessage
) {

    public static ApplicantInterviewDetailResponse from(InterviewParticipant participant) {
        Interview interview = participant.getInterview();
        JobApplication application = participant.getJobApplication();
        return new ApplicantInterviewDetailResponse(
                interview.getId(),
                application.getId(),
                interview.getJobPosting().getId(),
                interview.getJobPosting().getTitle(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                interview.getStage().getId(),
                interview.getStage().getStageName(),
                interview.getStage().getStageType(),
                interview.getGroupName(),
                interview.getStartDateTime(),
                interview.getEndDateTime(),
                interview.getMethod(),
                interview.getLocationName(),
                interview.getRoomName(),
                interview.getOnlineMeetingUrl(),
                interview.getStatus(),
                interview.isCancelled(),
                guideMessage(interview)
        );
    }

    private static String guideMessage(Interview interview) {
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            return "Interview schedule has been cancelled.";
        }
        return null;
    }
}
