package com.shinyoung.recruit.enumeration;

/**
 * 수신자·채널별 결과(설계서 2·7.4절). PENDING 솔루션 호출 전, REQUESTED 솔루션 접수(결과 수신 중),
 * SENT 성공, FAILED 실패(접수 실패 또는 실패 결과코드), SKIPPED 연락처 없음·형식 오류·채널 끔으로 보내지 않음.
 */
public enum MessageDeliveryStatus {
    PENDING,
    REQUESTED,
    SENT,
    FAILED,
    SKIPPED
}
