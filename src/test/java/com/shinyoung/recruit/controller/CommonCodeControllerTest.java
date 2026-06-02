package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.CommonCode;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class CommonCodeControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void public_read_returns_active_codes_sorted_excluding_inactive() throws Exception {
        String group = "WORK_LOCATION_" + uuid();
        commonCodeRepository.saveAll(List.of(
                CommonCode.create(group, "SEOUL", "Seoul", 2, true, null),
                CommonCode.create(group, "BUSAN", "Busan", 1, true, null),
                CommonCode.create(group, "OLD", "Retired", 3, false, null)));

        mockMvc.perform(get("/api/codes").param("groupCode", group)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("BUSAN")) // sortOrder 1 먼저
                .andExpect(jsonPath("$.data[1].code").value("SEOUL"))
                .andExpect(jsonPath("$.data[*].code")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("OLD"))));
    }

    @Test
    void admin_create_persists_and_rejects_duplicate() throws Exception {
        String group = "EMP_TYPE_" + uuid();
        String body = """
                {"groupCode":"%s","code":"FULL_TIME","displayName":"정규직","sortOrder":1,"active":true}
                """.formatted(group);

        mockMvc.perform(jsonAdmin(post("/api/admin/codes"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupCode").value(group))
                .andExpect(jsonPath("$.data.code").value("FULL_TIME"))
                .andExpect(jsonPath("$.data.displayName").value("정규직"))
                .andExpect(jsonPath("$.data.active").value(true));

        // (groupCode, code) 중복 → 400
        mockMvc.perform(jsonAdmin(post("/api/admin/codes"), body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_create_rejects_blank_code() throws Exception {
        String body = """
                {"groupCode":"GROUP","code":"   ","displayName":"name"}
                """;
        mockMvc.perform(jsonAdmin(post("/api/admin/codes"), body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_update_changes_display_and_soft_delete_hides_from_public() throws Exception {
        String group = "REGION_" + uuid();
        CommonCode code = commonCodeRepository.save(CommonCode.create(group, "GANGNAM", "Gangnam", 1, true, null));

        String body = """
                {"displayName":"강남구","sortOrder":5,"active":false,"description":"비활성화"}
                """;
        mockMvc.perform(jsonAdmin(put("/api/admin/codes/{id}", code.getId()), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("강남구"))
                .andExpect(jsonPath("$.data.sortOrder").value(5))
                .andExpect(jsonPath("$.data.active").value(false));

        // public read 에서는 비활성이라 제외
        mockMvc.perform(get("/api/codes").param("groupCode", group).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // admin read 에서는 비활성 포함
        mockMvc.perform(get("/api/admin/codes").param("groupCode", group)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].active").value(false));
    }

    @Test
    void admin_update_unknown_returns_not_found() throws Exception {
        mockMvc.perform(jsonAdmin(put("/api/admin/codes/{id}", 999999L), "{\"displayName\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_endpoints_block_applicant_and_anonymous() throws Exception {
        Applicant applicant = saveApplicant();
        String body = "{\"groupCode\":\"G\",\"code\":\"C\",\"displayName\":\"D\"}";

        mockMvc.perform(post("/api/admin/codes")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8).content(body)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/codes")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8).content(body)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder jsonAdmin(MockHttpServletRequestBuilder builder, String body) {
        return builder
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(body)
                .with(authentication(adminAuthentication()));
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Applicant saveApplicant() {
        String ci = "ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setUserName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "code-admin-" + UUID.randomUUID(),
                "Recruit",
                "Code Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
