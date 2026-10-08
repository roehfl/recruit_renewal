package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.User;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.service.AuthAttemptLimiter;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class RoutingAuthenticationProvider implements AuthenticationProvider {

    private final LdapAuthenticationProvider ldapProvider;
    private final DaoAuthenticationProvider daoProvider;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthAttemptLimiter attemptLimiter;
    private final LoginSecondFactorVerifier secondFactorVerifier;

    public RoutingAuthenticationProvider(LdapAuthenticationProvider ldapProvider, DaoAuthenticationProvider daoProvider, UserRepository userRepository, EmployeeRepository employeeRepository, AuthAttemptLimiter attemptLimiter, LoginSecondFactorVerifier secondFactorVerifier) {
        this.ldapProvider = ldapProvider;
        this.daoProvider = daoProvider;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.attemptLimiter = attemptLimiter;
        this.secondFactorVerifier = secondFactorVerifier;
    }

    /**
     * 아이디별 실패 한도에 닿았으면 LDAP·DB 인증을 시도하지 않고 429를 낸다. 사내 AD 계정 대입과
     * AD 잠금 유발을 여기서 먼저 막는다. LDAP 장애({@link InternalAuthenticationServiceException})는 실패로 세지 않는다.
     */
    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String loginId = authentication.getName();
        attemptLimiter.checkLogin(loginId);
        try {
            Authentication result = route(authentication, loginId);
            attemptLimiter.resetLogin(loginId);
            return result;
        } catch (InternalAuthenticationServiceException e) {
            throw e;
        } catch (AuthenticationException e) {
            attemptLimiter.recordLoginFailure(loginId);
            throw e;
        }
    }

    private Authentication route(Authentication authentication, String loginId) {
        Optional<User> userOptional = userRepository.findUserByLoginId(loginId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user instanceof Applicant applicant) {
                Authentication result = daoProvider.authenticate(authentication);
                // 비밀번호가 맞은 뒤에만 2차 인증을 본다. 임직원(LDAP) 경로에는 적용하지 않는다.
                secondFactorVerifier.verify(authentication.getDetails(), applicant);
                return result;
            }

            if (user instanceof Employee) {
                return processLdap(authentication, user);
            }
        }

        return processLdapAndJit(authentication, loginId);

    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private Authentication processLdap(Authentication authentication, User user) {
        Authentication ldapAuth = ldapProvider.authenticate(authentication);

        CustomUserDetails ldapUser = (CustomUserDetails) Objects.requireNonNull(ldapAuth).getPrincipal();
        return buildEmployeeAuthentication(user, Objects.requireNonNull(ldapUser));
    }

    private Authentication processLdapAndJit(Authentication authentication, String loginId) {
        Authentication ldapAuth = ldapProvider.authenticate(authentication);

        CustomUserDetails ldapUser = (CustomUserDetails) Objects.requireNonNull(ldapAuth).getPrincipal();

        Employee employee = new Employee();
        employee.setLoginId(loginId);
        employee.setDeptName(Objects.requireNonNull(ldapUser).getDeptName());
        employee.setName(ldapUser.getName());

        User savedUser;
        try {
            savedUser = employeeRepository.save(employee);
        } catch (DataIntegrityViolationException e) {
            // 동시 JIT loginId race: 다른 요청이 먼저 같은 Employee를 생성한 경우 재조회로 복구한다.
            // LDAP 인증은 이미 성공한 상태이므로 재인증 없이 기존 ldapUser로 토큰만 만든다.
            // loginId race가 아닌 제약 위반은 복구하지 않고 전파한다.
            User existingUser = userRepository.findUserByLoginId(loginId)
                    .filter(Employee.class::isInstance)
                    .orElseThrow(() -> e);
            return buildEmployeeAuthentication(existingUser, ldapUser);
        }

        return buildEmployeeAuthentication(savedUser, ldapUser);
    }

    private Authentication buildEmployeeAuthentication(User user, CustomUserDetails ldapUser) {
        // 표시용 부서명은 LDAP의 최신값을 쓴다. fromUser는 부서명을 비워 둔다.
        CustomUserDetails finalUser = CustomUserDetails.fromLdap(
                user.getLoginId(), ldapUser.getDeptName(), user.getName(), ldapUser.getAuthorities());
        return new UsernamePasswordAuthenticationToken(finalUser, null, finalUser.getAuthorities());
    }
}
