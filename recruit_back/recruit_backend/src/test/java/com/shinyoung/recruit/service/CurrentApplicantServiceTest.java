package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.exception.AccessForbiddenException;
import com.shinyoung.recruit.exception.AuthenticationRequiredException;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentApplicantServiceTest {

    private final ApplicantRepository applicantRepository = mock(ApplicantRepository.class);
    private final CurrentApplicantService currentApplicantService = new CurrentApplicantService(applicantRepository);

    @Test
    void applicant_user_details_returns_applicant_id() {
        Applicant applicant = applicant("applicant01");
        applicant.setId(7L);
        when(applicantRepository.findByLoginId("applicant01")).thenReturn(Optional.of(applicant));

        Long applicantId = currentApplicantService.getCurrentApplicantId(applicantUserDetails("applicant01"));

        assertThat(applicantId).isEqualTo(7L);
    }

    @Test
    void null_user_details_fails_with_authentication_required() {
        // 미인증 → 401 매핑 예외
        assertThatThrownBy(() -> currentApplicantService.getCurrentApplicantId(null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void employee_user_details_fails_with_forbidden() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "employee01",
                "Recruit",
                "Employee User",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        // 인증은 됐지만 타입 불일치 → 403 매핑 예외
        assertThatThrownBy(() -> currentApplicantService.getCurrentApplicantId(userDetails))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void missing_applicant_fails() {
        when(applicantRepository.findByLoginId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentApplicantService.getCurrentApplicantId(applicantUserDetails("missing")))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    private CustomUserDetails applicantUserDetails(String loginId) {
        return CustomUserDetails.fromUser(
                applicant(loginId),
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
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
