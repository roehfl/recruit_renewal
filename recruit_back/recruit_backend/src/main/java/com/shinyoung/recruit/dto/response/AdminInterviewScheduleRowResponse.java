package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 면접 스케줄 한 행 = 면접(조)에 배정된 지원자 1명. 조 단위 값(일자·장소·시각·면접관)은 같은 조의 행마다 반복된다.
 *
 * @param candidateOrder    면접순서(지원자 참가자의 sortOrder)
 * @param interviewDateTime 면접 시각(= 면접 startDateTime)
 */
public record AdminInterviewScheduleRowResponse(
        Long interviewId,
        String groupName,
        Integer candidateOrder,
        LocalDateTime interviewDateTime,
        LocalDateTime arrivalDateTime,
        String locationName,
        List<AdminInterviewScheduleInterviewerResponse> interviewers,
        Long applicationId,
        String applicantName
) {

    public static AdminInterviewScheduleRowResponse from(
            InterviewParticipant candidate,
            List<AdminInterviewScheduleInterviewerResponse> interviewers
    ) {
        Interview interview = candidate.getInterview();
        JobApplication application = candidate.getJobApplication();
        return new AdminInterviewScheduleRowResponse(
                interview.getId(),
                interview.getGroupName(),
                candidate.getSortOrder(),
                interview.getStartDateTime(),
                interview.getArrivalDateTime(),
                interview.getLocationName(),
                interviewers,
                application.getId(),
                application.getApplicantNameSnapshot()
        );
    }
}
