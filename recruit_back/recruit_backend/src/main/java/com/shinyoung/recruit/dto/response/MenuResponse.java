package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Menu;
import com.shinyoung.recruit.enumeration.MenuSite;
import com.shinyoung.recruit.enumeration.MenuType;

import java.util.List;

public record MenuResponse(
        Long id,
        Long parentId,
        MenuSite site,
        MenuType type,
        String name,
        String path,
        Integer sortOrder,
        String icon,
        List<MenuResponse> children
) {
    public static MenuResponse from(Menu menu, List<MenuResponse> children) {
        Long parentId = menu.getParent() == null ? null : menu.getParent().getId();
        return new MenuResponse(
                menu.getId(),
                parentId,
                menu.getSite(),
                menu.getType(),
                menu.getName(),
                menu.getPath(),
                menu.getSortOrder(),
                menu.getIcon(),
                children
        );
    }
}
