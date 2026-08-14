package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;

import java.util.List;

public record AdminApplicationFormLayoutResponse(
        Long jobPostingId,
        boolean layoutStored,
        boolean editable,
        List<PageResponse> pages,
        List<SectionAvailability> availableSections
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
            Integer sortOrder,
            boolean enabled,
            boolean required,
            boolean placed
    ) {}

    public record SectionAvailability(
            ApplicationSectionType sectionType,
            String sectionName,
            boolean enabled,
            boolean required,
            boolean placed,
            String source
    ) {}
}
