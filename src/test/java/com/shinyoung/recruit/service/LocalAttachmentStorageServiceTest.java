package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAttachmentStorageServiceTest {

    @TempDir
    private Path storageRoot;

    @Test
    void load_existing_file_returns_resource_and_actual_content_length() throws Exception {
        LocalAttachmentStorageService storageService = storageService();
        StoredAttachmentFile stored = storageService.store(
                10L,
                new MockMultipartFile(
                        "file",
                        "resume.pdf",
                        "application/pdf",
                        "resume-content".getBytes(StandardCharsets.UTF_8)
                ),
                "resume.pdf",
                "pdf"
        );

        AttachmentStorageResource resource = storageService.load(stored.storagePath());

        assertThat(resource.resource().exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo("resume-content".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void load_missing_file_fails_with_not_found_signal() {
        LocalAttachmentStorageService storageService = storageService();

        assertThatThrownBy(() -> storageService.load("applications/1/missing.pdf"))
                .isInstanceOf(JobApplicationNotFoundException.class)
                .hasMessage("Attachment file was not found.");
    }

    @Test
    void load_blocks_path_traversal_outside_storage_root() throws Exception {
        Path outside = storageRoot.getParent().resolve("outside.pdf");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);
        LocalAttachmentStorageService storageService = storageService();

        assertThatThrownBy(() -> storageService.load("../outside.pdf"))
                .isInstanceOf(JobApplicationNotFoundException.class)
                .hasMessage("Attachment file was not found.");
    }

    @Test
    void load_blocks_absolute_storage_path_even_when_it_points_under_storage_root() throws Exception {
        Path absolutePath = storageRoot.resolve("applications/1/resume.pdf").toAbsolutePath().normalize();
        Files.createDirectories(absolutePath.getParent());
        Files.writeString(absolutePath, "resume", StandardCharsets.UTF_8);
        LocalAttachmentStorageService storageService = storageService();

        assertThatThrownBy(() -> storageService.load(absolutePath.toString()))
                .isInstanceOf(JobApplicationNotFoundException.class)
                .hasMessage("Attachment file was not found.");
    }

    @Test
    void delete_if_exists_with_result_reports_deleted_absent_and_invalid_path_without_exposing_path() throws Exception {
        Path storedPath = storageRoot.resolve("applications/1/2026/06/15/resume.pdf");
        Files.createDirectories(storedPath.getParent());
        Files.writeString(storedPath, "resume", StandardCharsets.UTF_8);
        LocalAttachmentStorageService storageService = storageService();

        AttachmentStorageDeleteResult deleted = storageService.deleteIfExistsWithResult(
                "applications/1/2026/06/15/resume.pdf"
        );
        AttachmentStorageDeleteResult absent = storageService.deleteIfExistsWithResult(
                "applications/1/2026/06/15/resume.pdf"
        );
        AttachmentStorageDeleteResult invalid = storageService.deleteIfExistsWithResult("../outside.pdf");

        assertThat(deleted.requested()).isTrue();
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.existed()).isTrue();
        assertThat(deleted.failed()).isFalse();
        assertThat(absent.requested()).isTrue();
        assertThat(absent.deleted()).isFalse();
        assertThat(absent.existed()).isFalse();
        assertThat(absent.failed()).isFalse();
        assertThat(invalid.failed()).isTrue();
        assertThat(invalid.failureCode()).isEqualTo("INVALID_STORAGE_PATH");
        assertThat(invalid.message()).doesNotContain("outside.pdf");
    }

    private LocalAttachmentStorageService storageService() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setStorageRoot(storageRoot);
        return new LocalAttachmentStorageService(properties);
    }
}
