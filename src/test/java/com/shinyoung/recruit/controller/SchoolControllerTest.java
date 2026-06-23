package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.School;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.SchoolRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class SchoolControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SchoolRepository schoolRepository;

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
    void public_search_returns_active_prefix_first_excluding_inactive() throws Exception {
        schoolRepository.saveAll(List.of(
                School.create("Seoul National University", "UNIVERSITY", null, null, "Seoul", null, "KR", true),
                School.create("Hankuk Seoul Campus", "UNIVERSITY", null, null, "Seoul", null, "KR", true),
                School.create("Busan University", "UNIVERSITY", null, null, "Busan", null, "KR", true),
                School.create("Seoul Closed School", "UNIVERSITY", null, null, "Seoul", null, "KR", false)));

        mockMvc.perform(get("/api/schools").param("q", "Seoul").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].schoolName").value("Seoul National University")) // prefix 우선
                .andExpect(jsonPath("$.data[*].schoolName")
                        .value(org.hamcrest.Matchers.hasItem("Hankuk Seoul Campus")))
                .andExpect(jsonPath("$.data[*].schoolName")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Busan University"))))
                .andExpect(jsonPath("$.data[*].schoolName")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Seoul Closed School"))));
    }

    @Test
    void public_search_filters_by_school_type() throws Exception {
        schoolRepository.saveAll(List.of(
                School.create("Seoul High School", "HIGH_SCHOOL", null, null, "Seoul", null, "KR", true),
                School.create("Seoul University", "UNIVERSITY", null, null, "Seoul", null, "KR", true)));

        mockMvc.perform(get("/api/schools").param("q", "Seoul").param("schoolType", "UNIVERSITY")
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].schoolName").value("Seoul University"));
    }

    @Test
    void public_search_escapes_like_wildcards() throws Exception {
        // q="%" 가 전체 매칭으로 새지 않고, 이름에 literal '%' 가 든 학교만 매칭되어야 한다(escape).
        schoolRepository.saveAll(List.of(
                School.create("Alpha University", "UNIVERSITY", null, null, "Seoul", null, "KR", true),
                School.create("A%B University", "UNIVERSITY", null, null, "Seoul", null, "KR", true)));

        mockMvc.perform(get("/api/schools").param("q", "%").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].schoolName").value("A%B University"));
    }

    @Test
    void public_search_blank_q_returns_empty() throws Exception {
        schoolRepository.save(School.create("Anything University", "UNIVERSITY", null, null, "Seoul", null, "KR", true));

        mockMvc.perform(get("/api/schools").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void admin_create_persists() throws Exception {
        String body = """
                {"schoolName":"테스트대학교","schoolType":"UNIVERSITY","schoolCategory":"GENERAL","region":"서울"}
                """;
        mockMvc.perform(jsonAdmin(post("/api/admin/schools"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schoolName").value("테스트대학교"))
                .andExpect(jsonPath("$.data.schoolCategory").value("GENERAL"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void admin_create_allows_multiple_schools() throws Exception {
        mockMvc.perform(jsonAdmin(post("/api/admin/schools"), "{\"schoolName\":\"A대학교\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(jsonAdmin(post("/api/admin/schools"), "{\"schoolName\":\"B대학교\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void admin_create_rejects_blank_school_name() throws Exception {
        mockMvc.perform(jsonAdmin(post("/api/admin/schools"), "{\"schoolName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_update_changes_fields_and_soft_delete_hides_from_search() throws Exception {
        School school = schoolRepository.save(
                School.create("Editme University", "UNIVERSITY", null, null, "Seoul", null, "KR", true));

        String body = """
                {"schoolName":"Editme University","schoolType":"UNIVERSITY","schoolCategory":"GENERAL","region":"Incheon","active":false}
                """;
        mockMvc.perform(jsonAdmin(post("/api/admin/schools/{id}", school.getId()), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region").value("Incheon"))
                .andExpect(jsonPath("$.data.schoolCategory").value("GENERAL"))
                .andExpect(jsonPath("$.data.active").value(false));

        // 비활성이라 public 검색에서 제외
        mockMvc.perform(get("/api/schools").param("q", "Editme").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void admin_update_unknown_returns_not_found() throws Exception {
        mockMvc.perform(jsonAdmin(post("/api/admin/schools/{id}", 999999L), "{\"schoolName\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_list_includes_inactive_paged() throws Exception {
        schoolRepository.saveAll(List.of(
                School.create("ListedActive University", "UNIVERSITY", null, null, "Seoul", null, "KR", true),
                School.create("ListedInactive University", "UNIVERSITY", null, null, "Seoul", null, "KR", false)));

        mockMvc.perform(get("/api/admin/schools").param("q", "Listed")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void admin_endpoints_block_applicant_and_anonymous() throws Exception {
        Applicant applicant = saveApplicant();
        String body = "{\"schoolName\":\"X대학교\"}";

        mockMvc.perform(post("/api/admin/schools")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8).content(body)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/schools")
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
                "school-admin-" + UUID.randomUUID(),
                "Recruit",
                "School Admin",
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
