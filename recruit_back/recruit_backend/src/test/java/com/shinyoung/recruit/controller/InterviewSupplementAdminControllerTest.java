package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.enumeration.StageType;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
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
class InterviewSupplementAdminControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 관리자는_켜고_질문을_추가하고_세트_상태를_받는다() throws Exception {
        Stage stage = saveStage(StageType.FIRST_INTERVIEW);
        String base = "/api/admin/stages/" + stage.getId() + "/interview-supplement";

        mockMvc.perform(get(base).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.questions").isEmpty());

        mockMvc.perform(post(base).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post(base + "/questions")
                        .with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"입사 후 목표를 적어 주세요.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stageId").value(stage.getId()))
                .andExpect(jsonPath("$.data.questions[0].content").value("입사 후 목표를 적어 주세요."))
                .andExpect(jsonPath("$.data.questions[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.answeredApplicantCount").value(0));

        mockMvc.perform(get(base + "/candidates").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 검증_오류는_400과_404로_답한다() throws Exception {
        Stage document = saveStage(StageType.DOCUMENT);
        Stage interview = saveStage(StageType.FIRST_INTERVIEW);

        mockMvc.perform(post("/api/admin/stages/{id}/interview-supplement", document.getId())
                        .with(authentication(admin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/admin/stages/{id}/interview-supplement/questions", interview.getId())
                        .with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"질문\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/stages/{id}/interview-supplement", interview.getId())
                        .with(authentication(admin())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/stages/{id}/interview-supplement/questions", interview.getId())
                        .with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/stages/{id}/interview-supplement/windows", interview.getId())
                        .with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobApplicationIds\":[1],\"startDateTime\":\"2026-10-06T10:00:00\","
                                + "\"endDateTime\":\"2026-10-06T09:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 지원자와_비로그인은_관리자_API를_쓸_수_없다() throws Exception {
        Stage stage = saveStage(StageType.FIRST_INTERVIEW);

        mockMvc.perform(get("/api/admin/stages/{id}/interview-supplement", stage.getId())
                        .with(authentication(applicant())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/stages/{id}/interview-supplement", stage.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private Stage saveStage(StageType type) {
        JobPosting posting = JobPosting.create("Posting", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 20, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        return stageRepository.saveAndFlush(Stage.create(posting, "단계", type, 1, null, false));
    }

    private Authentication admin() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "employee-" + UUID.randomUUID(),
                "HR",
                "Employee User",
                List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicant() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "applicant-" + UUID.randomUUID(),
                null,
                "Applicant",
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
