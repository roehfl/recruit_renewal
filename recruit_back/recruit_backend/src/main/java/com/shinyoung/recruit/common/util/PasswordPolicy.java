package com.shinyoung.recruit.common.util;

/**
 * 지원자 새 비밀번호 조합 규칙(가입·재설정·변경 공통). KISA 권고 기준을 따른다.
 *
 * <p>영문 대문자·영문 소문자·숫자·특수문자 네 종류 중 2종류 이상이면 10자 이상, 3종류 이상이면 8자 이상.
 * 공백은 종류로 세지 않고, 영문·숫자·공백이 아닌 글자(한글 포함)는 특수문자로 센다.
 * 길이 상·하한(8~100자)과 BCrypt 72바이트 상한은 요청 DTO가 따로 검사한다. 프론트({@code common/passwordPolicy.ts})와 같은 규칙이다.
 */
public final class PasswordPolicy {

    public static final String MESSAGE =
            "비밀번호는 영문 대문자·소문자·숫자·특수문자 중 2종류 이상이면 10자 이상, 3종류 이상이면 8자 이상이어야 합니다.";

    private PasswordPolicy() {
    }

    /** null은 통과시킨다(필수 여부는 {@code @NotBlank}가 본다). */
    public static boolean isAcceptable(String password) {
        if (password == null) {
            return true;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else if (!Character.isWhitespace(c)) {
                special = true;
            }
        }
        int kinds = (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
        int length = password.codePointCount(0, password.length());
        return (kinds >= 3 && length >= 8) || (kinds >= 2 && length >= 10);
    }
}
