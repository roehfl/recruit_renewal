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
import java.util.Set;

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
                "https://rec.shinyoung.com",
                "https://shinrecruitdev.shinyoung.com"
        ));
        corsConfiguration.setAllowedMethods(Arrays.asList("POST", "GET"));
        corsConfiguration.setAllowedHeaders(List.of(
                "Content-Type", "X-Requested-With", "X-XSRF-TOKEN"
        ));
        // FE가 실패 업무 API 응답에서 X-Request-Id를 읽어 relatedCorrelationId로 보낸다(Phase 09f, 설계 7장).
        // Content-Disposition: 첨부/엑셀 다운로드에서 FE가 원본 파일명을 읽으려면 노출이 필요하다.
        corsConfiguration.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", corsConfiguration);
        // 설정이 null 이면 CorsFilter 는 CORS 처리를 건너뛴다(CORS 응답 헤더도 붙이지 않는다).
        return request -> CORS_EXEMPT_PATHS.contains(
                request.getRequestURI().substring(request.getContextPath().length()))
                ? null
                : source.getCorsConfiguration(request);
    }

    /**
     * CORS 처리에서 빼는 경로. NICE 팝업이 보내는 cross-site 폼 POST 라 브라우저가
     * Origin(https://nice.checkplus.co.kr, Referrer-Policy 에 따라 "null")을 붙인다. CorsFilter 는
     * 폼 이동과 스크립트 요청을 구분하지 않고 허용 목록 밖 Origin 을 403 으로 거부하므로, 여기서
     * 빼지 않으면 실제 NICE 인증이 전부 막힌다.
     *
     * <p>허용 목록에 NICE Origin 을 넣지 않는다. allowCredentials=true 라 그러면 NICE 쪽 스크립트가
     * 모든 API 를 자격 증명과 함께 호출하고 응답을 읽을 수 있게 된다. "null" 은 샌드박스 iframe·file://
     * 도 쓰는 값이라 허용하면 안 된다. 콜백의 안전성은 Origin 이 아니라 REQ_SEQ 대조와 암호문에 있다.
     */
    private static final Set<String> CORS_EXEMPT_PATHS = Set.of(
            "/api/auth/nice/callback",
            "/api/auth/nice/callback/error"
    );


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
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/applicants/sign-up", "/api/auth/applicants/check-email", "/api/auth/applicants/find-email").permitAll()
                // 본인확인은 가입 전(미인증) 흐름이다. callback 2종은 NICE 팝업이 부르는
                // cross-site POST 라 세션·인증이 없다.
                .requestMatchers("/api/auth/nice/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**", "/h2-console/**", "/api/menu/tree").permitAll()
                // 메뉴 관리 write — MenuController 의 base path 가 /menu 라 실제 경로가 /api/menu/admin/menu 이고,
                // 아래 broad /api/admin/** 매처에 걸리지 않는다. 명시하지 않으면 anyRequest().permitAll() 로 흘러
                // 비인증 사용자가 메뉴를 생성/수정할 수 있다.
                .requestMatchers(HttpMethod.POST, "/api/menu/admin/menu", "/api/menu/admin/menu/*").hasAnyAuthority(RoleNames.ADMIN, RoleNames.RECRUIT_ADMIN)
                // 공지 등록 — BoardController 의 base path 가 /board 라 broad /api/admin/** 에 걸리지 않는다.
                // 명시하지 않으면 비인증 사용자가 공지(HTML)를 등록할 수 있다. 조회(GET)는 공개 유지.
                .requestMatchers(HttpMethod.POST, "/api/board/**").hasAnyAuthority(RoleNames.ADMIN, RoleNames.RECRUIT_ADMIN)
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
                // 강제 파기(Phase 10) — 비가역 + 보존기간 무시라 PRIVACY 전용.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/force").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/execute").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/reconcile").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/dry-run").hasAnyAuthority(RoleNames.RECRUIT_ADMIN, RoleNames.PRIVACY_ADMIN)
                // 정책 create/update/delete 전부 POST(전 엔드포인트 GET/POST 정책) — /policies/** 한 줄로 커버.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/policies/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                // hold set/release 전부 POST(release 는 /holds/{id}/release) — /holds/** 한 줄로 커버.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/holds/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                // hold reason 은 자유 텍스트(민감 가능) — 조회도 PRIVACY_ADMIN 전용(9c 리뷰 Medium 1, 아래 GET 보다 먼저).
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/holds/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                // 파기 대상자 조회는 지원자 원문 PII 를 반환한다 — broad GET retention/** 보다 먼저 PRIVACY 로 좁힌다.
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/data-subjects/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/data-subjects").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/job-postings/*/anchor").hasAuthority(RoleNames.PRIVACY_ADMIN)
                // 자동 파기 on/off — 쓰기는 PRIVACY. GET 은 아래 broad GET retention/** (RECRUIT·PRIVACY)로 충분하다.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/schedule").hasAuthority(RoleNames.PRIVACY_ADMIN)
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
