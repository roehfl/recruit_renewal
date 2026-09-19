package com.shinyoung.recruit.service;

import java.util.regex.Pattern;

/**
 * 메시지 수신 연락처 규칙(설계서 4절). 대상 조회·발송·로그 마스킹이 같은 규칙을 쓴다.
 * 휴대폰은 숫자만 남겨 01로 시작하는 10~11자리, 이메일은 앞뒤 공백을 뺀 x@y.z 형식이어야 유효하다.
 */
public final class MessageContacts {

    /** 연락처 값이 없음. */
    public static final String NO_CONTACT = "NO_CONTACT";
    /** 연락처가 있지만 형식이 맞지 않음. */
    public static final String INVALID_CONTACT = "INVALID_CONTACT";
    /** 이번 발송에서 끈 채널. */
    public static final String CHANNEL_OFF = "CHANNEL_OFF";
    /** 게이트웨이 호출 중 예외. */
    public static final String GATEWAY_ERROR = "GATEWAY_ERROR";

    private static final Pattern MOBILE_PHONE = Pattern.compile("01\\d{8,9}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private MessageContacts() {
    }

    public static String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("\\D", "");
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && MOBILE_PHONE.matcher(normalizePhone(phone)).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL.matcher(normalizeEmail(email)).matches();
    }

    /** 채널을 쓸 수 없을 때의 제외 사유. 값이 비었으면 NO_CONTACT, 있으면 INVALID_CONTACT. */
    public static String skipReason(String value) {
        return value == null || value.isBlank() ? NO_CONTACT : INVALID_CONTACT;
    }

    /** 로그용. 첫 글자와 도메인만 남긴다. */
    public static String maskEmail(String email) {
        String normalized = normalizeEmail(email);
        int at = normalized == null ? -1 : normalized.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return normalized.charAt(0) + "***" + normalized.substring(at);
    }

    /** 로그용. 앞 3자리와 뒤 4자리만 남긴다. */
    public static String maskPhone(String phone) {
        String digits = normalizePhone(phone);
        if (digits == null || digits.length() < 10) {
            return "***";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}
