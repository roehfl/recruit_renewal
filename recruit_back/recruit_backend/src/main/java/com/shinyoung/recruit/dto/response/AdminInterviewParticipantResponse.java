package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;

public record AdminInterviewParticipantResponse(
        Long participantId,
        InterviewParticipantRole role,
        Long jobApplicationId,
        Long applicantId,
        String applicantName,
        Long jobPositionId,
        String jobPositionName,
        Long employeeId,
        String employeeName,
        String departmentName,
        InterviewParticipantStatus participantStatus,
        Integer sortOrder
) {

    public static AdminInterviewParticipantResponse from(InterviewParticipant participant) {
        if (participant.isCandidate()) {
            JobApplication application = participant.getJobApplication();
            return new AdminInterviewParticipantResponse(
                    participant.getId(),
                    participant.getRole(),
                    application.getId(),
                    application.getApplicant().getId(),
                    application.getApplicantNameSnapshot(),
                    application.getJobPosition().getId(),
                    application.getJobPositionNameSnapshot(),
                    null,
                    null,
                    null,
                    participant.getParticipantStatus(),
                    participant.getSortOrder()
            );
        }

        Employee employee = participant.getEmployee();
        return new AdminInterviewParticipantResponse(
                participant.getId(),
                participant.getRole(),
                null,
                null,
                null,
                null,
                null,
                employee.getId(),
                employee.getName(),
                employee.getDeptName(),
                participant.getParticipantStatus(),
                participant.getSortOrder()
        );
    }
}
