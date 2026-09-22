package com.shinyoung.recruit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.shinyoung.recruit.common.TRNodeEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사내 TR 노드(TRNodeEngine)로 메일·SMS 발송을 접수한다(recruit.message.gateway=trnode). 채널마다 TR·전문 형식이 다르다.
 * 메일: TR oseai_mail_001a, InBlock1 = 수신자(주소는 암호화), InBlock3 = 발송 내용.
 * SMS: TR oseai_isms_001a, InBlock1 = 발송 내용(SMS/LMS 코드값은 SmsMessage.kind, 90byte 기준), InBlock2 = 수신자.
 * 호출 1회의 수신자는 내용이 같으면 최대 10명, 사람마다 내용이 다르면 1명이다(DeliveryUnit). 레거시처럼 고정길이로 채우지 않는다.
 * 응답 body.OutBlock1 에서 CNFR_YN 이 y 인 행의 UUID_ID 가 거래 ID 다. 최종 발송 결과 수신(DeliveryReport)은 아직 없다.
 * 실패는 모두 GATEWAY_ERROR 로 돌려준다. 로그에는 거래 ID·마스킹한 수신자만 남긴다(본문·이름·응답 원문·예외 메시지 금지).
 */
@Component
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "trnode")
public class TRNodeMessageGateway implements MailGateway, SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(TRNodeMessageGateway.class);

    static final String MAIL_TR_NAME = "oseai_mail_001a";
    static final String SMS_TR_NAME = "oseai_isms_001a";
    /** 수신자 행 CUST_ID. 레거시 채용 발송이 쓰던 고정값. */
    static final String CUST_ID = "recruit";

    private final TRNodeEngine trNodeEngine;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TRNodeMessageGateway(TRNodeEngine trNodeEngine, @Value("${node.url}") String nodeUrl) {
        if (nodeUrl.isBlank()) {
            throw new IllegalStateException("recruit.message.gateway=trnode 이면 node.url(NODE_URL)이 필요합니다.");
        }
        this.trNodeEngine = trNodeEngine;
    }

    @Override
    public GatewayResult send(MailMessage message, List<String> toAddresses, List<String> names) {
        return request("message-mail", MAIL_TR_NAME, mailBody(message, toAddresses, names),
                toAddresses.stream().map(MessageContacts::maskEmail).toList());
    }

    @Override
    public GatewayResult send(SmsMessage message, List<String> toNumbers, List<String> names) {
        return request("message-sms", SMS_TR_NAME, smsBody(message, toNumbers, names),
                toNumbers.stream().map(MessageContacts::maskPhone).toList());
    }

    /** 메일 전문. InBlock1 = 수신자(RCMS_DATA 는 암호화한 주소), InBlock3 = 레거시 고정 코드값 + 보낸사람·제목·HTML 본문. */
    Map<String, Object> mailBody(MailMessage message, List<String> toAddresses, List<String> names) {
        List<Map<String, Object>> inBlock1 = new ArrayList<>(toAddresses.size());
        for (int index = 0; index < toAddresses.size(); index++) {
            inBlock1.add(recipientRow(names.get(index), encryptEmail(toAddresses.get(index))));
        }
        Map<String, Object> content = new HashMap<>();
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
        content.put("USER_NAME", message.fromName());
        content.put("EMAIL_NAME", message.fromAddress());
        content.put("TITL_CNTT", message.subject());
        content.put("SECU_USE_YN", "N");
        content.put("EMAIL_CNTT_DATA", message.html());
        return Map.of("InBlock1", inBlock1, "InBlock3", List.of(content));
    }

    /** SMS 전문. InBlock1 = 발송 내용, InBlock2 = 수신자(RCMS_DATA 는 숫자만 남긴 번호). */
    Map<String, Object> smsBody(SmsMessage message, List<String> toNumbers, List<String> names) {
        List<Map<String, Object>> inBlock2 = new ArrayList<>(toNumbers.size());
        for (int index = 0; index < toNumbers.size(); index++) {
            inBlock2.add(recipientRow(names.get(index), toNumbers.get(index)));
        }
        return Map.of("InBlock1", smsInBlock1(message), "InBlock2", inBlock2);
    }

    /**
     * TODO(폐쇄망): 메일 수신자 RCMS_DATA. 이메일 주소를 사내 암호화 라이브러리로 암호화한 값.
     * 채우기 전에는 예외를 던져 발송이 GATEWAY_ERROR 로 기록된다.
     */
    String encryptEmail(String email) {
        throw new UnsupportedOperationException("메일 주소 암호화 미작성");
    }

    /**
     * TODO(폐쇄망): SMS InBlock1(발송 내용).
     * 발신번호 callbackNumber, 본문 body. 길이별 코드값은 kind 로 정한다(SMS = 90byte 이하, LMS = 초과. MessageRenderer 기준).
     */
    List<Map<String, Object>> smsInBlock1(SmsMessage message) {
        throw new UnsupportedOperationException("SMS InBlock1 미작성");
    }

    /** 수신자 행 1개. 이름이 null 이면 빈 값. */
    private static Map<String, Object> recipientRow(String name, String rcmsData) {
        return Map.of("CUST_ID", CUST_ID, "RCMS_CNRP_NAME", name == null ? "" : name, "RCMS_DATA", rcmsData);
    }

    /** TR 1회 호출. 로그의 elapsedMs 는 TR 호출·응답 해석에 걸린 시간이다(대량 발송 소요 시간 추정용). */
    private GatewayResult request(String channel, String trName, Map<String, Object> body, List<String> maskedTo) {
        long startedAt = System.nanoTime();
        String transactionId;
        try {
            transactionId = transactionIdOf(trNodeEngine.setNodeEngine(trName, body));
        } catch (Exception e) {
            log.warn("[{}] TR 호출 실패: to={} error={} elapsedMs={}",
                    channel, maskedTo, e.getClass().getSimpleName(), elapsedMs(startedAt));
            return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
        }
        if (transactionId == null) {
            log.warn("[{}] TR 미접수(CNFR_YN 이 y 가 아니거나 UUID_ID·응답 없음): to={} elapsedMs={}",
                    channel, maskedTo, elapsedMs(startedAt));
            return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
        }
        log.info("[{}] transactionId={} to={} elapsedMs={}", channel, transactionId, maskedTo, elapsedMs(startedAt));
        return GatewayResult.accepted(transactionId);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /** body.OutBlock1 에서 CNFR_YN 이 y(대소문자 무시)이고 UUID_ID 가 있는 첫 행의 UUID_ID(앞뒤 공백 제거). 없으면 null. */
    private String transactionIdOf(String response) throws JsonProcessingException {
        for (JsonNode row : objectMapper.readTree(response).path("body").path("OutBlock1")) {
            String uuid = row.path("UUID_ID").asText("").trim();
            if ("y".equalsIgnoreCase(row.path("CNFR_YN").asText("").trim()) && !uuid.isEmpty()) {
                return uuid;
            }
        }
        return null;
    }
}
