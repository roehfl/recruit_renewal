package com.shinyoung.recruit.exception;

/**
 * 본인확인 검증 실패. 복호화 실패, 요청번호 불일치·만료·재사용, 세션 불일치를 모두 포함한다.
 *
 * <p>사용자에게는 원인을 구분해 보여주지 않는다. 어떤 값이 왜 틀렸는지 알려주면
 * 재전송을 시도하는 쪽에 정보를 주게 된다. 예외는 가입 제출 시점의 {@code requireFresh} 안내뿐이다
 * (자기 세션에 대한 정보라 공격 가치가 없다).
 */
public class NiceVerificationException extends RuntimeException {

    public NiceVerificationException(String message) {
        super(message);
    }

    public NiceVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
