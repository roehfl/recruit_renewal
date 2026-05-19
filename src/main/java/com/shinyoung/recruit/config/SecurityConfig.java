package com.shinyoung.recruit.config;

import com.shinyoung.recruit.security.auth.CustomAccessDeniedHandler;
import com.shinyoung.recruit.security.auth.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.ldap.LdapBindAuthenticationManagerFactory;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final AuthenticationManager authenticationManager;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            AuthenticationManager authenticationManager,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationManager = authenticationManager;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfiguration = new CorsConfiguration();
//        corsConfiguration.applyPermitDefaultValues();
        corsConfiguration.setAllowedOrigins(List.of(
                "http://localhost:5173"
        ));
        corsConfiguration.setAllowedMethods(Arrays.asList("POST", "GET"));
        corsConfiguration.setAllowedHeaders(List.of(
                "Content-Type", "X-Requested-With", "X-XSRF-TOKEN"
        ));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));
        http.cors(cors -> corsConfigurationSource());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        http.exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests
                .requestMatchers("/auth/login", "/auth/logout").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**", "/h2-console/**", "/menu/tree").permitAll()
                .requestMatchers(HttpMethod.GET, "/job-postings/{jobPostingId}/application").hasAuthority("ROLE_APPLICANT")
                .requestMatchers(HttpMethod.GET, "/job-postings/**").permitAll()
                .requestMatchers("/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_RECRUIT_ADMIN")
                .requestMatchers("/applications/**").hasAuthority("ROLE_APPLICANT")
                .anyRequest().permitAll());
        http.authenticationManager(authenticationManager);

//        http.formLogin(form -> form
//                .loginProcessingUrl("/auth/login")
//                .usernameParameter("loginId")
//                .passwordParameter("password")
//                .successHandler((req, res, auth) -> res.setStatus(200))
//                .failureHandler((req, res, ex) -> res.setStatus(401))
//        );

        return http.build();
    }
}
