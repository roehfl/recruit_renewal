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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
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
        // FE가 실패 업무 API 응답에서 X-Request-Id를 읽어 relatedCorrelationId로 보낸다(Phase 09f, 설계 7장).
        corsConfiguration.setExposedHeaders(List.of("X-Request-Id"));
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
        http.securityContext(sc -> sc.securityContextRepository(securityContextRepository()));
        http.exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/applicants/sign-up", "/api/auth/applicants/check-email").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**", "/h2-console/**", "/api/menu/tree").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/job-postings/{jobPostingId}/application").hasAuthority("ROLE_APPLICANT")
                .requestMatchers(HttpMethod.GET, "/api/job-postings/**").permitAll()
                // client event 수집(Phase 09f) — 로그인 전/세션 만료 오류도 수집하므로 permitAll(설계 7장).
                // anyRequest().permitAll()이 있어도 의도를 명시적으로 고정한다.
                .requestMatchers(HttpMethod.POST, "/api/client-events").permitAll()
                // 감사 read API(Phase 09b, ADR-0007). narrow matcher 를 broad /api/admin/** 보다 먼저 — 순서가 보안 요구사항.
                // ip/ua 원문 vs 마스킹은 GET 통과 후 컨트롤러에서 권한별 projection 으로 추가 게이팅한다.
                .requestMatchers(HttpMethod.GET, "/api/admin/audit/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
                // Retention(Phase 09c, ADR-0007) — write 는 ROLE_PRIVACY_ADMIN 전용, GET/dry-run 은 RECRUIT 포함.
                // method 까지 분기(설계 리뷰 #5/#7). 전부 broad /api/admin/** 보다 먼저.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/execute").hasAuthority("ROLE_PRIVACY_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/reconcile").hasAuthority("ROLE_PRIVACY_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/dry-run").hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
                // 정책 create/update/delete 전부 POST(전 엔드포인트 GET/POST 정책) — /policies/** 한 줄로 커버.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
                // hold set/release 전부 POST(release 는 /holds/{id}/release) — /holds/** 한 줄로 커버.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")
                // hold reason 은 자유 텍스트(민감 가능) — 조회도 PRIVACY_ADMIN 전용(9c 리뷰 Medium 1, 아래 GET 보다 먼저).
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/job-postings/*/anchor").hasAuthority("ROLE_PRIVACY_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
                .requestMatchers("/api/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_RECRUIT_ADMIN")
                .requestMatchers("/api/applicant/**").hasAuthority("ROLE_APPLICANT")
                .requestMatchers("/api/interviewer/**").hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN", "ROLE_RECRUIT_ADMIN", "ROLE_INTERVIEWER")
                .requestMatchers("/api/applications/**").hasAuthority("ROLE_APPLICANT")
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
