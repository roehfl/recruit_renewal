package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.Notice;
import com.shinyoung.recruit.domain.repository.NoticeRepository;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class NoticeControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private NoticeRepository noticeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        noticeRepository.deleteAll();
    }

    /* ===================== 지원자 공개 조회 ===================== */

    @Test
    void 공개_목록은_삭제된_공지를_제외한다() throws Exception {
        saveNotice("살아있는 공지", "<p>본문</p>", false, false);
        saveNotice("삭제된 공지", "<p>본문</p>", false, true);

        mockMvc.perform(get("/api/board/notices").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("살아있는 공지"));
    }

    @Test
    void 공개_목록은_고정공지를_먼저_보여준다() throws Exception {
        saveNotice("일반 공지", "<p>본문</p>", false, false);
        saveNotice("고정 공지", "<p>본문</p>", true, false);

        mockMvc.perform(get("/api/board/notices").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("고정 공지"))
                .andExpect(jsonPath("$.data.content[0].pinned").value(true));
    }

    @Test
    void 공개_상세는_삭제된_공지를_404로_돌려준다() throws Exception {
        Notice deleted = saveNotice("삭제된 공지", "<p>본문</p>", false, true);

        mockMvc.perform(get("/api/board/notices/" + deleted.getId()).with(anonymous()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 없는_공지_상세는_404다() throws Exception {
        mockMvc.perform(get("/api/board/notices/999999").with(anonymous()))
                .andExpect(status().isNotFound());
    }

    /* ===================== 관리자 조회 ===================== */

    @Test
    void 관리자_목록은_삭제된_공지도_포함한다() throws Exception {
        saveNotice("살아있는 공지", "<p>본문</p>", false, false);
        saveNotice("삭제된 공지", "<p>본문</p>", false, true);

        mockMvc.perform(get("/api/admin/notices").with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void 관리자_목록은_삭제여부로_거를_수_있다() throws Exception {
        saveNotice("살아있는 공지", "<p>본문</p>", false, false);
        saveNotice("삭제된 공지", "<p>본문</p>", false, true);

        mockMvc.perform(get("/api/admin/notices").param("deleted", "true")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("삭제된 공지"))
                .andExpect(jsonPath("$.data.content[0].deleted").value(true));
    }

    @Test
    void 관리자_목록은_고정만_보기와_검색어를_함께_적용한다() throws Exception {
        saveNotice("면접 일정 안내", "<p>본문</p>", true, false);
        saveNotice("면접 장소 안내", "<p>본문</p>", false, false);
        saveNotice("서류 결과 안내", "<p>본문</p>", true, false);

        mockMvc.perform(get("/api/admin/notices")
                        .param("pinnedOnly", "true")
                        .param("searchType", "TITLE")
                        .param("keyword", "면접")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("면접 일정 안내"));
    }

    @Test
    void 관리자_상세는_삭제된_공지도_정제된_본문과_함께_돌려준다() throws Exception {
        Notice notice = saveNotice("삭제된 공지", "<p onclick=\"x()\">본문</p><script>alert(1)</script>", false, true);

        mockMvc.perform(get("/api/admin/notices/" + notice.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.contentHtml").value("<p>본문</p>"));
    }

    @Test
    void 관리자_목록은_비인증이면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/notices").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_목록은_지원자_권한이면_403이다() throws Exception {
        mockMvc.perform(get("/api/admin/notices").with(authentication(applicantAuthentication())))
                .andExpect(status().isForbidden());
    }

    /* ===================== 관리자 쓰기 ===================== */

    @Test
    void 공지를_등록한다() throws Exception {
        mockMvc.perform(post("/api/board/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 공지\",\"content\":\"<p>본문</p>\",\"isPinned\":true}")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber());

        Notice saved = noticeRepository.findAll().get(0);
        assertThat(saved.getTitle()).isEqualTo("새 공지");
        assertThat(saved.isPinned()).isTrue();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getContentText()).isEqualTo("본문");
    }

    @Test
    void 공지를_수정하면_검색용_텍스트도_다시_계산한다() throws Exception {
        Notice notice = saveNotice("옛 제목", "<p>옛 본문</p>", false, false);

        mockMvc.perform(post("/api/board/notices/" + notice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 제목\",\"content\":\"<p>새 본문</p>\",\"isPinned\":true}")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());

        Notice updated = noticeRepository.findById(notice.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("새 제목");
        assertThat(updated.isPinned()).isTrue();
        assertThat(updated.getContentText()).isEqualTo("새 본문");
    }

    @Test
    void 제목이_비면_400이다() throws Exception {
        mockMvc.perform(post("/api/board/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"content\":\"<p>본문</p>\",\"isPinned\":false}")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 공지를_삭제하고_복구한다() throws Exception {
        Notice notice = saveNotice("공지", "<p>본문</p>", false, false);

        mockMvc.perform(post("/api/board/notices/" + notice.getId() + "/delete")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());
        assertThat(noticeRepository.findById(notice.getId()).orElseThrow().isDeleted()).isTrue();

        mockMvc.perform(post("/api/board/notices/" + notice.getId() + "/restore")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());
        assertThat(noticeRepository.findById(notice.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void 공지를_고정하고_해제한다() throws Exception {
        Notice notice = saveNotice("공지", "<p>본문</p>", false, false);

        mockMvc.perform(post("/api/board/notices/" + notice.getId() + "/pin")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());
        assertThat(noticeRepository.findById(notice.getId()).orElseThrow().isPinned()).isTrue();

        mockMvc.perform(post("/api/board/notices/" + notice.getId() + "/unpin")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());
        assertThat(noticeRepository.findById(notice.getId()).orElseThrow().isPinned()).isFalse();
    }

    @Test
    void 고정_변경은_본문을_건드리지_않는다() throws Exception {
        Notice notice = saveNotice("공지", "<p>본문</p>", false, false);

        mockMvc.perform(post("/api/board/notices/" + notice.getId() + "/pin")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());

        Notice pinned = noticeRepository.findById(notice.getId()).orElseThrow();
        assertThat(pinned.getContentHtml()).isEqualTo("<p>본문</p>");
        assertThat(pinned.getContentText()).isEqualTo("본문");
    }

    @Test
    void 이미_삭제된_공지를_다시_삭제해도_200이다() throws Exception {
        Notice notice = saveNotice("공지", "<p>본문</p>", false, true);

        mockMvc.perform(post("/api/board/notices/" + notice.getId() + "/delete")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());

        assertThat(noticeRepository.findById(notice.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void 없는_공지를_수정하거나_삭제하면_404다() throws Exception {
        mockMvc.perform(post("/api/board/notices/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"<p>본문</p>\",\"isPinned\":false}")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/board/notices/999999/delete")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isNotFound());
    }

    @Test
    void 공지_수정은_비인증이면_401이다() throws Exception {
        Notice notice = saveNotice("공지", "<p>본문</p>", false, false);

        mockMvc.perform(post("/api/board/notices/" + notice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"<p>본문</p>\",\"isPinned\":false}")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 공지_삭제는_지원자_권한이면_403이다() throws Exception {
        Notice notice = saveNotice("공지", "<p>본문</p>", false, false);

        mockMvc.perform(post("/api/board/notices/" + notice.getId() + "/delete")
                        .with(authentication(applicantAuthentication())))
                .andExpect(status().isForbidden());
    }

    /* ===================== helper ===================== */

    private Notice saveNotice(String title, String contentHtml, boolean pinned, boolean deleted) {
        Notice notice = Notice.create(title, contentHtml, pinned);
        if (deleted) {
            notice.delete();
        }
        return noticeRepository.saveAndFlush(notice);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "notice-admin-" + UUID.randomUUID(),
                "Recruit",
                "Notice Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicantAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "notice-applicant-" + UUID.randomUUID(),
                "Applicant",
                "Notice Applicant",
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
