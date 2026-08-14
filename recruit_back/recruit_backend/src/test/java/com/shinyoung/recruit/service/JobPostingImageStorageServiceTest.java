package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JobPostingImageProperties;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private JobPostingImageStorageService newService() {
        JobPostingImageProperties properties = new JobPostingImageProperties();
        properties.setStorageRoot(tempDir);
        return new JobPostingImageStorageService(properties);
    }

    @Test
    void 저장하면_공고별_경로에_UUID_파일명으로_기록된다() {
        JobPostingImageStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1, 2, 3});

        StoredPostingImageFile stored = service.store(7L, file, "png");

        assertThat(stored.storagePath()).startsWith("job-postings/7/").endsWith(".png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.fileSize()).isEqualTo(3L);
        assertThat(service.exists(stored.storagePath())).isTrue();
    }

    @Test
    void 저장한_파일을_load하면_리소스를_반환한다() {
        JobPostingImageStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1, 2, 3});
        StoredPostingImageFile stored = service.store(7L, file, "png");

        PostingImageResource resource = service.load(stored.storagePath(), stored.contentType());

        assertThat(resource.contentLength()).isEqualTo(3L);
        assertThat(resource.contentType()).isEqualTo("image/png");
        assertThat(resource.resource().exists()).isTrue();
    }

    @Test
    void 없는_경로_load는_NotFound_예외() {
        JobPostingImageStorageService service = newService();

        assertThatThrownBy(() -> service.load("job-postings/1/none.png", "image/png"))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void 루트_밖_경로는_거부한다() {
        JobPostingImageStorageService service = newService();

        assertThatThrownBy(() -> service.load("../secret.txt", "image/png"))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void deleteIfExists는_파일을_지운다() {
        JobPostingImageStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1});
        StoredPostingImageFile stored = service.store(7L, file, "png");

        service.deleteIfExists(stored.storagePath());

        assertThat(service.exists(stored.storagePath())).isFalse();
    }
}
