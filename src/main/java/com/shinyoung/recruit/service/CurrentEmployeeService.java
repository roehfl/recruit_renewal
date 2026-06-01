package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentEmployeeService {

    private final EmployeeRepository employeeRepository;

    public String getCurrentEmployeeActor(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new InvalidStageResultException("Employee authentication is required.");
        }
        if (!CustomUserDetails.USER_TYPE_EMPLOYEE.equals(userDetails.getUserType())) {
            throw new InvalidStageResultException("Only employee users can access admin commands.");
        }

        String actor = userDetails.getUsername();
        if (actor == null || actor.isBlank()) {
            throw new InvalidStageResultException("Employee actor is required.");
        }
        return actor;
    }

    public Long getCurrentEmployeeId(CustomUserDetails userDetails) {
        String loginId = getCurrentEmployeeActorForInterview(userDetails);
        return employeeRepository.findByLoginId(loginId)
                .map(Employee::getId)
                .orElseThrow(() -> new InvalidInterviewException("Employee user was not found."));
    }

    private String getCurrentEmployeeActorForInterview(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new InvalidInterviewException("Employee authentication is required.");
        }
        if (!CustomUserDetails.USER_TYPE_EMPLOYEE.equals(userDetails.getUserType())) {
            throw new InvalidInterviewException("Only employee users can access interviewer APIs.");
        }

        String actor = userDetails.getUsername();
        if (actor == null || actor.isBlank()) {
            throw new InvalidInterviewException("Employee actor is required.");
        }
        return actor;
    }
}
