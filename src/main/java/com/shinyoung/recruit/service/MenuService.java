package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Menu;
import com.shinyoung.recruit.domain.repository.MenuRepository;
import com.shinyoung.recruit.dto.request.MenuSaveRequest;
import com.shinyoung.recruit.dto.response.MenuResponse;
import com.shinyoung.recruit.enumeration.MenuSite;
import com.shinyoung.recruit.enumeration.MenuType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {
    private final MenuRepository menuRepository;

    @Transactional
    public Long create(MenuSaveRequest request) {
        Menu parent = findParent(request.parentId());

        validateParentSite(request.site(), parent);
        validateTwoLevelMenu(parent);
        validatePathRule(parent, request.type(), request.path());
//        validateDuplicatedPath(request.site(), request.path(), null);

        Menu menu = Menu.create(
                request.site(),
                request.type(),
                parent,
                request.name(),
                request.path(),
                request.sortOrder()
        );

        return menuRepository.save(menu).getId();
    }

    @Transactional
    public Long update(Long menuId, MenuSaveRequest request) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴입니다. menuId=" + menuId));

        Menu parent = findParent(request.parentId());

        validateSelfParent(menu, parent);
        validateParentSite(request.site(), parent);
        validateTwoLevelMenu(parent);
        validatePathRule(parent, request.type(), request.path());
//        validateDuplicatedPath(request.site(), request.path(), menuId);

        menu.update(
                request.site(),
                request.type(),
                parent,
                request.name(),
                request.path(),
                request.sortOrder()
        );

        return menu.getId();
    }

    public MenuResponse get(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴입니다. menuId=" + menuId));

        return MenuResponse.from(menu, List.of());
    }

    public List<MenuResponse> getTree(MenuSite site) {
        List<Menu> menus = menuRepository.findAllBySiteOrderBySortOrderAscIdAsc(site);

        return buildTwoLevelTree(menus);
    }

    public List<MenuResponse> getBreadcrumb(MenuSite site, String path) {
        Menu menu = menuRepository.findBySiteAndPath(site, path)
                .orElseThrow(() -> new IllegalArgumentException("해당 경로의 메뉴가 없습니다. path=" + path));

        LinkedList<MenuResponse> breadcrumb = new LinkedList<>();

        Menu current = menu;

        while (current != null) {
            breadcrumb.addFirst(MenuResponse.from(current, List.of()));
            current = current.getParent();
        }

        return breadcrumb;
    }

    private List<MenuResponse> buildTwoLevelTree(List<Menu> menus) {
        Map<Long, List<Menu>> childrenMap = new LinkedHashMap<>();

        for (Menu menu : menus) {
            if (menu.getParent() == null) {
                continue;
            }

            Long parentId = menu.getParent().getId();

            childrenMap
                    .computeIfAbsent(parentId, key -> new ArrayList<>())
                    .add(menu);
        }

        return menus.stream()
                .filter(Menu::isRootMenu)
                .map(root -> {
                    List<MenuResponse> children = childrenMap
                            .getOrDefault(root.getId(), List.of())
                            .stream()
                            .map(child -> MenuResponse.from(child, List.of()))
                            .toList();

                    return MenuResponse.from(root, children);
                })
                .toList();
    }

    private Menu findParent(Long parentId) {
        if (parentId == null) {
            return null;
        }

        return menuRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부모 메뉴입니다. parentId=" + parentId));
    }

    private void validateParentSite(MenuSite site, Menu parent) {
        if (parent == null) {
            return;
        }

        if (parent.getSite() != site) {
            throw new IllegalArgumentException("부모 메뉴와 사이트 구분이 다릅니다.");
        }
    }

    private void validateTwoLevelMenu(Menu parent) {
        if (parent == null) {
            return;
        }

        if (parent.getParent() != null) {
            throw new IllegalArgumentException("소메뉴 하위에는 메뉴를 등록할 수 없습니다.");
        }
    }

    private void validateSelfParent(Menu menu, Menu parent) {
        if (parent == null) {
            return;
        }

        if (Objects.equals(menu.getId(), parent.getId())) {
            throw new IllegalArgumentException("자기 자신을 부모 메뉴로 지정할 수 없습니다.");
        }
    }

    private void validatePathRule(Menu parent, MenuType type, String path) {
        /*
         * 대메뉴는 그룹 역할도 가능하므로 path null 허용.
         * 소메뉴는 실제 이동 대상이므로 path 필수.
         */
        if (parent != null && !StringUtils.hasText(path)) {
            throw new IllegalArgumentException("소메뉴에는 path가 필요합니다.");
        }

        if (!StringUtils.hasText(path)) {
            return;
        }

        if (type == MenuType.ROUTE && !path.startsWith("/")) {
            throw new IllegalArgumentException("ROUTE 타입의 path는 '/'로 시작해야 합니다.");
        }

        if (type == MenuType.URL && !(path.startsWith("http://") || path.startsWith("https://"))) {
            throw new IllegalArgumentException("URL 타입의 path는 http:// 또는 https:// 로 시작해야 합니다.");
        }
    }

}
