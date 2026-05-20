package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
public class LocalAttachmentStorageService implements AttachmentStorageService {

    private final Path storageRoot;

    public LocalAttachmentStorageService(AttachmentProperties properties) {
        this.storageRoot = properties.getStorageRoot().toAbsolutePath().normalize();
    }

    @Override
    public StoredAttachmentFile store(
            Long applicationId,
            MultipartFile file,
            String sanitizedOriginalFileName,
            String extension
    ) {
        String storedFileName = UUID.randomUUID() + "." + extension;
        LocalDate today = LocalDate.now();
        String storagePath = "applications/%d/%04d/%02d/%02d/%s".formatted(
                applicationId,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                storedFileName
        );

        Path target = resolveUnderRoot(storagePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new InvalidJobApplicationException("Attachment file could not be stored.");
        }

        return new StoredAttachmentFile(
                storedFileName,
                storagePath,
                file.getContentType(),
                file.getSize()
        );
    }

    @Override
    public void deleteIfExists(String storagePath) {
        try {
            Files.deleteIfExists(resolveUnderRoot(storagePath));
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to delete attachment file.", e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        return Files.exists(resolveUnderRoot(storagePath));
    }

    @Override
    public AttachmentStorageResource load(String storagePath) {
        Path path = resolveUnderRoot(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new JobApplicationNotFoundException("Attachment file was not found.");
        }
        try {
            return new AttachmentStorageResource(new FileSystemResource(path), Files.size(path));
        } catch (IOException e) {
            throw new JobApplicationNotFoundException("Attachment file was not found.");
        }
    }

    private Path resolveUnderRoot(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new JobApplicationNotFoundException("Attachment file was not found.");
        }
        if (Path.of(storagePath).isAbsolute()) {
            throw new JobApplicationNotFoundException("Attachment file was not found.");
        }
        Path resolved = storageRoot.resolve(storagePath).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new JobApplicationNotFoundException("Attachment file was not found.");
        }
        return resolved;
    }
}
