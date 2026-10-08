package com.shinyoung.recruit.enumeration;

public enum Gender {
    MALE,
    FEMALE;

    /**
     * NICE 본인확인 {@code GENDER} 코드 → 성별. NICE 체크플러스 규격 {@code 1}=남성, {@code 0}=여성.
     * 그 밖의 값·빈 값은 null 이다(가입을 막지 않는다).
     */
    public static Gender fromNiceCode(String code) {
        if (code == null) {
            return null;
        }
        return switch (code.trim()) {
            case "1" -> MALE;
            case "0" -> FEMALE;
            default -> null;
        };
    }
}
