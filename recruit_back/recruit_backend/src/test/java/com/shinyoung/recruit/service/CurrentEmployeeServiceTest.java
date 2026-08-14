package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.exception.AccessForbiddenException;
import com.shinyoung.recruit.exception.AuthenticationRequiredException;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentEmployeeServiceTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final CurrentEmployeeService currentEmployeeService = new CurrentEmployeeService(employeeRepository);

    @Test
    void employee_user_details_returns_username() {
        CustomUserDetails userDetails = employee("employee01");

        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);

        assertThat(actor).isEqualTo("employee01");
    }

    @Test
    void applicant_user_details_fails_with_forbidden() {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant("applicant01"),
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );

        // 인증은 됐지만 타입 불일치 → 403 매핑 예외
        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeActor(userDetails))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void null_user_details_fails_with_authentication_required() {
        // 미인증 → 401 매핑 예외
        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeActor(null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void blank_username_fails() {
        CustomUserDetails userDetails = employee(" ");

        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeActor(userDetails))
                .isInstanceOf(InvalidStageResultException.class);
    }

    @Test
    void employee_user_details_returns_employee_id() {
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setLoginId("employee-id");
        when(employeeRepository.findByLoginId("employee-id")).thenReturn(Optional.of(employee));

        Long employeeId = currentEmployeeService.getCurrentEmployeeId(employee("employee-id"));

        assertThat(employeeId).isEqualTo(10L);
    }

    @Test
    void missing_employee_id_fails_for_interviewer_api() {
        when(employeeRepository.findByLoginId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeId(employee("missing")))
                .isInstanceOf(InvalidInterviewException.class);
    }

    @Test
    void null_user_details_fails_with_authentication_required_for_interviewer_api() {
        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeId(null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void applicant_user_details_fails_with_forbidden_for_interviewer_api() {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant("applicant02"),
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );

        assertThatThrownBy(() -> currentEmployeeService.getCurrentEmployeeId(userDetails))
                .isInstanceOf(AccessForbiddenException.class);
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
