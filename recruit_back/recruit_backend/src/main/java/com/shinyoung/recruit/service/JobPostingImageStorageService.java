package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JobPostingImageProperties;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class JobPostingImageStorageService {

    private final Path storageRoot;

    public JobPostingImageStorageService(JobPostingImageProperties properties) {
        this.storageRoot = properties.getStorageRoot().toAbsolutePath().normalize();
    }

    public StoredPostingImageFile store(Long jobPostingId, MultipartFile file, String extension) {
        String storedFileName = UUID.randomUUID() + "." + extension;
        String storagePath = "job-postings/%d/%s".formatted(jobPostingId, storedFileName);

        Path target = resolveUnderRoot(storagePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new InvalidJobPostingException("공고 이미지를 저장하지 못했습니다.");
        }

        return new StoredPostingImageFile(storagePath, file.getContentType(), file.getSize());
    }

    public PostingImageResource load(String storagePath, String contentType) {
        Path path = resolveUnderRoot(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
        try {
            return new PostingImageResource(new FileSystemResource(path), contentType, Files.size(path));
        } catch (IOException e) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
    }

    public boolean exists(String storagePath) {
        return Files.exists(resolveUnderRoot(storagePath));
    }

    public void deleteIfExists(String storagePath) {
        try {
            Files.deleteIfExists(resolveUnderRoot(storagePath));
        } catch (IOException | RuntimeException e) {
            // 행 삭제가 우선이며 파일 잔존은 재삭제 가능하므로 실패는 로깅만 한다.
            log.warn("Failed to delete posting image file.", e);
        }
    }

    private Path resolveUnderRoot(String storagePath) {
        if (storagePath == null || storagePath.isBlank() || Path.of(storagePath).isAbsolute()) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
        Path resolved = storageRoot.resolve(storagePath).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
        return resolved;
    }
}
