package com.shinyoung.recruit.service;

/**
 * 발송 솔루션이 메시지큐로 나중에 보내는 거래 1건의 결과(설계서 7.4). 그 거래의 수신자 전원에 같이 적용한다.
 * 실제 소켓 클라이언트는 받은 메시지를 이 값으로 바꿔 DeliveryReportHandler.handle 만 부르면 된다.
 */
public record DeliveryReport(String transactionId, String resultCode) {
}
