package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 감사 read API(Phase 09b) — 권한별 projection(마스킹/원문) 분리 + read 가드(page size/range/default range) +
 * SecurityConfig narrow matcher 검증(springSecurity filter chain 적용).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminAuditControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private ActivityLog seedLog(LocalDateTime occurredAt) {
        return activityLogRepository.save(ActivityLog.builder()
                .occurredAt(occurredAt)
                .actorType(ActorType.EMPLOYEE)
                .actorId("admin01")
                .actorRoleSnapshot("ROLE_RECRUIT_ADMIN")
                .actionType(AuditActionType.EXPORT_APPLICATIONS)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.EXPORT_DATASET)
                .targetId("APPLICATIONS")
                .jobPostingId(7L)
                .ipAddress("10.0.0.1")
                .userAgent("test-agent")
                .metadataJson("{\"datasetType\":\"APPLICATIONS\",\"rowCount\":3}")
                .build());
    }

    private Authentication adminAuthentication(String... roles) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "admin01", "인사팀", "관리자",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    void RECRUIT_ADMIN은_ip_ua가_마스킹된다() throws Exception {
        seedLog(LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/admin/audit/activities")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].actorId").value("admin01"))
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("***"))
                .andExpect(jsonPath("$.data.content[0].userAgent").value("***"))
                .andExpect(jsonPath("$.data.content[0].metadataJson").exists());
    }

    @Test
    void PRIVACY_ADMIN은_ip_ua_원문을_본다() throws Exception {
        seedLog(LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/admin/audit/activities")
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("10.0.0.1"))
                .andExpect(jsonPath("$.data.content[0].userAgent").value("test-agent"));
    }

    @Test
    void 범위_미지정이면_최근_30일만_조회된다() throws Exception {
        seedLog(LocalDateTime.now().minusDays(1));
        seedLog(LocalDateTime.now().minusDays(40)); // default range(30일) 밖

        mockMvc.perform(get("/api/admin/audit/activities")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 명시_범위로_과거_로그도_조회된다() throws Exception {
        seedLog(LocalDateTime.now().minusDays(40));

        mockMvc.perform(get("/api/admin/audit/activities")
                        .param("from", LocalDateTime.now().minusDays(60).toString())
                        .param("to", LocalDateTime.now().toString())
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void page_size_상한_초과는_400() throws Exception {
        mockMvc.perform(get("/api/admin/audit/activities")
                        .param("size", "101")
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 범위_상한_초과는_400() throws Exception {
        mockMvc.perform(get("/api/admin/audit/activities")
                        .param("from", LocalDateTime.now().minusDays(91).toString())
                        .param("to", LocalDateTime.now().toString())
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 단건_조회도_권한별_마스킹이_적용된다() throws Exception {
        ActivityLog saved = seedLog(LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/admin/audit/activities/{id}", saved.getId())
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ipAddress").value("***"));

        mockMvc.perform(get("/api/admin/audit/activities/{id}", saved.getId())
                        .with(authentication(adminAuthentication("ROLE_PRIVACY_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ipAddress").value("10.0.0.1"));
    }

    @Test
    void 단건_부재면_404() throws Exception {
        mockMvc.perform(get("/api/admin/audit/activities/{id}", 999999L)
                        .with(authentication(adminAuthentication("ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 미인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/audit/activities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 권한_없는_임직원이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/audit/activities")
                        .with(authentication(adminAuthentication("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
}
