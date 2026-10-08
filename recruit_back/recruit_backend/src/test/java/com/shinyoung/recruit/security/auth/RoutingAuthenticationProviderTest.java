package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.AuthAttemptLimitProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.exception.AuthAttemptLimitExceededException;
import com.shinyoung.recruit.exception.LoginSecondFactorException;
import com.shinyoung.recruit.service.AuthAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import java.time.Clock;
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

    @Mock
    private LoginSecondFactorVerifier secondFactorVerifier;

    private RoutingAuthenticationProvider routingAuthenticationProvider;

    @BeforeEach
    void setUp() {
        routingAuthenticationProvider = new RoutingAuthenticationProvider(
                ldapProvider, daoProvider, userRepository, employeeRepository,
                new AuthAttemptLimiter(Clock.systemUTC(), new AuthAttemptLimitProperties()),
                secondFactorVerifier);
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
    void JIT_최초_로그인_시_principal_부서명은_LDAP_부서명이다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01")).willReturn(Optional.empty());
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("emp01"));
        given(employeeRepository.save(any(Employee.class))).willAnswer(invocation -> invocation.getArgument(0));

        Authentication result = routingAuthenticationProvider.authenticate(request);

        CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
        assertThat(principal.getDeptName()).isEqualTo("IT센터");
    }

    @Test
    void 기존_임직원_로그인_시_principal_부서명은_LDAP의_최신_부서명이다() {
        Authentication request = loginRequest("emp01");
        Employee employee = existingEmployee("emp01");
        employee.setDeptName("이전부서");
        given(userRepository.findUserByLoginId("emp01")).willReturn(Optional.of(employee));
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("emp01"));

        Authentication result = routingAuthenticationProvider.authenticate(request);

        CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
        assertThat(principal.getDeptName()).isEqualTo("IT센터");
        assertThat(principal.getName()).isEqualTo("임직원");
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_EMPLOYEE");
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
                .willReturn(Optional.empty()); // 재조회도 부재 — loginId race가 아닌 제약 위반
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("emp01"));
        DataIntegrityViolationException violation = new DataIntegrityViolationException("constraint violation");
        given(employeeRepository.save(any(Employee.class))).willThrow(violation);

        assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request))
                .isSameAs(violation);
    }

    @Test
    void JIT_race_재조회가_Employee가_아니면_예외가_전파된다() {
        Authentication request = loginRequest("user01");
        Applicant applicant = new Applicant(HashUtil.sha256("race-ci"));
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

    /* 로그인 시도 제한 — 아이디별 실패 5회면 LDAP·DB 인증을 시도하지 않고 429(AD 계정 대입·잠금 유발 차단). */

    @Test
    void 실패가_5회_쌓이면_인증을_시도하지_않고_거부한다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01")).willReturn(Optional.empty());
        given(ldapProvider.authenticate(request)).willThrow(new BadCredentialsException("bad"));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(loginRequest(" EMP01 ")))
                .isInstanceOf(AuthAttemptLimitExceededException.class)
                .hasMessage("로그인 시도가 너무 많습니다. 15분 후 다시 시도해 주세요.");
        verify(ldapProvider, times(5)).authenticate(request);
    }

    @Test
    void 성공하면_실패_수를_지운다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01")).willReturn(Optional.empty());
        given(employeeRepository.save(any(Employee.class))).willAnswer(invocation -> invocation.getArgument(0));
        BadCredentialsException bad = new BadCredentialsException("bad");
        given(ldapProvider.authenticate(request))
                .willThrow(bad, bad, bad, bad)
                .willReturn(ldapSuccess("emp01"))
                .willThrow(bad);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request)).isSameAs(bad);
        }
        routingAuthenticationProvider.authenticate(request);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request)).isSameAs(bad);
        }
    }

    /* 지원자 로그인 2차 인증(NICE) — 비밀번호 검증 뒤에 LoginSecondFactorVerifier 를 부른다. 임직원 경로는 건드리지 않는다. */

    private Applicant applicantUser(String loginId) {
        Applicant applicant = new Applicant(HashUtil.sha256("login-2fa-ci"));
        applicant.setLoginId(loginId);
        return applicant;
    }

    private Authentication passwordSuccess(String loginId) {
        return new UsernamePasswordAuthenticationToken(loginId, null, List.of());
    }

    @Test
    void 지원자는_비밀번호가_맞으면_요청_details와_계정으로_2차_인증을_검사한다() {
        Applicant applicant = applicantUser("user01@example.test");
        UsernamePasswordAuthenticationToken request = new UsernamePasswordAuthenticationToken("user01@example.test", "pw");
        request.setDetails("login-nice-details");
        Authentication passwordOk = passwordSuccess("user01@example.test");
        given(userRepository.findUserByLoginId("user01@example.test")).willReturn(Optional.of(applicant));
        given(daoProvider.authenticate(request)).willReturn(passwordOk);

        Authentication result = routingAuthenticationProvider.authenticate(request);

        assertThat(result).isSameAs(passwordOk);
        verify(secondFactorVerifier).verify("login-nice-details", applicant);
    }

    @Test
    void 지원자_2차_인증이_실패하면_로그인이_거부되고_실패로_센다() {
        Applicant applicant = applicantUser("user01@example.test");
        Authentication request = new UsernamePasswordAuthenticationToken("user01@example.test", "pw");
        given(userRepository.findUserByLoginId("user01@example.test")).willReturn(Optional.of(applicant));
        given(daoProvider.authenticate(request)).willReturn(passwordSuccess("user01@example.test"));
        LoginSecondFactorException failure = new LoginSecondFactorException(LoginSecondFactorException.Reason.MISMATCH);
        org.mockito.Mockito.doThrow(failure).when(secondFactorVerifier).verify(any(), any(Applicant.class));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request)).isSameAs(failure);
        }

        assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request))
                .isInstanceOf(AuthAttemptLimitExceededException.class);
    }

    @Test
    void 지원자_비밀번호가_틀리면_2차_인증을_검사하지_않는다() {
        Applicant applicant = applicantUser("user01@example.test");
        Authentication request = new UsernamePasswordAuthenticationToken("user01@example.test", "wrong");
        given(userRepository.findUserByLoginId("user01@example.test")).willReturn(Optional.of(applicant));
        given(daoProvider.authenticate(request)).willThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(secondFactorVerifier, org.mockito.Mockito.never()).verify(any(), any());
    }

    @Test
    void 임직원은_2차_인증을_검사하지_않는다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01")).willReturn(Optional.of(existingEmployee("emp01")));
        given(ldapProvider.authenticate(request)).willReturn(ldapSuccess("emp01"));

        routingAuthenticationProvider.authenticate(request);

        verify(secondFactorVerifier, org.mockito.Mockito.never()).verify(any(), any());
    }

    @Test
    void LDAP_장애는_실패로_세지_않는다() {
        Authentication request = loginRequest("emp01");
        given(userRepository.findUserByLoginId("emp01")).willReturn(Optional.empty());
        given(ldapProvider.authenticate(request)).willThrow(new InternalAuthenticationServiceException("ldap down"));

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> routingAuthenticationProvider.authenticate(request))
                    .isInstanceOf(InternalAuthenticationServiceException.class);
        }
    }
}
