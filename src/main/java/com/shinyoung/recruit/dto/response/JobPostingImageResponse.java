package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPostingImage;

public record JobPostingImageResponse(
        Long id,
        String altText,
        Integer sortOrder,
        String contentType,
        Long fileSize
) {
    public static JobPostingImageResponse from(JobPostingImage image) {
        return new JobPostingImageResponse(
                image.getId(),
                image.getAltText(),
                image.getSortOrder(),
                image.getContentType(),
                image.getFileSize()
        );
    }
}
