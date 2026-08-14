package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentFilePolicy {

    private static final int ORIGINAL_FILE_NAME_MAX_LENGTH = 255;
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private final AttachmentProperties properties;

    public ValidatedAttachmentFile validate(MultipartFile file) {
        if (file == null) {
            throw new InvalidJobApplicationException("Attachment file is required.");
        }
        if (file.isEmpty() || file.getSize() <= 0) {
            throw new InvalidJobApplicationException("Attachment file must not be empty.");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new InvalidJobApplicationException("Attachment file size exceeds the allowed limit.");
        }

        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        validateExtension(extension);
        validateContentType(file.getContentType());

        return new ValidatedAttachmentFile(originalFileName, extension);
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidJobApplicationException("Original file name is required.");
        }
        if (originalFileName.indexOf('/') >= 0 || originalFileName.indexOf('\\') >= 0) {
            throw new InvalidJobApplicationException("Original file name cannot contain path separators.");
        }
        if (containsControlCharacter(originalFileName)) {
            throw new InvalidJobApplicationException("Original file name cannot contain control characters.");
        }

        String sanitized = originalFileName.trim().replaceAll("\\s+", " ");
        if (sanitized.isBlank()) {
            throw new InvalidJobApplicationException("Original file name is required.");
        }
        if (sanitized.length() > ORIGINAL_FILE_NAME_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Original file name must be 255 characters or less.");
        }

        String baseName = sanitized;
        int dotIndex = sanitized.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = sanitized.substring(0, dotIndex);
        }
        if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
            throw new InvalidJobApplicationException("Original file name is reserved.");
        }

        return sanitized;
    }

    private boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String extractExtension(String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
            throw new InvalidJobApplicationException("Attachment file extension is required.");
        }
        return originalFileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension) {
        Set<String> allowed = properties.getAllowedExtensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowed.contains(extension)) {
            throw new InvalidJobApplicationException("Attachment file extension is not allowed.");
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidJobApplicationException("Attachment content type is required.");
        }
        Set<String> allowed = properties.getAllowedContentTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowed.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidJobApplicationException("Attachment content type is not allowed.");
        }
    }

    public record ValidatedAttachmentFile(String originalFileName, String extension) {
    }
}
