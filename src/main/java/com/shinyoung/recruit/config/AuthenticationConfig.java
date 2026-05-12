package com.shinyoung.recruit.config;

import com.shinyoung.recruit.domain.repository.DeptRoleMappingRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.security.auth.CustomLdapUserDetailsMapper;
import com.shinyoung.recruit.security.auth.CustomUserDetailsService;
import com.shinyoung.recruit.security.auth.RoutingAuthenticationProvider;
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

@Configuration
public class AuthenticationConfig {

    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public AuthenticationConfig(CustomUserDetailsService userDetailsService, UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Bean
    public LdapContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://");
        contextSource.setBase("");
        contextSource.setUserDn("");
        contextSource.setPassword("");
        return contextSource;
    }

    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider(BaseLdapPathContextSource contextSource, CustomLdapUserDetailsMapper customLdapUserDetailsMapper) {
        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(new FilterBasedLdapUserSearch("ou=임직원,ou=Shinyoungin", "(sAMAccountName={0})", contextSource));

        DefaultLdapAuthoritiesPopulator authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource, "ou=부서,ou=Shinyoungin");
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
    public CustomLdapUserDetailsMapper customLdapUserDetailsMapper(DeptRoleMappingRepository deptRoleMappingRepository) {
        return new CustomLdapUserDetailsMapper(deptRoleMappingRepository);
    }
}
