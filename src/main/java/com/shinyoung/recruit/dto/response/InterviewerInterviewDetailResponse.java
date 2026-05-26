package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewerInterviewDetailResponse(
        Long interviewId,
        Long jobPostingId,
        String jobPostingTitle,
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
        List<InterviewerInterviewCandidateResponse> candidates,
        String guideMessage
) {

    public static InterviewerInterviewDetailResponse from(
            InterviewParticipant participant,
            List<InterviewerInterviewCandidateResponse> candidates
    ) {
        Interview interview = participant.getInterview();
        return new InterviewerInterviewDetailResponse(
                interview.getId(),
                interview.getJobPosting().getId(),
                interview.getJobPosting().getTitle(),
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
                candidates,
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
