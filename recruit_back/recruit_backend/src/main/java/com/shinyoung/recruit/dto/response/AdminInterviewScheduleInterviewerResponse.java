package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Employee;

public record AdminInterviewScheduleInterviewerResponse(
        Long employeeId,
        String name,
        String loginId
) {

    public static AdminInterviewScheduleInterviewerResponse from(Employee employee) {
        return new AdminInterviewScheduleInterviewerResponse(employee.getId(), employee.getName(), employee.getLoginId());
    }
}
