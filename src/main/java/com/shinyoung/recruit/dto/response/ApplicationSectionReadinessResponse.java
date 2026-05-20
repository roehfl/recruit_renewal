package com.shinyoung.recruit.dto.response;

public record ApplicationSectionReadinessResponse(
        String sectionCode,
        String sectionName,
        boolean required,
        boolean complete,
        String reasonCode,
        String message
) {
}
