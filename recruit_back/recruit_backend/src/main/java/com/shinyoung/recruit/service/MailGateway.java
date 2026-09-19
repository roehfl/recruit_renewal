package com.shinyoung.recruit.service;

import java.util.List;

/**
 * 메일 발송 연동. 호출 1회 = 내용 1개 + 수신자 1~10명(발송 솔루션 트랜잭션 제약).
 * 수신자 묶기는 호출하는 쪽(DeliveryUnit)이 책임진다. 실제 SMTP 구현은 사내 스펙 확정 후 추가한다.
 * 구현체는 수신자마다 따로 보내야 한다(수신자별 To 또는 BCC). 지원자끼리 서로의 주소가 보이면 안 된다.
 */
public interface MailGateway {

    GatewayResult send(MailMessage message, List<String> toAddresses);
}
