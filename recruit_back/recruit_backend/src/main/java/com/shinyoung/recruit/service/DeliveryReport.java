package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;

/**
 * 발송 솔루션이 나중에 보내는 수신자 1명의 발송 결과(설계서 7.4). 거래 ID 가 같은 수신자 중 연락처(to)가 같은 한 명에게만 적용한다.
 * to 는 솔루션이 돌려준 수신 이메일 또는 번호다. 결과 수신부(UmsReportServer, 목업은 MockDeliveryReportScheduler)가
 * 이 값으로 바꿔 DeliveryReportHandler.handle 만 부른다.
 */
public record DeliveryReport(MessageChannel channel, String transactionId, String to, String resultCode) {
}
