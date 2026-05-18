package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationAnswerReplaceRequest;
import com.shinyoung.recruit.dto.request.ApplicationAnswerRequest;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.response.JobPostingQuestionResponse;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationAnswerService;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingQuestionService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationAnswerControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private ApplicationAnswerService applicationAnswerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_questions_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("answer-api-get", "Answer Api Get");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        authenticate(applicant);

        mockMvc.perform(get("/applications/{applicationId}/questions", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].questionId").value(question.questionId()))
                .andExpect(jsonPath("$.data[0].answerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].answerText").doesNotExist());
    }

    @Test
    void get_questions_returns_empty_array_when_no_question() throws Exception {
        Applicant applicant = createApplicant("answer-api-empty", "Answer Api Empty");
        Long jobPostingId = createJobPosting();
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        authenticate(applicant);

        mockMvc.perform(get("/applications/{applicationId}/questions", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void get_questions_includes_existing_answer() throws Exception {
        Applicant applicant = createApplicant("answer-api-existing", "Answer Api Existing");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, new ApplicationAnswerReplaceRequest(List.of(
                new ApplicationAnswerRequest(question.questionId(), "saved answer")
        )));
        authenticate(applicant);

        mockMvc.perform(get("/applications/{applicationId}/questions", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].answerId").isNumber())
                .andExpect(jsonPath("$.data[0].answerText").value("saved answer"));
    }

    @Test
    void post_answers_replace_success() throws Exception {
        Applicant applicant = createApplicant("answer-api-post", "Answer Api Post");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(question.questionId(), "new answer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].questionId").value(question.questionId()))
                .andExpect(jsonPath("$.data[0].answerText").value("new answer"));
    }

    @Test
    void post_empty_answers_success() throws Exception {
        Applicant applicant = createApplicant("answer-api-empty-post", "Answer Api Empty Post");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        applicationAnswerService.replaceAnswers(applicant.getId(), applicationId, new ApplicationAnswerReplaceRequest(List.of(
                new ApplicationAnswerRequest(question.questionId(), "saved answer")
        )));
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].answerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].answerText").doesNotExist());
    }

    @Test
    void validation_and_invalid_question_failures_return_api_response() throws Exception {
        Applicant applicant = createApplicant("answer-api-validation", "Answer Api Validation");
        Long jobPostingId = createJobPosting();
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/applications/{applicationId}/answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {
                                      "questionId": null,
                                      "answerText": "answer"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/applications/{applicationId}/answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(99999L, "answer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("answer-api-owner", "Answer Api Owner");
        Applicant other = createApplicant("answer-api-other", "Answer Api Other");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);
        publish(jobPostingId);
        Long applicationId = createApplication(owner, jobPostingId);
        authenticate(other);

        mockMvc.perform(get("/applications/{applicationId}/questions", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/applications/{applicationId}/answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(question.questionId(), "answer")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submitted_application_replace_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("answer-api-submitted", "Answer Api Submitted");
        Long jobPostingId = createJobPosting();
        JobPostingQuestionResponse question = createQuestion(jobPostingId, 0);
        publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/answers", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson(question.questionId(), "answer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unsupported_methods_and_paths_are_not_supported() throws Exception {
        mockMvc.perform(put("/applications/{applicationId}/answers", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/applications/{applicationId}/answers", 1L))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/applications/{applicationId}/answers", 1L))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/applications/{applicationId}/questions", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    private void authenticate(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    private Applicant createApplicant(String loginId, String applicantName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + applicantName);
        applicant.setUserName(applicantName);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private JobPostingQuestionResponse createQuestion(Long jobPostingId, int sortOrder) {
        return jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Question " + sortOrder,
                "Helper " + sortOrder,
                QuestionCategory.JOB_SPECIFIC,
                QuestionAnswerType.LONG_TEXT,
                true,
                null,
                3000,
                sortOrder
        ));
    }

    private void publish(Long jobPostingId) {
        jobPostingService.publish(jobPostingId);
    }

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }

    private String answerJson(Long questionId, String answerText) {
        return """
                {
                  "answers": [
                    {
                      "questionId": %d,
                      "answerText": "%s"
                    }
                  ]
                }
                """.formatted(questionId, answerText);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
