package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDateTime;

public record InterviewerInterviewSummaryResponse(
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
        long candidateCount
) {

    public static InterviewerInterviewSummaryResponse from(InterviewParticipant participant, long candidateCount) {
        Interview interview = participant.getInterview();
        return new InterviewerInterviewSummaryResponse(
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
                candidateCount
        );
    }
}
