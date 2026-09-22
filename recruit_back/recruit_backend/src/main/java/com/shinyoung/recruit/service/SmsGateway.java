package com.shinyoung.recruit.service;

import java.util.List;

/**
 * 문자 발송 연동. 호출 1회 = 내용 1개 + 수신자 1~10명. 번호는 숫자만 넘긴다.
 * names 는 toNumbers 와 같은 순서의 수신자 이름이다(null 일 수 있다). 이름은 로그에 남기지 않는다.
 */
public interface SmsGateway {

    GatewayResult send(SmsMessage message, List<String> toNumbers, List<String> names);
}
