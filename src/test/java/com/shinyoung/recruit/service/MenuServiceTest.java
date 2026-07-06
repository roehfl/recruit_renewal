package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.MenuSaveRequest;
import com.shinyoung.recruit.dto.response.MenuResponse;
import com.shinyoung.recruit.enumeration.MenuSite;
import com.shinyoung.recruit.enumeration.MenuType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MenuServiceTest {

    @Autowired
    private MenuService menuService;

    @Test
    void 메뉴_생성시_아이콘이_저장되고_단건조회로_반환된다() {
        Long menuId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "지원자 관리", "/admin/applicants", 1, "TeamOutlined"));

        MenuResponse response = menuService.get(menuId);

        assertThat(response.icon()).isEqualTo("TeamOutlined");
    }

    @Test
    void 아이콘은_선택값이라_null로_생성할_수_있다() {
        Long menuId = menuService.create(new MenuSaveRequest(
                MenuSite.APPLICANT, MenuType.ROUTE, null, "채용공고", "/jobs", 1, null));

        MenuResponse response = menuService.get(menuId);

        assertThat(response.icon()).isNull();
    }

    @Test
    void 트리조회시_대메뉴와_소메뉴의_아이콘이_각각_반환된다() {
        Long parentId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "설정", null, 1, "SettingOutlined"));
        menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, parentId, "메뉴 관리", "/admin/menus", 1, "MenuOutlined"));

        List<MenuResponse> tree = menuService.getTree(MenuSite.ADMIN);

        MenuResponse root = tree.stream()
                .filter(menu -> menu.id().equals(parentId))
                .findFirst()
                .orElseThrow();
        assertThat(root.icon()).isEqualTo("SettingOutlined");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).icon()).isEqualTo("MenuOutlined");
    }

    @Test
    void 아이콘을_수정하면_변경된_값이_반환된다() {
        Long menuId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "통계", "/admin/stats", 1, "BarChartOutlined"));

        menuService.update(menuId, new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "통계", "/admin/stats", 1, "PieChartOutlined"));

        assertThat(menuService.get(menuId).icon()).isEqualTo("PieChartOutlined");
    }
}
