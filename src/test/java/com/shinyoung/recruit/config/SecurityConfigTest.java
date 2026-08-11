package com.shinyoung.recruit.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityConfigTest {

    /*
     * 컨트롤러 엔드포인트는 WebMvcConfig 의 addPathPrefix 로 /api 가 붙는다.
     * MenuController 의 base path 는 /menu 라 메뉴 관리 write 경로가 /api/menu/admin/menu 가 되고,
     * broad /api/admin/** 매처에 걸리지 않는다. 전용 매처가 없으면 anyRequest().permitAll() 로 흘러
     * 비인증 사용자가 메뉴를 생성/수정할 수 있으므로 아래 테스트로 고정한다.
     */
    private static final String MENU_CREATE_PATH = "/api/menu/admin/menu";
    private static final String MENU_UPDATE_PATH = "/api/menu/admin/menu/1";
    private static final String MENU_SAVE_BODY = """
            {"site":"ADMIN","type":"ROUTE","name":"메뉴 관리","path":"/admin/menus"}
            """;

    @Autowired
    MockMvc mockMvc;

    @Test
    void 메뉴_트리_조회는_인증없이_허용() throws Exception {
        mockMvc.perform(get("/api/menu/tree").param("site", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void 메뉴_생성은_비인증이면_401() throws Exception {
        mockMvc.perform(post(MENU_CREATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_SAVE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메뉴_수정은_비인증이면_401() throws Exception {
        mockMvc.perform(post(MENU_UPDATE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_SAVE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메뉴_생성은_관리자_권한이_아니면_403() throws Exception {
        mockMvc.perform(post(MENU_CREATE_PATH)
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_SAVE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void 메뉴_수정은_관리자_권한이_아니면_403() throws Exception {
        mockMvc.perform(post(MENU_UPDATE_PATH)
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_SAVE_BODY))
                .andExpect(status().isForbidden());
    }

    /*
     * 인가 통과 여부만 확인한다. 컨트롤러 진입 이후 결과(성공/검증 실패)는 MenuServiceTest 의 몫이라
     * 401/403 이 아니라는 것만 본다.
     */
    @Test
    void 메뉴_생성은_관리자_권한이면_인가를_통과() throws Exception {
        mockMvc.perform(post(MENU_CREATE_PATH)
                        .with(user("admin").authorities(() -> "ROLE_RECRUIT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_SAVE_BODY))
                .andExpect(status().is(allOf(not(401), not(403))));
    }
}
