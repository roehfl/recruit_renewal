package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
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
import org.springframework.util.FileSystemUtils;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.attachment.storage-root=build/test-attachments/attachment-storage-health-controller",
        "recruit.attachment.max-file-size=5KB",
        "recruit.attachment.max-files-per-application=10",
        "recruit.attachment.max-total-size-per-application=50KB"
})
@Transactional
class AdminAttachmentStorageHealthControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private AttachmentProperties attachmentProperties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        FileSystemUtils.deleteRecursively(attachmentProperties.getStorageRoot());
        Files.createDirectories(attachmentProperties.getStorageRoot());
    }

    @Test
    void admin_scan_returns_dry_run_response_without_storage_internals() throws Exception {
        Path orphan = attachmentProperties.getStorageRoot()
                .resolve("applications/999/2026/06/15/orphan.pdf");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "orphan", StandardCharsets.UTF_8);

        mockMvc.perform(post("/admin/attachments/storage-health/scan")
                        .with(authentication(employeeAuthentication("admin-health", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.orphanPhysicalFileCount").value(1))
                .andExpect(jsonPath("$.data.issues[0].category").value("ORPHAN_PHYSICAL_FILE"))
                .andExpect(jsonPath("$.data.issues[0].fileKeyHash").exists())
                .andExpect(jsonPath("$.data.issues[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.issues[0].storedFileName").doesNotExist())
                .andExpect(content().string(not(containsString("build/test-attachments"))))
                .andExpect(content().string(not(containsString("applications/"))));
    }

    @Test
    void recruit_admin_can_scan_storage_health() throws Exception {
        mockMvc.perform(post("/admin/attachments/storage-health/scan")
                        .with(authentication(employeeAuthentication("recruit-admin-health", "ROLE_RECRUIT_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dryRun").value(true));
    }

    @Test
    void applicant_and_anonymous_requests_are_blocked() throws Exception {
        Applicant applicant = createApplicant("health-applicant", "Health Applicant");

        mockMvc.perform(post("/admin/attachments/storage-health/scan")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/admin/attachments/storage-health/scan")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
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
}
