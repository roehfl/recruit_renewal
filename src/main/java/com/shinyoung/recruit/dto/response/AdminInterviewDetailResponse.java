package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDateTime;
import java.util.List;

public record AdminInterviewDetailResponse(
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
        String memo,
        InterviewStatus status,
        List<AdminInterviewParticipantResponse> candidates,
        List<AdminInterviewParticipantResponse> interviewers
) {

    public static AdminInterviewDetailResponse from(
            Interview interview,
            List<InterviewParticipant> participants
    ) {
        List<AdminInterviewParticipantResponse> candidates = participants.stream()
                .filter(participant -> participant.getRole() == InterviewParticipantRole.CANDIDATE)
                .map(AdminInterviewParticipantResponse::from)
                .toList();
        List<AdminInterviewParticipantResponse> interviewers = participants.stream()
                .filter(participant -> participant.getRole() == InterviewParticipantRole.INTERVIEWER)
                .map(AdminInterviewParticipantResponse::from)
                .toList();

        return new AdminInterviewDetailResponse(
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
                interview.getMemo(),
                interview.getStatus(),
                candidates,
                interviewers
        );
    }
}
