package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequirementReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequirementRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.service.JobPostingAttachmentRequirementService;
import com.shinyoung.recruit.service.JobPostingQuestionService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class JobPostingPublicControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingQuestionService jobPostingQuestionService;

    @Autowired
    private JobPostingAttachmentRequirementService attachmentRequirementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void public_list_exposes_applicant_fields_and_hides_internal_fields() throws Exception {
        createPublishedPosting(publicRequest("public posting", true, displayStart(), displayEnd(), true));

        mockMvc.perform(get("/job-postings")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("public posting"))
                .andExpect(jsonPath("$.data.content[0].postingType").value("EXPERIENCED_RECRUITMENT"))
                .andExpect(jsonPath("$.data.content[0].summary").value("public summary"))
                .andExpect(jsonPath("$.data.content[0].receptionStatus").value("ACCEPTING"))
                .andExpect(jsonPath("$.data.content[0].accepting").value(true))
                .andExpect(jsonPath("$.data.content[0].pinned").value(true))
                .andExpect(jsonPath("$.data.content[0].positions[0].applicationType").value("NEW_GRADUATE"))
                .andExpect(jsonPath("$.data.content[0].positions[0].jobGroup").value("Research"))
                .andExpect(jsonPath("$.data.content[0].positions[0].jobTitle").value("Analyst"))
                .andExpect(jsonPath("$.data.content[0].positions[0].workLocation").value("Seoul"))
                .andExpect(jsonPath("$.data.content[0].positions[0].employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.requiredSectionCount").value(2))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.optionalSectionCount").value(1))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.requiredQuestionCount").value(0))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.attachmentRequired").value(false))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.sections[0].sectionCode").value("EDUCATION"))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.sections[0].requirementType").value("REQUIRED"))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.sections[8].sectionCode").value("ATTACHMENT"))
                .andExpect(jsonPath("$.data.content[0].applicationFormRequiredPolicy.sections[8].requirementType").value("DISABLED"))
                .andExpect(jsonPath("$.data.content[0].attachmentRequirements").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].visible").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].displayStartDateTime").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].displayEndDateTime").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].displayOrder").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].contentHtml").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].applicationFormConfig").doesNotExist());
    }

    @Test
    void public_detail_exposes_detail_fields_and_hides_internal_fields() throws Exception {
        Long id = createPublishedPosting(publicRequest("detail posting", true, displayStart(), displayEnd(), true));

        mockMvc.perform(get("/job-postings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("detail posting"))
                .andExpect(jsonPath("$.data.postingType").value("EXPERIENCED_RECRUITMENT"))
                .andExpect(jsonPath("$.data.summary").value("public summary"))
                .andExpect(jsonPath("$.data.contentHtml").value("<p>public content</p>"))
                .andExpect(jsonPath("$.data.receptionStatus").value("ACCEPTING"))
                .andExpect(jsonPath("$.data.accepting").value(true))
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.jobPositions[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.applicationFormConfig.useEducation").value(true))
                .andExpect(jsonPath("$.data.applicationFormConfig.requireEducation").value(true))
                .andExpect(jsonPath("$.data.applicationFormConfig.requireCertificate").value(false))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.requiredSectionCount").value(2))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.optionalSectionCount").value(1))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[4].sectionCode").value("MILITARY"))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[4].requirementType").value("REQUIRED"))
                .andExpect(jsonPath("$.data.attachmentRequirements").isArray())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.visible").doesNotExist())
                .andExpect(jsonPath("$.data.displayStartDateTime").doesNotExist())
                .andExpect(jsonPath("$.data.displayEndDateTime").doesNotExist())
                .andExpect(jsonPath("$.data.displayOrder").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.closedAt").doesNotExist());
    }

    @Test
    void public_detail_exposes_question_policy_counts_as_json() throws Exception {
        Long id = jobPostingService.create(publicRequest("question policy", true, displayStart(), displayEnd(), false));
        createQuestion(id, 0, true);
        createQuestion(id, 1, false);
        jobPostingService.publish(id);

        mockMvc.perform(get("/job-postings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.requiredQuestionCount").value(1))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.optionalQuestionCount").value(1))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.hasRequiredQuestion").value(true))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[7].sectionCode").value("QUESTION"))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[7].enabled").value(true))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[7].required").value(true))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[7].requirementType").value("REQUIRED"));
    }

    @Test
    void public_detail_exposes_safe_attachment_requirements_as_json() throws Exception {
        Long id = jobPostingService.create(publicRequest("attachment policy", true, displayStart(), displayEnd(), false));
        attachmentRequirementService.replaceRequirements(
                id,
                new AttachmentRequirementReplaceRequest(List.of(
                        new AttachmentRequirementRequest(
                                AttachmentType.RESUME,
                                ApplicationSectionType.APPLICATION,
                                true,
                                1,
                                0,
                                "Resume",
                                "Upload resume."
                        )
                ))
        );
        jobPostingService.publish(id);

        mockMvc.perform(get("/job-postings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.attachmentRequired").value(true))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[8].sectionCode").value("ATTACHMENT"))
                .andExpect(jsonPath("$.data.applicationFormRequiredPolicy.sections[8].requirementType").value("REQUIRED"))
                .andExpect(jsonPath("$.data.attachmentRequirements[0].attachmentType").value("RESUME"))
                .andExpect(jsonPath("$.data.attachmentRequirements[0].sectionType").value("APPLICATION"))
                .andExpect(jsonPath("$.data.attachmentRequirements[0].required").value(true))
                .andExpect(jsonPath("$.data.attachmentRequirements[0].minCount").value(1))
                .andExpect(jsonPath("$.data.attachmentRequirements[0].displayName").value("Resume"))
                .andExpect(jsonPath("$.data.attachmentRequirements[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.attachmentRequirements[0].physicalFileStatus").doesNotExist());
    }

    @Test
    void public_detail_returns_not_found_for_hidden_or_out_of_display_period() throws Exception {
        Long hiddenId = createPublishedPosting(publicRequest("hidden", false, displayStart(), displayEnd(), true));
        Long futureDisplayId = createPublishedPosting(publicRequest(
                "future display",
                true,
                LocalDateTime.of(2026, 6, 2, 0, 0),
                displayEnd(),
                false
        ));

        mockMvc.perform(get("/job-postings/{id}", hiddenId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/job-postings/{id}", futureDisplayId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long createPublishedPosting(JobPostingCreateRequest request) {
        Long id = jobPostingService.create(request);
        jobPostingService.publish(id);
        return id;
    }

    private void createQuestion(Long jobPostingId, int sortOrder, boolean required) {
        jobPostingQuestionService.createQuestion(
                jobPostingId,
                new JobPostingQuestionCreateRequest(
                        null,
                        "Question " + sortOrder,
                        "Helper " + sortOrder,
                        QuestionCategory.GENERAL,
                        QuestionAnswerType.SHORT_TEXT,
                        required,
                        0,
                        500,
                        sortOrder
                )
        );
    }

    private JobPostingCreateRequest publicRequest(
            String title,
            boolean visible,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            boolean pinned
    ) {
        return new JobPostingCreateRequest(
                title,
                JobPostingType.EXPERIENCED_RECRUITMENT,
                "public summary",
                "<p>public content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                displayStartDateTime,
                displayEndDateTime,
                visible,
                pinned,
                3,
                List.of(
                        new JobPositionRequest(
                                "Analyst",
                                JobPositionApplicationType.NEW_GRADUATE,
                                "Research",
                                "Analyst",
                                "Seoul",
                                EmploymentType.FULL_TIME,
                                1,
                                1
                        )
                ),
                new ApplicationFormConfigRequest(true, false, true, false, true, false, false)
        );
    }

    private LocalDateTime displayStart() {
        return LocalDateTime.of(2026, 5, 25, 9, 0);
    }

    private LocalDateTime displayEnd() {
        return LocalDateTime.of(2026, 7, 1, 18, 0);
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
