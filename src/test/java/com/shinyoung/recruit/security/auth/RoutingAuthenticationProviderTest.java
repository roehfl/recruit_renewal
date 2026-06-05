package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * LDAP JIT 동시 생성 race 복구 단위 테스트. 실제 LDAP에 연결하지 않는다(provider 전부 mock).
 */
@ExtendWith(MockitoExtension.class)
class RoutingAuthenticationProviderTest {

    @Mock
    private LdapAuthenticationProvider ldapProvider;

    @Mock
    private DaoAuthenticationProvider daoProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private RoutingAuthenticationProvider routingAuthenticationProvider;

    @BeforeEach
    void setUp() {
        routingAuthenticationProvider = new RoutingAuthenticationProvider(
                ldapProvider, daoProvider, userRepository, employeeRepository);
    }

    private Authentication loginRequest(String loginId) {
        return new UsernamePasswordAuthenticationToken(loginId, "ldap-password");
    }

    private Authentication ldapSuccess(String loginId) {
        CustomUserDetails ldapUser = CustomUserDetails.fromLdap(
                loginId, "IT센터", "임직원",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        return new UsernamePasswordAuthenticationToken(ldapUser, null, ldapUser.getAuthorities());
    }

    @Test
    void JIT_최초_로그인_성공_시_Employee_저장_후_인증된다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01")).willReturn(Optional.empty());
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("emp01"));
        given(employeeRepository.save(any(Employee.class))).willAnswer(invocation -> invocation.getArgument(0));

        Authentication result = routingAuthenticationProvider.authenticate(request);

        assertThat(result).isNotNull();
        CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
        assertThat(principal.getUsername()).isEqualTo("emp01");
        assertThat(principal.getUserType()).isEqualTo(CustomUserDetails.USER_TYPE_EMPLOYEE);
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void JIT_race_재조회가_Employee면_LDAP_재인증_없이_복구된다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01"))
                .willReturn(Optional.empty()) // 최초 부재 확인
                .willReturn(Optional.of(existingEmployee("emp01"))); // race 후 재조회
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("emp01"));
        given(employeeRepository.save(any(Employee.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint violation"));

        Authentication result = routingAuthenticationProvider.authenticate(request);

        assertThat(result).isNotNull();
        CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
        assertThat(principal.getUsername()).isEqualTo("emp01");
        assertThat(principal.getUserType()).isEqualTo(CustomUserDetails.USER_TYPE_EMPLOYEE);
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_EMPLOYEE");
        // 복구 경로에서 LDAP 인증이 재수행되면 안 된다 — 정확히 1회만 호출.
        verify(ldapProvider, times(1)).authenticate(any(Authentication.class));
    }

    @Test
    void JIT_race_재조회_부재면_예외가_전파된다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01"))
                .willReturn(Optional.empty())
                .willReturn(Optional.empty()); // 재조회도 부재 — loginId race가 아닌 제약 위반(deptName unique 등)
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("emp01"));
        DataIntegrityViolationException violation = new DataIntegrityViolationException("constraint violation");
        given(employeeRepository.save(any(Employee.class))).willThrow(violation);

        assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request))
                .isSameAs(violation);
    }

    @Test
    void JIT_race_재조회가_Employee가_아니면_예외가_전파된다() {
        Authentication request = loginRequest("user01");
        Applicant applicant = new Applicant("race-ci", HashUtil.sha256("race-ci"));
        applicant.setLoginId("user01");
        given(userRepository.findUserByLoginId("user01"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(applicant)); // 동일 loginId 지원자 가입이 선점한 경우
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("user01"));
        DataIntegrityViolationException violation = new DataIntegrityViolationException("unique constraint violation");
        given(employeeRepository.save(any(Employee.class))).willThrow(violation);

        assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request))
                .isSameAs(violation);
    }

    private Employee existingEmployee(String loginId) {
        Employee employee = new Employee();
        employee.setLoginId(loginId);
        employee.setDeptName("IT센터");
        employee.setName("임직원");
        return employee;
    }
}
