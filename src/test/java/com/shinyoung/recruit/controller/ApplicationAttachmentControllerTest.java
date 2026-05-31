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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationAttachmentControllerTest {

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
    private JobApplicationService jobApplicationService;

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
    void get_attachments_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("attachment-api-get", "Attachment Api Get");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(get("/api/applications/{applicationId}/attachments", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void replace_attachments_returns_api_response_without_internal_storage_fields() throws Exception {
        Applicant applicant = createApplicant("attachment-api-replace", "Attachment Api Replace");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/attachments", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAttachmentJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].originalFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data[0].storedFileName").doesNotExist())
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data[0].physicalFileStatus").doesNotExist())
                .andExpect(jsonPath("$.data[0].downloadAvailable").doesNotExist());
    }

    @Test
    void upload_attachment_file_returns_api_response_without_internal_storage_fields() throws Exception {
        Applicant applicant = createApplicant("attachment-api-upload", "Attachment Api Upload");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(multipart("/api/applications/{applicationId}/attachments/files", applicationId)
                        .file(file("resume.pdf", "application/pdf", "resume"))
                        .param("attachmentType", "RESUME")
                        .param("sectionType", "APPLICATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.originalFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.data.sortOrder").value(0))
                .andExpect(jsonPath("$.data.storedFileName").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.physicalFileStatus").doesNotExist())
                .andExpect(jsonPath("$.data.downloadAvailable").doesNotExist());
    }

    @Test
    void upload_attachment_file_rejects_forbidden_multipart_parts() throws Exception {
        Applicant applicant = createApplicant("attachment-api-upload-forbidden", "Attachment Api Upload Forbidden");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(multipart("/api/applications/{applicationId}/attachments/files", applicationId)
                        .file(file("resume.pdf", "application/pdf", "resume"))
                        .param("attachmentType", "RESUME")
                        .param("sectionType", "APPLICATION")
                        .param("sortOrder", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(multipart("/api/applications/{applicationId}/attachments/files", applicationId)
                        .file(file("resume.pdf", "application/pdf", "resume"))
                        .param("attachmentType", "RESUME")
                        .param("sectionType", "APPLICATION")
                        .param("storedFileName", "stored-resume.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void validation_and_invalid_enum_failures_return_api_response() throws Exception {
        Applicant applicant = createApplicant("attachment-api-validation", "Attachment Api Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/attachments", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attachments": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/applications/{applicationId}/attachments", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attachments": [
                                    {
                                      "attachmentType": "UNKNOWN_TYPE",
                                      "sectionType": "APPLICATION",
                                      "originalFileName": "resume.pdf",
                                      "storedFileName": "stored-resume.pdf",
                                      "storagePath": "/attachments/resume.pdf",
                                      "contentType": "application/pdf",
                                      "fileSize": 1024,
                                      "sortOrder": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void metadata_replace_rejects_client_supplied_storage_fields() throws Exception {
        Applicant applicant = createApplicant("attachment-api-forbidden-storage", "Attachment Api Forbidden Storage");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/attachments", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attachments": [
                                    {
                                      "attachmentType": "RESUME",
                                      "sectionType": "APPLICATION",
                                      "originalFileName": "resume.pdf",
                                      "storedFileName": "stored-resume.pdf",
                                      "contentType": "application/pdf",
                                      "fileSize": 1024,
                                      "sortOrder": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("attachment-api-owner", "Attachment Api Owner");
        Applicant other = createApplicant("attachment-api-other", "Attachment Api Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        authenticate(other);

        mockMvc.perform(get("/api/applications/{applicationId}/attachments", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/applications/{applicationId}/attachments", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAttachmentJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submitted_application_replace_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("attachment-api-submitted", "Attachment Api Submitted");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/attachments", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAttachmentJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_and_delete_methods_are_not_supported() throws Exception {
        mockMvc.perform(put("/api/applications/{applicationId}/attachments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/applications/{applicationId}/attachments", 1L))
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

    private MockMultipartFile file(String originalFileName, String contentType, String content) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                contentType,
                content.getBytes(StandardCharsets.UTF_8)
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

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long createPublishedJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
    }

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }

    private String validAttachmentJson() {
        return """
                {
                  "attachments": [
                    {
                      "attachmentType": "RESUME",
                      "sectionType": "APPLICATION",
                      "originalFileName": "resume.pdf",
                      "contentType": "application/pdf",
                      "fileSize": 1024,
                      "sortOrder": 0
                    }
                  ]
                }
                """;
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
