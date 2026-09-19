package com.shinyoung.recruit.service;

import java.util.List;

/** 발송 요청 커밋 후 디스패처가 받는 이벤트. 치환 결과는 메모리로만 넘기고 DB 에 저장하지 않는다. */
public record MessageSendRequestedEvent(Long messageSendId, List<DeliveryItem> items) {
}
