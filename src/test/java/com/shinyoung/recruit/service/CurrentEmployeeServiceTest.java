package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentEmployeeServiceTest {

    private final CurrentEmployeeService currentEmployeeService = new CurrentEmployeeService();

    @Test
    void employee_user_details_returns_username() {
        CustomUserDetails userDetails = employee("employee01");

        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);

        assertThat(actor).isEqualTo("employee01");
    }

    @Test
    void applicant_user_details_fails() {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant("applicant01"),
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );

        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeActor(userDetails))
                .isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void null_user_details_fails() {
        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeActor(null))
                .isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void blank_username_fails() {
        CustomUserDetails userDetails = employee(" ");

        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeActor(userDetails))
                .isInstanceOf(InvalidStageResultException.class);
    }

    private CustomUserDetails employee(String loginId) {
        return CustomUserDetails.fromLdap(
                loginId,
                "Recruit",
                "Employee User",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private Applicant applicant(String loginId) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicant;
    }
}
