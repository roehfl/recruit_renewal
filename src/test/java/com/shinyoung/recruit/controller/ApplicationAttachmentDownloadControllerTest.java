package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.AttachmentReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.AttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationAttachmentFileService;
import com.shinyoung.recruit.service.ApplicationAttachmentService;
import com.shinyoung.recruit.service.AttachmentStorageService;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.attachment.storage-root=build/test-attachments/application-attachment-download-controller",
        "recruit.attachment.max-file-size=5KB",
        "recruit.attachment.max-files-per-application=10",
        "recruit.attachment.max-total-size-per-application=50KB"
})
@Transactional
class ApplicationAttachmentDownloadControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicationAttachmentFileService fileService;

    @Autowired
    private ApplicationAttachmentService attachmentService;

    @Autowired
    private AttachmentStorageService storageService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationAttachmentRepository attachmentRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void applicant_download_returns_file_stream_headers_and_bytes() throws Exception {
        Applicant applicant = createApplicant("download-api-applicant", "Download Api Applicant");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        byte[] bytes = "resume-bytes".getBytes(StandardCharsets.UTF_8);
        Long attachmentId = upload(applicant, applicationId, "resume.pdf", "application/pdf", bytes);

        mockMvc.perform(get("/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, attachmentId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename=\"resume.pdf\"")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void applicant_download_encodes_korean_filename_with_filename_star() throws Exception {
        Applicant applicant = createApplicant("download-api-korean", "Download Api Korean");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(
                applicant,
                applicationId,
                "이력서.pdf",
                "application/pdf",
                "korean".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(get("/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, attachmentId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("%EC%9D%B4%EB%A0%A5%EC%84%9C.pdf")));
    }

    @Test
    void admin_download_returns_file_stream_for_stored_attachment() throws Exception {
        Applicant applicant = createApplicant("download-api-admin", "Download Api Admin");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        byte[] bytes = "admin-bytes".getBytes(StandardCharsets.UTF_8);
        Long attachmentId = upload(applicant, applicationId, "admin.pdf", "application/pdf", bytes);

        mockMvc.perform(get("/admin/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, attachmentId)
                        .with(authentication(employeeAuthentication("admin-download", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void applicant_download_rejects_metadata_only_missing_file_and_scope_mismatch_as_json_404() throws Exception {
        Applicant owner = createApplicant("download-api-owner", "Download Api Owner");
        Applicant other = createApplicant("download-api-other", "Download Api Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        Long otherApplicationId = createApplication(other, createPublishedJobPosting());
        Long storedAttachmentId = upload(owner, applicationId, "stored.pdf", "application/pdf", "stored".getBytes(StandardCharsets.UTF_8));
        ApplicationAttachment stored = attachmentRepository.findById(storedAttachmentId).orElseThrow();
        storageService.deleteIfExists(stored.getStoragePath());

        attachmentService.replaceAttachments(
                owner.getId(),
                applicationId,
                new AttachmentReplaceRequest(List.of(attachment("metadata.pdf", 10)))
        );
        Long metadataOnlyAttachmentId = attachmentRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .filter(attachment -> attachment.getOriginalFileName().equals("metadata.pdf"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, metadataOnlyAttachmentId)
                        .with(authentication(applicantAuthentication(owner))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(content().string(not(containsString("metadata-only/"))));

        mockMvc.perform(get("/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, storedAttachmentId)
                        .with(authentication(applicantAuthentication(owner))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("build/test-attachments"))))
                .andExpect(content().string(not(containsString("applications/"))));

        mockMvc.perform(get("/applications/{applicationId}/attachments/{attachmentId}/download", otherApplicationId, storedAttachmentId)
                        .with(authentication(applicantAuthentication(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void admin_download_rejects_not_found_and_scope_mismatch_as_json_404_without_storage_internals() throws Exception {
        Applicant applicant = createApplicant("download-api-admin-404", "Download Api Admin 404");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long otherApplicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "admin.pdf", "application/pdf", "admin".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/admin/applications/{applicationId}/attachments/{attachmentId}/download", 99999L, attachmentId)
                        .with(authentication(employeeAuthentication("admin-404", "ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("storagePath"))));

        mockMvc.perform(get("/admin/applications/{applicationId}/attachments/{attachmentId}/download", otherApplicationId, attachmentId)
                        .with(authentication(employeeAuthentication("admin-mismatch", "ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("storedFileName"))));
    }

    @Test
    void download_security_blocks_wrong_roles_and_anonymous_requests() throws Exception {
        Applicant applicant = createApplicant("download-api-security", "Download Api Security");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "security.pdf", "application/pdf", "security".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, attachmentId)
                        .with(authentication(employeeAuthentication("employee-applicant-path", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        mockMvc.perform(get("/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, attachmentId)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        mockMvc.perform(get("/admin/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, attachmentId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        mockMvc.perform(get("/admin/applications/{applicationId}/attachments/{attachmentId}/download", applicationId, attachmentId)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void applicant_delete_returns_api_response_without_storage_internals() throws Exception {
        Applicant applicant = createApplicant("delete-api-applicant", "Delete Api Applicant");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "delete.pdf", "application/pdf", "delete".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/applications/{applicationId}/attachments/{attachmentId}/delete", applicationId, attachmentId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.attachmentId").value(attachmentId))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.physicalDeleteRequested").value(true))
                .andExpect(jsonPath("$.data.storedFileName").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.physicalFileStatus").doesNotExist())
                .andExpect(jsonPath("$.data.downloadAvailable").doesNotExist());

        mockMvc.perform(post("/applications/{applicationId}/attachments/{attachmentId}/delete", applicationId, attachmentId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void applicant_delete_security_blocks_wrong_roles_and_anonymous_requests() throws Exception {
        Applicant applicant = createApplicant("delete-api-security", "Delete Api Security");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "security.pdf", "application/pdf", "security".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/applications/{applicationId}/attachments/{attachmentId}/delete", applicationId, attachmentId)
                        .with(authentication(employeeAuthentication("employee-delete-applicant-path", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/applications/{applicationId}/attachments/{attachmentId}/delete", applicationId, attachmentId)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void admin_delete_returns_api_response_and_validates_reason() throws Exception {
        Applicant adminApplicant = createApplicant("delete-api-admin", "Delete Api Admin");
        Long adminApplicationId = createApplication(adminApplicant, createPublishedJobPosting());
        Long adminAttachmentId = upload(adminApplicant, adminApplicationId, "admin.pdf", "application/pdf", "admin".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/admin/applications/{applicationId}/attachments/{attachmentId}/delete", adminApplicationId, adminAttachmentId)
                        .with(authentication(employeeAuthentication("admin-delete", "ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "admin cleanup"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.physicalDeleteRequested").value(true))
                .andExpect(jsonPath("$.data.storedFileName").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.physicalFileStatus").doesNotExist());

        Applicant recruitAdminApplicant = createApplicant("delete-api-recruit-admin", "Delete Api Recruit Admin");
        Long recruitAdminApplicationId = createApplication(recruitAdminApplicant, createPublishedJobPosting());
        Long recruitAdminAttachmentId = upload(recruitAdminApplicant, recruitAdminApplicationId, "recruit-admin.pdf", "application/pdf", "recruit".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/admin/applications/{applicationId}/attachments/{attachmentId}/delete", recruitAdminApplicationId, recruitAdminAttachmentId)
                        .with(authentication(employeeAuthentication("recruit-admin-delete", "ROLE_RECRUIT_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "recruit admin cleanup"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Applicant validationApplicant = createApplicant("delete-api-admin-validation", "Delete Api Admin Validation");
        Long validationApplicationId = createApplication(validationApplicant, createPublishedJobPosting());
        Long validationAttachmentId = upload(validationApplicant, validationApplicationId, "validation.pdf", "application/pdf", "validation".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/admin/applications/{applicationId}/attachments/{attachmentId}/delete", validationApplicationId, validationAttachmentId)
                        .with(authentication(employeeAuthentication("admin-delete-blank", "ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void admin_delete_security_blocks_applicant_and_anonymous_requests() throws Exception {
        Applicant applicant = createApplicant("delete-api-admin-security", "Delete Api Admin Security");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        Long attachmentId = upload(applicant, applicationId, "security.pdf", "application/pdf", "security".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/admin/applications/{applicationId}/attachments/{attachmentId}/delete", applicationId, attachmentId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "blocked"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/admin/applications/{applicationId}/attachments/{attachmentId}/delete", applicationId, attachmentId)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "blocked"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long upload(
            Applicant applicant,
            Long applicationId,
            String originalFileName,
            String contentType,
            byte[] bytes
    ) {
        AttachmentResponse response = fileService.upload(
                applicant.getId(),
                applicationId,
                new MockMultipartFile("file", originalFileName, contentType, bytes),
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null
        );
        return response.attachmentId();
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
                List.of(new JobPositionRequest("Backend", 2, 0)),
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

    private AttachmentRequest attachment(String originalFileName, Integer sortOrder) {
        return new AttachmentRequest(
                AttachmentType.RESUME,
                ApplicationSectionType.APPLICATION,
                null,
                originalFileName,
                null,
                null,
                "application/pdf",
                1024L,
                sortOrder
        );
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication employeeAuthentication(String loginId, String authority) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                loginId,
                "Recruit",
                "Employee User",
                List.of(new SimpleGrantedAuthority(authority))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
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
