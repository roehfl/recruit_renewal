package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 메시지(메일·SMS) 발신 정보, 본문 변수 #{채용사이트} 값, 발송 연동·결과 수신 설정.
 * 실제 값은 운영 환경변수로 주입하고 코드에는 예시 값만 둔다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.message")
public class MessageProperties {

    /** 메일 보낸사람 표시 이름. */
    @NotBlank
    private String senderName = "신영증권 채용담당";

    /** 메일 보낸사람 주소. */
    @NotBlank
    private String senderEmail = "recruit@example.co.kr";

    /** SMS 발신번호(표시·발송 공용). */
    @NotBlank
    private String smsCallbackNumber = "02-0000-0000";

    /** 채용 사이트 주소. 변수 #{채용사이트}에 들어간다. */
    @NotBlank
    private String siteUrl = "https://recruit.example.co.kr";

    /** 한 번 발송의 최대 수신자 수. */
    @Min(1)
    private int maxRecipients = 3000;

    /** 발송 연동 구현 선택. 기본 logging(로그만 남기는 목업). */
    @NotBlank
    private String gateway = "logging";

    /** 발송 요청 후 이 시간(분)이 지나도 완료가 아니면 이력에 지연(결과 미수신·발송 중단)으로 표시한다. */
    @Min(1)
    private int resultWaitMinutes = 60;

    /** 성공으로 볼 발송 결과코드 목록. 솔루션 스펙 확정 전 기본 0000(설계서 17절 6번). */
    @NotEmpty
    private List<String> successResultCodes = new ArrayList<>(List.of("0000"));

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getSmsCallbackNumber() {
        return smsCallbackNumber;
    }

    public void setSmsCallbackNumber(String smsCallbackNumber) {
        this.smsCallbackNumber = smsCallbackNumber;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public int getMaxRecipients() {
        return maxRecipients;
    }

    public void setMaxRecipients(int maxRecipients) {
        this.maxRecipients = maxRecipients;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public int getResultWaitMinutes() {
        return resultWaitMinutes;
    }

    public void setResultWaitMinutes(int resultWaitMinutes) {
        this.resultWaitMinutes = resultWaitMinutes;
    }

    public List<String> getSuccessResultCodes() {
        return successResultCodes;
    }

    public void setSuccessResultCodes(List<String> successResultCodes) {
        this.successResultCodes = successResultCodes;
    }
}
