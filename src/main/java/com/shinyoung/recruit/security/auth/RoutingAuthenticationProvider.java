package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.User;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationProvider;
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

    public RoutingAuthenticationProvider(LdapAuthenticationProvider ldapProvider, DaoAuthenticationProvider daoProvider, UserRepository userRepository, EmployeeRepository employeeRepository) {
        this.ldapProvider = ldapProvider;
        this.daoProvider = daoProvider;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String loginId = authentication.getName();
        Optional<User> userOptional = userRepository.findUserByLoginId(loginId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if(user instanceof Applicant) {
                return daoProvider.authenticate(authentication);
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
            // loginId race가 아닌 제약 위반(deptName unique 등)은 복구하지 않고 전파한다.
            User existingUser = userRepository.findUserByLoginId(loginId)
                    .filter(Employee.class::isInstance)
                    .orElseThrow(() -> e);
            return buildEmployeeAuthentication(existingUser, ldapUser);
        }

        return buildEmployeeAuthentication(savedUser, ldapUser);
    }

    private Authentication buildEmployeeAuthentication(User user, CustomUserDetails ldapUser) {
        CustomUserDetails finalUser = CustomUserDetails.fromUser(user, ldapUser.getAuthorities());
        return new UsernamePasswordAuthenticationToken(finalUser, null, finalUser.getAuthorities());
    }
}
