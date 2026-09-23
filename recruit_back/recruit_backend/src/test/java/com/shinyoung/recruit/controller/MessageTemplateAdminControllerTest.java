package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.service.MessageTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageTemplateAdminControllerTest {

    private static final String SAVE_JSON = """
            {
              "type": "RESULT_ANNOUNCEMENT",
              "name": "중립 안내",
              "defaultTemplate": true,
              "mailSubject": "[신영증권] #{공고명} #{전형명} 결과 안내",
              "mailBody": "#{이름}님, 안녕하세요.",
              "smsBody": "[신영증권] #{이름}님, #{전형명} 결과가 발표되었습니다."
            }
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MessageTemplateService messageTemplateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 템플릿을_등록한다() throws Exception {
        mockMvc.perform(post("/api/admin/message-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.defaultTemplate").value(true));
    }

    @Test
    void 허용되지_않은_변수는_400() throws Exception {
        mockMvc.perform(post("/api/admin/message-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FREE","name":"잘못된 변수","defaultTemplate":false,"smsBody":"#{면접일시} 안내"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("사용할 수 없는 변수: #{면접일시}"));
    }

    @Test
    void 이름이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/admin/message-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FREE","name":"","defaultTemplate":false,"smsBody":"안내"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 종류로_목록을_거른다() throws Exception {
        createTemplate(MessageType.RESULT_ANNOUNCEMENT, "결과 안내");
        createTemplate(MessageType.FREE, "설명회 초대");

        mockMvc.perform(get("/api/admin/message-templates").param("type", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("설명회 초대"));
    }

    @Test
    void 템플릿을_수정한다() throws Exception {
        MessageTemplateResponse saved = createTemplate(MessageType.FREE, "설명회 초대");

        mockMvc.perform(post("/api/admin/message-templates/{id}", saved.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("중립 안내"))
                .andExpect(jsonPath("$.data.type").value("RESULT_ANNOUNCEMENT"));
    }

    @Test
    void 삭제한_템플릿_조회는_404() throws Exception {
        MessageTemplateResponse saved = createTemplate(MessageType.FREE, "삭제 대상");

        mockMvc.perform(post("/api/admin/message-templates/{id}/delete", saved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/message-templates/{id}", saved.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("메시지 템플릿을 찾을 수 없습니다."));
    }

    @Test
    void 변수_카탈로그를_조회한다() throws Exception {
        mockMvc.perform(get("/api/admin/messages/variables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(14))
                .andExpect(jsonPath("$.data[0].key").value("이름"))
                .andExpect(jsonPath("$.data[0].label").value("지원자 이름"));
    }

    private MessageTemplateResponse createTemplate(MessageType type, String name) {
        return messageTemplateService.createTemplate(
                new MessageTemplateSaveRequest(type, name, false, null, null, "[신영증권] #{이름}님 안내"));
    }
}
