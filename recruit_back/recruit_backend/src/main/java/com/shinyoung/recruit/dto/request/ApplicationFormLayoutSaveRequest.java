package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApplicationFormLayoutSaveRequest(
        @NotEmpty(message = "Layout must contain at least one page.")
        @Valid
        List<PageRequest> pages
) {
    public record PageRequest(
            @NotNull(message = "Page number is required.")
            @Positive(message = "Page number must be positive.")
            Integer pageNo,

            @NotBlank(message = "Page title is required.")
            @Size(max = 100, message = "Page title must be 100 characters or less.")
            String title,

            @Size(max = 500, message = "Page description must be 500 characters or less.")
            String description,

            @NotNull(message = "Page sort order is required.")
            @PositiveOrZero(message = "Page sort order must be zero or positive.")
            Integer sortOrder,

            @NotEmpty(message = "Page must contain at least one item.")
            @Valid
            List<ItemRequest> items
    ) {}

    public record ItemRequest(
            @NotNull(message = "Section type is required.")
            ApplicationSectionType sectionType,

            @NotNull(message = "Item sort order is required.")
            @PositiveOrZero(message = "Item sort order must be zero or positive.")
            Integer sortOrder
    ) {}
}
