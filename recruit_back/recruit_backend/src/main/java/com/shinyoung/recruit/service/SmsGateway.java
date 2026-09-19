package com.shinyoung.recruit.service;

import java.util.List;

/** 문자 발송 연동. 호출 1회 = 내용 1개 + 수신자 1~10명. 번호는 숫자만 넘긴다. */
public interface SmsGateway {

    GatewayResult send(SmsMessage message, List<String> toNumbers);
}
