package com.shinyoung.recruit.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "recruit.attachment")
public class AttachmentProperties {

    @NotNull
    private Path storageRoot = Path.of("attachments");

    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    @Min(1)
    private int maxFilesPerApplication = 20;

    @NotNull
    private DataSize maxTotalSizePerApplication = DataSize.ofMegabytes(100);

    @NotEmpty
    private List<String> allowedExtensions = new ArrayList<>(List.of(
            "pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx", "hwp", "hwpx"
    ));

    @NotEmpty
    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/x-hwp",
            "application/haansofthwp",
            "application/vnd.hancom.hwpx"
    ));

    public Path getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxFilesPerApplication() {
        return maxFilesPerApplication;
    }

    public void setMaxFilesPerApplication(int maxFilesPerApplication) {
        this.maxFilesPerApplication = maxFilesPerApplication;
    }

    public DataSize getMaxTotalSizePerApplication() {
        return maxTotalSizePerApplication;
    }

    public void setMaxTotalSizePerApplication(DataSize maxTotalSizePerApplication) {
        this.maxTotalSizePerApplication = maxTotalSizePerApplication;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    @AssertTrue(message = "maxFileSize must be greater than 0.")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }

    @AssertTrue(message = "maxTotalSizePerApplication must be greater than 0.")
    public boolean isMaxTotalSizePerApplicationPositive() {
        return maxTotalSizePerApplication != null && maxTotalSizePerApplication.toBytes() > 0;
    }
}
