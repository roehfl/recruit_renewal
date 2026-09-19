package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.SmsKind;

/** 문자 1건의 내용. kind 는 치환 후 byte 로 판정한 SMS/LMS. */
public record SmsMessage(String callbackNumber, String body, SmsKind kind) {
}
