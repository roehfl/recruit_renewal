package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retention API 권한 매트릭스 검증(Phase 09c, ADR-0007) — springSecurity filter chain 으로
 * SecurityConfig narrow matcher(method 분기 포함)를 실제 검증한다.
 * write = ROLE_PRIVACY_ADMIN 전용, GET·dry-run = RECRUIT·PRIVACY.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminRetentionControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private Authentication adminAuthentication(String role) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "admin01", "인사팀", "관리자",
                List.of(new SimpleGrantedAuthority(role))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private static final String GLOBAL_POLICY_JSON = """
            {
              "retentionPeriodDays": 30,
              "baselineType": "HIRING_ENDED_AT",
              "enabled": true
            }
            """;

    @Test
    void 정책_조회는_RECRUIT_ADMIN도_가능() throws Exception {
        mockMvc.perform(get("/api/admin/retention/policies")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 정책_생성은_RECRUIT_ADMIN이면_403() throws Exception {
        mockMvc.perform(post("/api/admin/retention/policies")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GLOBAL_POLICY_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void 정책_생성은_PRIVACY_ADMIN이면_200() throws Exception {
        mockMvc.perform(post("/api/admin/retention/policies")
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GLOBAL_POLICY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionPeriodDays").value(30))
                .andExpect(jsonPath("$.data.jobPostingId").isEmpty());
    }

    @Test
    void hold_설정은_PRIVACY_ADMIN_전용() throws Exception {
        Long applicationId = createDraftApplication("retention-hold-api");
        String body = """
                { "applicationId": %d, "reason": "법적 분쟁 보존" }
                """.formatted(applicationId);

        mockMvc.perform(post("/api/admin/retention/holds")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/retention/holds")
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void hold_조회는_RECRUIT_ADMIN이면_403_reason_원문_차단() throws Exception {
        // hold reason 은 자유 텍스트(민감 가능) — 조회도 PRIVACY_ADMIN 전용(9c 리뷰 Medium 1).
        mockMvc.perform(get("/api/admin/retention/holds")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/retention/holds")
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void execute는_PRIVACY_ADMIN_전용이고_confirm_없으면_400() throws Exception {
        mockMvc.perform(post("/api/admin/retention/purge-batches/execute")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "confirm": true, "sourceDryRunBatchId": 1 }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/retention/purge-batches/execute")
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "confirm": false, "sourceDryRunBatchId": 1 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void batch_목록_size_상한_초과는_400() throws Exception {
        mockMvc.perform(get("/api/admin/retention/purge-batches")
                        .param("size", "101")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void hold_해제는_RECRUIT_ADMIN이면_403() throws Exception {
        mockMvc.perform(delete("/api/admin/retention/holds/{id}", 1L)
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anchor_확정은_PRIVACY_ADMIN_전용이고_확정값이_저장된다() throws Exception {
        Long jobPostingId = createPosting();
        String body = """
                { "hiringEndedAt": "%s" }
                """.formatted(LocalDateTime.now().minusDays(10).withNano(0));

        mockMvc.perform(post("/api/admin/retention/job-postings/{id}/anchor", jobPostingId)
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/retention/job-postings/{id}/anchor", jobPostingId)
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.hiringEndedAt").exists());
    }

    @Test
    void dry_run은_RECRUIT_ADMIN도_실행_가능() throws Exception {
        mockMvc.perform(post("/api/admin/retention/purge-batches/dry-run")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch.mode").value("DRY_RUN"))
                .andExpect(jsonPath("$.data.batch.status").value("COMPLETED"));
    }

    @Test
    void batch_목록_조회는_RECRUIT_ADMIN도_가능하고_페이지네이션된다() throws Exception {
        mockMvc.perform(get("/api/admin/retention/purge-batches")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void 미인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/retention/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 일반_임직원은_조회도_403() throws Exception {
        mockMvc.perform(get("/api/admin/retention/policies")
                        .with(authentication(adminAuthentication("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 정책_validation_위반은_400() throws Exception {
        mockMvc.perform(post("/api/admin/retention/policies")
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "retentionPeriodDays": 0, "baselineType": "HIRING_ENDED_AT", "enabled": true }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "retention api recruitment",
                "<p>content</p>",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private Long createDraftApplication(String loginId) {
        Long jobPostingId = createPosting();
        jobPostingService.publish(jobPostingId);
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);

        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long jobPositionId = jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
        return jobApplicationService.create(applicant.getId(), new ApplicationCreateRequest(jobPostingId, jobPositionId));
    }
}
