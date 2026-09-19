package com.shinyoung.recruit.enumeration;

/** SMS 구분. 치환 후 90byte 이하 SMS, 2000byte 이하 LMS. 수신자별로 판정한다. */
public enum SmsKind {
    SMS,
    LMS
}
