package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.domain.entity.Faq;
import com.shinyoung.recruit.domain.entity.FaqCategory;
import com.shinyoung.recruit.domain.repository.FaqCategoryRepository;
import com.shinyoung.recruit.domain.repository.FaqRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
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
class FaqControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FaqCategoryRepository faqCategoryRepository;

    @Autowired
    private FaqRepository faqRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /* ===================== 공개 조회 ===================== */

    @Test
    void 공개_조회는_활성_카테고리와_활성_FAQ만_정렬순으로_반환한다() throws Exception {
        FaqCategory second = saveCategory("채용 절차", 1, true);
        FaqCategory first = saveCategory("지원서 관련", 0, true);
        faqRepository.save(Faq.create(first, "질문 A2", "답변 A2", 1, true));
        faqRepository.save(Faq.create(first, "질문 A1", "답변 A1", 0, true));
        faqRepository.save(Faq.create(first, "숨긴 질문", "답변", 2, false));
        faqRepository.save(Faq.create(second, "질문 B1", "답변 B1", 0, true));

        mockMvc.perform(get("/api/faqs").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("지원서 관련"))
                .andExpect(jsonPath("$.data[0].faqs.length()").value(2))
                .andExpect(jsonPath("$.data[0].faqs[0].question").value("질문 A1"))
                .andExpect(jsonPath("$.data[0].faqs[1].question").value("질문 A2"))
                .andExpect(jsonPath("$.data[1].name").value("채용 절차"))
                .andExpect(jsonPath("$.data[1].faqs[0].answer").value("답변 B1"));
    }

    @Test
    void 공개_조회는_노출가능한_FAQ가_없는_카테고리를_제외한다() throws Exception {
        FaqCategory empty = saveCategory("빈 카테고리 " + uuid(), 0, true);
        FaqCategory hiddenCategory = saveCategory("비노출 카테고리 " + uuid(), 1, false);
        faqRepository.save(Faq.create(hiddenCategory, "비노출 카테고리 질문", "답변", 0, true));

        mockMvc.perform(get("/api/faqs").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + empty.getName() + "')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.name == '" + hiddenCategory.getName() + "')]").isEmpty());
    }

    /* ===================== 관리자: 카테고리 ===================== */

    @Test
    void 카테고리_생성은_sortOrder를_자동부여하고_이름중복을_거부한다() throws Exception {
        saveCategory("기존 카테고리 " + uuid(), 7, true);
        String name = "신규 카테고리 " + uuid();
        String body = """
                {"name":"%s","active":true}
                """.formatted(name);

        mockMvc.perform(jsonAdmin(post("/api/admin/faq-categories"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.sortOrder").value(8))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.faqCount").value(0));

        mockMvc.perform(jsonAdmin(post("/api/admin/faq-categories"), body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 카테고리_생성은_공백_이름을_거부한다() throws Exception {
        mockMvc.perform(jsonAdmin(post("/api/admin/faq-categories"), "{\"name\":\"   \",\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 카테고리_수정은_자기이름_유지를_허용하고_다른_카테고리_이름_충돌은_거부한다() throws Exception {
        FaqCategory target = saveCategory("대상 " + uuid(), 0, true);
        FaqCategory other = saveCategory("타 카테고리 " + uuid(), 1, true);

        mockMvc.perform(jsonAdmin(post("/api/admin/faq-categories/{id}", target.getId()),
                        """
                                {"name":"%s","active":false}
                                """.formatted(target.getName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(jsonAdmin(post("/api/admin/faq-categories/{id}", target.getId()),
                        """
                                {"name":"%s","active":true}
                                """.formatted(other.getName())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 카테고리_삭제는_soft_delete이며_하위_FAQ의_active를_바꾸지_않는다() throws Exception {
        FaqCategory category = saveCategory("삭제 대상 " + uuid(), 0, true);
        Faq faq = faqRepository.save(Faq.create(category, "질문", "답변", 0, true));

        mockMvc.perform(post("/api/admin/faq-categories/{id}/delete", category.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());

        assertThat(faqCategoryRepository.findById(category.getId())).isPresent();
        assertThat(faqCategoryRepository.findById(category.getId()).get().isActive()).isFalse();
        assertThat(faqRepository.findById(faq.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void 카테고리_정렬은_배열_순서대로_sortOrder를_정규화한다() throws Exception {
        // reorder 는 전체 대상을 요구하므로, 이 테스트 시작 시점의 전체 카테고리를 대상으로 삼는다.
        FaqCategory a = saveCategory("정렬 A " + uuid(), 0, true);
        FaqCategory b = saveCategory("정렬 B " + uuid(), 1, true);
        List<Long> reversed = faqCategoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(FaqCategory::getId)
                .sorted((left, right) -> Long.compare(right, left))
                .toList();

        mockMvc.perform(jsonAdmin(post("/api/admin/faq-categories/reorder"),
                        "{\"ids\":" + reversed + "}"))
                .andExpect(status().isOk());

        assertThat(faqCategoryRepository.findById(b.getId()).orElseThrow().getSortOrder())
                .isLessThan(faqCategoryRepository.findById(a.getId()).orElseThrow().getSortOrder());
    }

    @Test
    void 카테고리_정렬은_전체_대상과_다른_id집합을_거부한다() throws Exception {
        FaqCategory category = saveCategory("정렬 검증 " + uuid(), 0, true);
        saveCategory("정렬 검증2 " + uuid(), 1, true);

        mockMvc.perform(jsonAdmin(post("/api/admin/faq-categories/reorder"),
                        "{\"ids\":[" + category.getId() + "]}"))
                .andExpect(status().isBadRequest());
    }

    /* ===================== 관리자: FAQ ===================== */

    @Test
    void FAQ_생성은_카테고리내_sortOrder를_자동부여한다() throws Exception {
        FaqCategory category = saveCategory("FAQ 생성 " + uuid(), 0, true);
        faqRepository.save(Faq.create(category, "기존 질문", "답변", 3, true));

        mockMvc.perform(jsonAdmin(post("/api/admin/faqs"), """
                        {"categoryId":%d,"question":"새 질문","answer":"첫 줄\\n둘째 줄","active":true}
                        """.formatted(category.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").value("새 질문"))
                .andExpect(jsonPath("$.data.answer").value("첫 줄\n둘째 줄"))
                .andExpect(jsonPath("$.data.sortOrder").value(4))
                .andExpect(jsonPath("$.data.categoryId").value(category.getId()));
    }

    @Test
    void FAQ_생성은_공백_질문답변과_미존재_카테고리를_거부한다() throws Exception {
        FaqCategory category = saveCategory("검증 " + uuid(), 0, true);

        mockMvc.perform(jsonAdmin(post("/api/admin/faqs"), """
                        {"categoryId":%d,"question":"   ","answer":"답변"}
                        """.formatted(category.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(jsonAdmin(post("/api/admin/faqs"), """
                        {"categoryId":%d,"question":"질문","answer":"  "}
                        """.formatted(category.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(jsonAdmin(post("/api/admin/faqs"),
                        "{\"categoryId\":999999,\"question\":\"질문\",\"answer\":\"답변\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void FAQ_수정으로_카테고리를_옮기면_대상_카테고리_기준으로_sortOrder가_재부여된다() throws Exception {
        FaqCategory from = saveCategory("이동 전 " + uuid(), 0, true);
        FaqCategory to = saveCategory("이동 후 " + uuid(), 1, true);
        faqRepository.save(Faq.create(to, "대상 카테고리 기존 질문", "답변", 5, true));
        Faq moving = faqRepository.save(Faq.create(from, "이동할 질문", "답변", 0, true));

        mockMvc.perform(jsonAdmin(post("/api/admin/faqs/{id}", moving.getId()), """
                        {"categoryId":%d,"question":"이동한 질문","answer":"답변","active":true}
                        """.formatted(to.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(to.getId()))
                .andExpect(jsonPath("$.data.sortOrder").value(6));
    }

    @Test
    void FAQ_삭제는_soft_delete이며_관리자_조회에는_남고_공개_조회에서는_빠진다() throws Exception {
        FaqCategory category = saveCategory("FAQ 삭제 " + uuid(), 0, true);
        Faq faq = faqRepository.save(Faq.create(category, "삭제될 질문 " + uuid(), "답변", 0, true));

        mockMvc.perform(post("/api/admin/faqs/{id}/delete", faq.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/faqs").param("categoryId", String.valueOf(category.getId()))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].active").value(false));

        mockMvc.perform(get("/api/faqs").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + category.getName() + "')]").isEmpty());
    }

    @Test
    void FAQ_정렬은_해당_카테고리_전체_id집합만_허용한다() throws Exception {
        FaqCategory category = saveCategory("FAQ 정렬 " + uuid(), 0, true);
        FaqCategory otherCategory = saveCategory("타 카테고리 " + uuid(), 1, true);
        Faq first = faqRepository.save(Faq.create(category, "질문 1", "답변", 0, true));
        Faq second = faqRepository.save(Faq.create(category, "질문 2", "답변", 1, true));
        Faq outsider = faqRepository.save(Faq.create(otherCategory, "타 카테고리 질문", "답변", 0, true));

        mockMvc.perform(jsonAdmin(post("/api/admin/faqs/reorder"),
                        "{\"categoryId\":%d,\"ids\":[%d,%d]}".formatted(category.getId(), second.getId(), first.getId())))
                .andExpect(status().isOk());

        assertThat(faqRepository.findById(second.getId()).orElseThrow().getSortOrder()).isZero();
        assertThat(faqRepository.findById(first.getId()).orElseThrow().getSortOrder()).isEqualTo(1);

        // 타 카테고리 FAQ 를 섞으면 400
        mockMvc.perform(jsonAdmin(post("/api/admin/faqs/reorder"),
                        "{\"categoryId\":%d,\"ids\":[%d,%d,%d]}"
                                .formatted(category.getId(), first.getId(), second.getId(), outsider.getId())))
                .andExpect(status().isBadRequest());
    }

    /* ===================== 권한 ===================== */

    @Test
    void 관리자_API는_비인증을_차단한다() throws Exception {
        mockMvc.perform(get("/api/admin/faq-categories").with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/faq-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"권한 테스트\",\"active\":true}")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private FaqCategory saveCategory(String name, int sortOrder, boolean active) {
        return faqCategoryRepository.save(FaqCategory.create(name, sortOrder, active));
    }

    private MockHttpServletRequestBuilder jsonAdmin(MockHttpServletRequestBuilder builder, String body) {
        return builder
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(body)
                .with(authentication(adminAuthentication()));
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "faq-admin-" + UUID.randomUUID(),
                "Recruit",
                "Faq Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
