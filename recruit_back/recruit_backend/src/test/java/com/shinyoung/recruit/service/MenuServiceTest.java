package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.MenuSaveRequest;
import com.shinyoung.recruit.dto.response.MenuResponse;
import com.shinyoung.recruit.enumeration.MenuSite;
import com.shinyoung.recruit.enumeration.MenuType;
import com.shinyoung.recruit.exception.InvalidMenuException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        // SecurityConfigTest가 커밋하는 ADMIN "/admin/menus"와 path 중복 검증으로 충돌하지 않도록 별도 path를 쓴다.
        menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, parentId, "메뉴 관리", "/admin/menus-tree-test", 1, "MenuOutlined"));

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

    @Test
    void 같은_사이트에_같은_path로_생성하면_예외() {
        menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "공고 목록", "/admin/dup-create", 1, null));

        assertThatThrownBy(() -> menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "공고 목록 2", "/admin/dup-create", 2, null)))
                .isInstanceOf(InvalidMenuException.class)
                .hasMessage("같은 사이트에 동일한 경로를 사용하는 메뉴가 이미 있습니다. path=/admin/dup-create");
    }

    @Test
    void 소메뉴도_같은_사이트의_다른_메뉴와_path가_같으면_예외() {
        Long parentId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "공고 관리", "/admin/dup-sub", 1, null));

        assertThatThrownBy(() -> menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, parentId, "공고 목록", "/admin/dup-sub", 1, null)))
                .isInstanceOf(InvalidMenuException.class);
    }

    @Test
    void 수정시_자기_자신의_path는_중복으로_보지_않는다() {
        Long menuId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "통계", "/admin/dup-self", 1, null));

        menuService.update(menuId, new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "통계 현황", "/admin/dup-self", 2, null));

        assertThat(menuService.get(menuId).name()).isEqualTo("통계 현황");
    }

    @Test
    void 수정시_다른_메뉴의_path로_바꾸면_예외() {
        menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "공고", "/admin/dup-update-a", 1, null));
        Long menuId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "지원자", "/admin/dup-update-b", 2, null));

        assertThatThrownBy(() -> menuService.update(menuId, new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "지원자", "/admin/dup-update-a", 2, null)))
                .isInstanceOf(InvalidMenuException.class);
    }

    @Test
    void 사이트가_다르면_같은_path를_허용한다() {
        menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "공지", "/dup-cross-site", 1, null));

        Long applicantMenuId = menuService.create(new MenuSaveRequest(
                MenuSite.APPLICANT, MenuType.ROUTE, null, "공지", "/dup-cross-site", 1, null));

        assertThat(menuService.get(applicantMenuId).path()).isEqualTo("/dup-cross-site");
    }

    @Test
    void path가_없는_대메뉴는_여러개_둘_수_있다() {
        Long nullPathId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "그룹 A", null, 1, null));
        Long anotherNullPathId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "그룹 B", null, 2, null));
        Long blankPathId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "그룹 C", "", 3, null));
        Long anotherBlankPathId = menuService.create(new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "그룹 D", "", 4, null));

        menuService.update(nullPathId, new MenuSaveRequest(
                MenuSite.ADMIN, MenuType.ROUTE, null, "그룹 A2", null, 1, null));

        assertThat(List.of(nullPathId, anotherNullPathId, blankPathId, anotherBlankPathId)).doesNotHaveDuplicates();
        assertThat(menuService.get(nullPathId).name()).isEqualTo("그룹 A2");
    }
}
