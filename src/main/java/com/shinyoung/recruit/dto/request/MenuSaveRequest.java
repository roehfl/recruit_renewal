package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.MenuSite;
import com.shinyoung.recruit.enumeration.MenuType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MenuSaveRequest(
        @NotNull
        MenuSite site,
        @NotNull
        MenuType type,
        Long parentId,
        @NotBlank
        String name,
        String path,
        Integer sortOrder
) {
}
