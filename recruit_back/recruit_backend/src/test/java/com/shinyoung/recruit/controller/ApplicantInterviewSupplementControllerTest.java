package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionSaveRequest;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.InterviewSupplementAdminService;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantInterviewSupplementControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private Clock clock;
    @Autowired private InterviewSupplementAdminService adminService;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewParticipantRepository participantRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 입력_시간_안이면_목록_조회_저장이_된다() throws Exception {
        LocalDateTime arrival = LocalDateTime.now(clock).minusMinutes(10);
        Fixture f = fixture(arrival);
        String form = "/api/applicant/applications/" + f.application().getId()
                + "/interview-supplements/" + f.stage().getId();

        mockMvc.perform(get("/api/applicant/interview-supplements").with(authentication(auth(f.applicant()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].applicationId").value(f.application().getId()))
                .andExpect(jsonPath("$.data[0].stageId").value(f.stage().getId()))
                .andExpect(jsonPath("$.data[0].open").value(true))
                .andExpect(jsonPath("$.data[0].questionCount").value(1));

        mockMvc.perform(get(form).with(authentication(auth(f.applicant()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].questionId").value(f.questionId()))
                .andExpect(jsonPath("$.data.questions[0].answerText").doesNotExist())
                .andExpect(jsonPath("$.data.remainingSeconds").isNumber());

        mockMvc.perform(post(form + "/answers")
                        .with(authentication(auth(f.applicant())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":" + f.questionId() + ",\"answerText\":\"답변\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredCount").value(1))
                .andExpect(jsonPath("$.data.savedAt").exists());
    }

    @Test
    void 종료된_창은_400이고_1000자를_넘는_답은_400이다() throws Exception {
        Fixture closed = fixture(LocalDateTime.now(clock).minusHours(3));
        mockMvc.perform(post("/api/applicant/applications/{a}/interview-supplements/{s}/answers",
                        closed.application().getId(), closed.stage().getId())
                        .with(authentication(auth(closed.applicant())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":" + closed.questionId() + ",\"answerText\":\"늦음\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("추가사항 입력 시간이 아닙니다."));

        Fixture open = fixture(LocalDateTime.now(clock).minusMinutes(1));
        mockMvc.perform(post("/api/applicant/applications/{a}/interview-supplements/{s}/answers",
                        open.application().getId(), open.stage().getId())
                        .with(authentication(auth(open.applicant())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":" + open.questionId()
                                + ",\"answerText\":\"" + "가".repeat(1001) + "\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자는_지원자_API를_쓸_수_없다() throws Exception {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "employee-" + UUID.randomUUID(), "HR", "Employee User",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication admin = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        mockMvc.perform(get("/api/applicant/interview-supplements").with(authentication(admin)))
                .andExpect(status().isForbidden());
    }

    private record Fixture(Stage stage, Applicant applicant, JobApplication application, Long questionId) {
    }

    private Fixture fixture(LocalDateTime arrival) {
        JobPosting posting = JobPosting.create("Posting", "Content", arrival.minusDays(30), arrival.minusDays(10));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        Stage stage = stageRepository.saveAndFlush(
                Stage.create(posting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false));

        Applicant applicant = new Applicant(HashUtil.sha256("test-ci-" + UUID.randomUUID()));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName("지원자");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.saveAndFlush(applicant);
        JobPosition position = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, position, "지원자", posting.getTitle(), position.getPositionName());
        application.submit(arrival.minusDays(20));
        application = jobApplicationRepository.saveAndFlush(application);

        Interview interview = Interview.createDraft(posting, stage, "1", arrival.plusMinutes(30), arrival,
                InterviewMethod.IN_PERSON, "본사", null, null, null);
        interview.confirm();
        interview = interviewRepository.saveAndFlush(interview);
        participantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, 1));

        adminService.enable(stage.getId());
        Long questionId = adminService.addQuestion(stage.getId(),
                new InterviewSupplementQuestionSaveRequest("질문")).questions().get(0).questionId();
        return new Fixture(stage, applicant, application, questionId);
    }

    private Authentication auth(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
