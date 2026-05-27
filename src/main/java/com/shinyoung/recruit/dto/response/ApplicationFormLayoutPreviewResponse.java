package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;

import java.util.List;

public record ApplicationFormLayoutPreviewResponse(
        Long jobPostingId,
        String jobPostingTitle,
        List<PageResponse> pages
) {
    public record PageResponse(
            Integer pageNo,
            String title,
            String description,
            Integer sortOrder,
            List<ItemResponse> items
    ) {}

    public record ItemResponse(
            ApplicationSectionType sectionType,
            String sectionName,
            boolean required,
            Integer sortOrder
    ) {}
}
