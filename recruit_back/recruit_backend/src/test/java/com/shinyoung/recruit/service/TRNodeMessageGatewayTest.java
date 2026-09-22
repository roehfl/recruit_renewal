package com.shinyoung.recruit.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shinyoung.recruit.common.TRNodeEngine;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TRNodeMessageGatewayTest {

    private static final List<Map<String, Object>> SMS_IN_BLOCK1 = List.of(Map.of("KIND", "sms"));
    private static final MailMessage MAIL =
            new MailMessage("채용담당", "recruit@example.co.kr", "제목", "<p>본문</p>", "본문");
    private static final SmsMessage SMS = new SmsMessage("0200000000", "본문", SmsKind.SMS);

    private final TRNodeEngine trNodeEngine = mock(TRNodeEngine.class);
    /** 메일 주소 암호화·SMS InBlock1 은 폐쇄망에서 채운다. 여기서는 대역으로 나머지 전문·호출·응답 처리를 검증한다. */
    private final TRNodeMessageGateway gateway = new TRNodeMessageGateway(trNodeEngine, "http://node.example.test") {
        @Override
        String encryptEmail(String email) {
            return "ENC(" + email + ")";
        }

        @Override
        List<Map<String, Object>> smsInBlock1(SmsMessage message) {
            return SMS_IN_BLOCK1;
        }
    };

    private final Logger gatewayLogger = (Logger) LoggerFactory.getLogger(TRNodeMessageGateway.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeEach
    void attachLogAppender() {
        logAppender.start();
        gatewayLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        gatewayLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    private static String response(String cnfrYn, String uuid) {
        return "{\"body\":{\"OutBlock1\":[{\"CNFR_YN\":\"" + cnfrYn + "\",\"UUID_ID\":\"" + uuid + "\"}]}}";
    }

    private static Map<String, Object> recipientRow(String name, String contact) {
        return Map.of("CUST_ID", "recruit", "RCMS_CNRP_NAME", name, "RCMS_DATA", contact);
    }

    @Test
    void 메일은_메일_TR로_InBlock1_수신자_암호화_주소와_InBlock3_내용을_보내고_CNFR_YN이_y면_UUID_ID로_접수한다() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn(response("y", "UUID-1"));

        GatewayResult result = gateway.send(MAIL, List.of("kim@example.com", "lee@example.com"), List.of("김민준", "이서연"));

        assertThat(result).isEqualTo(GatewayResult.accepted("UUID-1"));
        Map<String, Object> content = new java.util.HashMap<>();
        content.put("MSG_APLY_CODE", "S");
        content.put("MSG_SHAP_CODE", "0");
        content.put("EMAIL_APLY_CODE", "2");
        content.put("EMAIL_CLS_CODE", "000");
        content.put("EMAIL_CLS4_CODE", "00");
        content.put("EMAIL_CLS5_CODE", "00");
        content.put("EMAIL_TMPL_CODE", "00000000");
        content.put("USER_ID", "recruit");
        content.put("UI_DEPT_CODE1", "180");
        content.put("BCDT_CLS_CODE", "W");
        content.put("TRNM_SLIP_NO", "WEB");
        content.put("USER_NAME", "채용담당");
        content.put("EMAIL_NAME", "recruit@example.co.kr");
        content.put("TITL_CNTT", "제목");
        content.put("SECU_USE_YN", "N");
        content.put("EMAIL_CNTT_DATA", "<p>본문</p>");
        verify(trNodeEngine).setNodeEngine("oseai_mail_001a", Map.of(
                "InBlock1", List.of(recipientRow("김민준", "ENC(kim@example.com)"), recipientRow("이서연", "ENC(lee@example.com)")),
                "InBlock3", List.of(content)));
    }

    @Test
    void SMS는_SMS_TR로_InBlock1_내용과_InBlock2_수신자를_보내고_CNFR_YN은_대소문자를_가리지_않고_UUID_ID_공백은_뺀다() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn(response("Y", "UUID-2   "));

        GatewayResult result = gateway.send(SMS, List.of("01000000000"), List.of("김민준"));

        assertThat(result).isEqualTo(GatewayResult.accepted("UUID-2"));
        verify(trNodeEngine).setNodeEngine("oseai_isms_001a", Map.of(
                "InBlock1", SMS_IN_BLOCK1,
                "InBlock2", List.of(recipientRow("김민준", "01000000000"))));
    }

    @Test
    void 이름이_없으면_수신자명은_빈_값이다() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn(response("y", "UUID-5"));

        gateway.send(SMS, List.of("01000000000"), Arrays.asList((String) null));

        verify(trNodeEngine).setNodeEngine("oseai_isms_001a", Map.of(
                "InBlock1", SMS_IN_BLOCK1,
                "InBlock2", List.of(recipientRow("", "01000000000"))));
    }

    @Test
    void CNFR_YN이_y가_아니면_GATEWAY_ERROR() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn(response("N", "UUID-3"));

        assertThat(gateway.send(MAIL, List.of("kim@example.com"), List.of("김민준")))
                .isEqualTo(GatewayResult.failure(MessageContacts.GATEWAY_ERROR));
    }

    @Test
    void UUID_ID가_비면_GATEWAY_ERROR() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn(response("y", " "));

        assertThat(gateway.send(MAIL, List.of("kim@example.com"), List.of("김민준")))
                .isEqualTo(GatewayResult.failure(MessageContacts.GATEWAY_ERROR));
    }

    @Test
    void 빈_응답이면_GATEWAY_ERROR() throws Exception {
        // TRNodeEngine 은 HTTP 200 이 아니거나 본문이 비면 빈 문자열을 돌려준다.
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn("");

        assertThat(gateway.send(MAIL, List.of("kim@example.com"), List.of("김민준")))
                .isEqualTo(GatewayResult.failure(MessageContacts.GATEWAY_ERROR));
    }

    @Test
    void JSON이_아닌_응답이면_GATEWAY_ERROR() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn("not-json");

        assertThat(gateway.send(MAIL, List.of("kim@example.com"), List.of("김민준")))
                .isEqualTo(GatewayResult.failure(MessageContacts.GATEWAY_ERROR));
    }

    @Test
    void 호출_예외면_GATEWAY_ERROR이고_로그에_예외_메시지와_연락처_원문을_남기지_않는다() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any()))
                .thenThrow(new IllegalStateException("kim@example.com 연결 실패"));

        GatewayResult result = gateway.send(MAIL, List.of("kim@example.com"), List.of("김민준"));

        assertThat(result).isEqualTo(GatewayResult.failure(MessageContacts.GATEWAY_ERROR));
        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("IllegalStateException", "k***@example.com");
            assertThat(event.getFormattedMessage()).doesNotContain("kim@example.com", "연결 실패", "김민준");
        });
    }

    @Test
    void 접수_로그에는_거래_ID와_마스킹한_번호만_남긴다() throws Exception {
        when(trNodeEngine.setNodeEngine(anyString(), any())).thenReturn(response("y", "UUID-4"));

        gateway.send(SMS, List.of("01012345678"), List.of("김민준"));

        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("UUID-4");
            assertThat(event.getFormattedMessage()).doesNotContain("01012345678", "본문", "김민준");
        });
    }

    @Test
    void node_url이_비면_기동을_거부한다() {
        assertThatThrownBy(() -> new TRNodeMessageGateway(trNodeEngine, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NODE_URL");
    }
}
