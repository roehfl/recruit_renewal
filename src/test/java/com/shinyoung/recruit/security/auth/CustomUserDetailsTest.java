package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    @Test
    void applicant_username_is_login_id() {
        Applicant applicant = new Applicant("test-ci", HashUtil.sha256("test-ci"));
        applicant.setLoginId("applicant-login");
        applicant.setName("Applicant Name");
        applicant.setPassword("encoded-password");

        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );

        assertThat(userDetails.getUsername()).isEqualTo("applicant-login");
        assertThat(userDetails.getUserType()).isEqualTo(CustomUserDetails.USER_TYPE_APPLICANT);
        assertThat(userDetails.getName()).isEqualTo("Applicant Name");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
    }

    @Test
    void employee_user_type_uses_constant() {
        Employee employee = new Employee();
        employee.setLoginId("employee-login");
        employee.setName("Employee Name");

        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                employee,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );

        assertThat(userDetails.getUsername()).isEqualTo("employee-login");
        assertThat(userDetails.getUserType()).isEqualTo(CustomUserDetails.USER_TYPE_EMPLOYEE);
        assertThat(userDetails.getName()).isEqualTo("Employee Name");
    }

    @Test
    void ldap_username_is_login_id_and_employee_type() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "ldap-login",
                "IT",
                "Ldap User",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );

        assertThat(userDetails.getUsername()).isEqualTo("ldap-login");
        assertThat(userDetails.getUserType()).isEqualTo(CustomUserDetails.USER_TYPE_EMPLOYEE);
        assertThat(userDetails.getDeptName()).isEqualTo("IT");
        assertThat(userDetails.getName()).isEqualTo("Ldap User");
    }
}
