package com.shinyoung.recruit.config;

import com.shinyoung.recruit.domain.repository.DeptRoleMappingRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.domain.repository.UserRoleMappingRepository;
import com.shinyoung.recruit.security.auth.CustomLdapUserDetailsMapper;
import com.shinyoung.recruit.security.auth.CustomUserDetailsService;
import com.shinyoung.recruit.security.auth.RoutingAuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

@Slf4j
@Configuration
public class AuthenticationConfig {

    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public AuthenticationConfig(CustomUserDetailsService userDetailsService, UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    /*
     * 접속정보와 검색 base/filter는 하드코딩하지 않고 LdapProperties로 주입받는다(CLAUDE.md 3장).
     * 값 주입 방법은 docs/codex/01-project-context.md의 환경변수 표를 따른다.
     */
    @Bean
    public LdapContextSource contextSource(LdapProperties ldapProperties) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapProperties.getUrl());
        contextSource.setBase(ldapProperties.getBase());
        contextSource.setUserDn(ldapProperties.getManagerDn());
        contextSource.setPassword(ldapProperties.getManagerPassword());
        return contextSource;
    }

    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider(
            BaseLdapPathContextSource contextSource,
            CustomLdapUserDetailsMapper customLdapUserDetailsMapper,
            LdapProperties ldapProperties
    ) {
        /*
         * 로컬 개발에서는 LDAP을 쓰지 않을 수 있어 기동은 막지 않는다.
         * 다만 미설정 상태로 조용히 인증만 실패하면 원인을 찾기 어려우므로 경고를 남긴다.
         * 비밀값은 출력하지 않는다.
         */
        if (!ldapProperties.isConfigured()) {
            log.warn("LDAP 설정이 비어 있어 임직원(LDAP) 인증은 동작하지 않는다. "
                    + "LDAP_URL, LDAP_MANAGER_DN, LDAP_MANAGER_PASSWORD, LDAP_USER_SEARCH_BASE 를 설정해야 한다.");
        }

        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(new FilterBasedLdapUserSearch(
                ldapProperties.getUserSearchBase(),
                ldapProperties.getUserSearchFilter(),
                contextSource
        ));

        /*
         * 부서 그룹의 cn(예: "내부채널_부서_6315")을 가공 없이 받기 위해 접두어·대문자 변환을 끈다.
         * 실제 권한(ROLE_*)은 이 결과가 아니라 CustomLdapUserDetailsMapper가 dept_role_mapping을 보고 부여한다.
         */
        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
                new DefaultLdapAuthoritiesPopulator(contextSource, ldapProperties.getGroupSearchBase());
        authoritiesPopulator.setRolePrefix("");
        authoritiesPopulator.setConvertToUpperCase(false);

        LdapAuthenticationProvider provider = new LdapAuthenticationProvider(authenticator, authoritiesPopulator);
        provider.setUserDetailsContextMapper(customLdapUserDetailsMapper);
        return provider;
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider((UserDetailsService) userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public RoutingAuthenticationProvider routingAuthenticationProvider(LdapAuthenticationProvider ldapAuthenticationProvider, DaoAuthenticationProvider daoAuthenticationProvider, EmployeeRepository employeeRepository) {
        return new RoutingAuthenticationProvider(ldapAuthenticationProvider, daoAuthenticationProvider, userRepository, employeeRepository);
    }

    @Bean
    public AuthenticationManager authenticationManager(RoutingAuthenticationProvider routingAuthenticationProvider) {
        return new ProviderManager(routingAuthenticationProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CustomLdapUserDetailsMapper customLdapUserDetailsMapper(
            DeptRoleMappingRepository deptRoleMappingRepository,
            UserRoleMappingRepository userRoleMappingRepository
    ) {
        return new CustomLdapUserDetailsMapper(deptRoleMappingRepository, userRoleMappingRepository);
    }
}
