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
@ConfigurationProperties(prefix = "recruit.posting-image")
public class JobPostingImageProperties {

    /** 첨부파일 storage 헬스스캔과 충돌하지 않도록 attachment root와 반드시 분리한다. */
    @NotNull
    private Path storageRoot = Path.of("posting-images");

    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    @Min(1)
    private int maxImagesPerPosting = 10;

    @NotEmpty
    private List<String> allowedExtensions = new ArrayList<>(List.of("jpg", "jpeg", "png", "webp"));

    @NotEmpty
    private List<String> allowedContentTypes = new ArrayList<>(List.of("image/jpeg", "image/png", "image/webp"));

    public Path getStorageRoot() { return storageRoot; }
    public void setStorageRoot(Path storageRoot) { this.storageRoot = storageRoot; }
    public DataSize getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }
    public int getMaxImagesPerPosting() { return maxImagesPerPosting; }
    public void setMaxImagesPerPosting(int maxImagesPerPosting) { this.maxImagesPerPosting = maxImagesPerPosting; }
    public List<String> getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(List<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
    public List<String> getAllowedContentTypes() { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> allowedContentTypes) { this.allowedContentTypes = allowedContentTypes; }

    @AssertTrue(message = "maxFileSize must be greater than 0.")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }
}
