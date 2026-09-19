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

    /*
     * 공지 등록(/api/board/notices)도 BoardController base path 가 /board 라 broad /api/admin/** 에 걸리지 않는다.
     * 전용 매처가 없으면 anyRequest().permitAll() 로 흘러 비인증 사용자가 공지(HTML)를 등록할 수 있다.
     */
    private static final String NOTICE_PATH = "/api/board/notices";
    private static final String NOTICE_SAVE_BODY = """
            {"title":"공지","content":"<p>본문</p>","isPinned":false}
            """;

    @Test
    void 공지_목록_조회는_인증없이_허용() throws Exception {
        mockMvc.perform(get(NOTICE_PATH))
                .andExpect(status().isOk());
    }

    @Test
    void 공지_등록은_비인증이면_401() throws Exception {
        mockMvc.perform(post(NOTICE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTICE_SAVE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 공지_등록은_지원자_권한이면_403() throws Exception {
        mockMvc.perform(post(NOTICE_PATH)
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTICE_SAVE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void 공지_등록은_관리자_권한이면_인가를_통과() throws Exception {
        mockMvc.perform(post(NOTICE_PATH)
                        .with(user("admin").authorities(() -> "ROLE_RECRUIT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTICE_SAVE_BODY))
                .andExpect(status().is(allOf(not(401), not(403))));
    }

    /*
     * 권한 관리 API(/api/admin/role-mappings/**)는 전용 매처 없이 broad /api/admin/**
     * (ROLE_ADMIN, ROLE_RECRUIT_ADMIN) 매처에 걸리는 것이 계약이다. 매처 순서가 바뀌거나
     * 경로가 broad 매처 밖으로 이동하면 아래 테스트가 깨진다.
     */
    private static final String ROLE_MAPPING_DEPT_PATH = "/api/admin/role-mappings/dept";
    private static final String ROLE_MAPPING_DEPT_BODY = """
            {"deptName":"내부채널","roleName":"ROLE_RECRUIT_ADMIN"}
            """;

    @Test
    void 권한관리_조회는_비인증이면_401() throws Exception {
        mockMvc.perform(get(ROLE_MAPPING_DEPT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 권한관리_생성은_비인증이면_401() throws Exception {
        mockMvc.perform(post(ROLE_MAPPING_DEPT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROLE_MAPPING_DEPT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 권한관리는_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get(ROLE_MAPPING_DEPT_PATH)
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 권한관리는_면접관_권한으로도_접근할_수_없다() throws Exception {
        mockMvc.perform(get(ROLE_MAPPING_DEPT_PATH)
                        .with(user("interviewer").authorities(() -> "ROLE_INTERVIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 권한관리_조회는_IT관리자_권한이면_인가를_통과() throws Exception {
        mockMvc.perform(get(ROLE_MAPPING_DEPT_PATH)
                        .with(user("admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().is(allOf(not(401), not(403))));
    }

    @Test
    void 권한관리_생성은_운영관리자_권한이면_인가를_통과() throws Exception {
        mockMvc.perform(post(ROLE_MAPPING_DEPT_PATH)
                        .with(user("recruitAdmin").authorities(() -> "ROLE_RECRUIT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROLE_MAPPING_DEPT_BODY))
                .andExpect(status().is(allOf(not(401), not(403))));
    }

    @Test
    void 메시지_템플릿_조회는_비인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/message-templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_템플릿은_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/message-templates")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 메시지_변수_조회는_운영관리자_권한이면_인가를_통과() throws Exception {
        mockMvc.perform(get("/api/admin/messages/variables")
                        .with(user("recruitAdmin").authorities(() -> "ROLE_RECRUIT_ADMIN")))
                .andExpect(status().is(allOf(not(401), not(403))));
    }

    @Test
    void 메시지_대상자_조회는_비인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/messages/targets").param("type", "FREE").param("jobPostingId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_대상자_조회는_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/messages/targets").param("type", "FREE").param("jobPostingId", "1")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 메시지_발송은_비인증이면_401() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_발송은_지원자_권한이면_403() throws Exception {
        mockMvc.perform(post("/api/admin/messages/send")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 메시지_발송_이력은_비인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_발송_이력은_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 메시지_발송_이력_상세는_비인증이면_401() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지_발송_이력_상세는_지원자_권한이면_403() throws Exception {
        mockMvc.perform(get("/api/admin/messages/history/1")
                        .with(user("applicant").authorities(() -> "ROLE_APPLICANT")))
                .andExpect(status().isForbidden());
    }
}
