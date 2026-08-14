package com.shinyoung.recruit.config;

import com.shinyoung.recruit.security.auth.CustomAccessDeniedHandler;
import com.shinyoung.recruit.security.auth.CustomAuthenticationEntryPoint;
import com.shinyoung.recruit.security.auth.RoleNames;
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
                "http://localhost:5173",
                "https://rec.shinyoung.com"
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
                // 메뉴 관리 write — MenuController 의 base path 가 /menu 라 실제 경로가 /api/menu/admin/menu 이고,
                // 아래 broad /api/admin/** 매처에 걸리지 않는다. 명시하지 않으면 anyRequest().permitAll() 로 흘러
                // 비인증 사용자가 메뉴를 생성/수정할 수 있다.
                .requestMatchers(HttpMethod.POST, "/api/menu/admin/menu", "/api/menu/admin/menu/*").hasAnyAuthority(RoleNames.ADMIN, RoleNames.RECRUIT_ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/job-postings/{jobPostingId}/application").hasAuthority(RoleNames.APPLICANT)
                .requestMatchers(HttpMethod.GET, "/api/job-postings/**").permitAll()
                // client event 수집(Phase 09f) — 로그인 전/세션 만료 오류도 수집하므로 permitAll(설계 7장).
                // anyRequest().permitAll()이 있어도 의도를 명시적으로 고정한다.
                .requestMatchers(HttpMethod.POST, "/api/client-events").permitAll()
                // 감사 read API(Phase 09b, ADR-0007). narrow matcher 를 broad /api/admin/** 보다 먼저 — 순서가 보안 요구사항.
                // ip/ua 원문 vs 마스킹은 GET 통과 후 컨트롤러에서 권한별 projection 으로 추가 게이팅한다.
                .requestMatchers(HttpMethod.GET, "/api/admin/audit/**").hasAnyAuthority(RoleNames.RECRUIT_ADMIN, RoleNames.PRIVACY_ADMIN)
                // Retention(Phase 09c, ADR-0007) — write 는 ROLE_PRIVACY_ADMIN 전용, GET/dry-run 은 RECRUIT 포함.
                // method 까지 분기(설계 리뷰 #5/#7). 전부 broad /api/admin/** 보다 먼저.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/execute").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/reconcile").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/dry-run").hasAnyAuthority(RoleNames.RECRUIT_ADMIN, RoleNames.PRIVACY_ADMIN)
                // 정책 create/update/delete 전부 POST(전 엔드포인트 GET/POST 정책) — /policies/** 한 줄로 커버.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/policies/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                // hold set/release 전부 POST(release 는 /holds/{id}/release) — /holds/** 한 줄로 커버.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/holds/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                // hold reason 은 자유 텍스트(민감 가능) — 조회도 PRIVACY_ADMIN 전용(9c 리뷰 Medium 1, 아래 GET 보다 먼저).
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/holds/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/job-postings/*/anchor").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/**").hasAnyAuthority(RoleNames.RECRUIT_ADMIN, RoleNames.PRIVACY_ADMIN)
                // cleanup은 삭제(write) — retention 관례에 따라 PRIVACY_ADMIN 전용(설계 9장).
                .requestMatchers(HttpMethod.POST, "/api/admin/client-events/cleanup").hasAuthority(RoleNames.PRIVACY_ADMIN)
                // client event 조회(Phase 09f-3) — broad /api/admin/** 보다 먼저(순서가 보안 요구사항).
                // 민감 필드 원문 vs 마스킹은 컨트롤러에서 권한별 projection으로 추가 게이팅한다.
                .requestMatchers(HttpMethod.GET, "/api/admin/client-events/**").hasAnyAuthority(RoleNames.RECRUIT_ADMIN, RoleNames.PRIVACY_ADMIN)
                .requestMatchers("/api/admin/**").hasAnyAuthority(RoleNames.ADMIN, RoleNames.RECRUIT_ADMIN)
                .requestMatchers("/api/applicant/**").hasAuthority(RoleNames.APPLICANT)
                .requestMatchers("/api/interviewer/**").hasAnyAuthority(RoleNames.EMPLOYEE, RoleNames.ADMIN, RoleNames.RECRUIT_ADMIN, RoleNames.INTERVIEWER)
                .requestMatchers("/api/applications/**").hasAuthority(RoleNames.APPLICANT)
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
