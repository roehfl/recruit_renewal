package com.shinyoung.recruit.service;

import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.springframework.stereotype.Service;

@Service
public class CurrentEmployeeService {

    public String getCurrentEmployeeActor(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new InvalidStageResultException("Employee authentication is required.");
        }
        if (!CustomUserDetails.USER_TYPE_EMPLOYEE.equals(userDetails.getUserType())) {
            throw new InvalidStageResultException("Only employee users can access admin StageResult commands.");
        }

        String actor = userDetails.getUsername();
        if (actor == null || actor.isBlank()) {
            throw new InvalidStageResultException("Employee actor is required.");
        }
        return actor;
    }
}
