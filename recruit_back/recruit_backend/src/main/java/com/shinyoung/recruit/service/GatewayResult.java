package com.shinyoung.recruit.service;

/**
 * 게이트웨이 1회 호출의 접수 결과(설계서 7.3). 접수되면 accepted = true 와 솔루션 거래 ID(필수, 100자 이내).
 * 최종 발송 결과는 나중에 거래 ID 로 온다(DeliveryReport). 접수 실패면 failureReason 에 사유 코드(200자 이내 권장).
 * 사유는 평문으로 저장되고 테스트 발송 응답에도 나가므로 주소·번호를 넣지 않는다. 비우면 GATEWAY_ERROR 로 기록한다.
 */
public record GatewayResult(boolean accepted, String transactionId, String failureReason) {

    public static GatewayResult accepted(String transactionId) {
        return new GatewayResult(true, transactionId, null);
    }

    public static GatewayResult failure(String failureReason) {
        return new GatewayResult(false, null, failureReason);
    }
}
