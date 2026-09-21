package com.shinyoung.recruit.enumeration;

/**
 * 본인확인을 요구한 흐름. 요청 발급 시 기록하고 결과 소비 시 대조한다.
 *
 * <p>용도를 대조하지 않으면 가입용으로 받은 인증 결과를 다른 흐름(이메일 찾기 등)에
 * 그대로 밀어 넣을 수 있다. 값이 하나뿐이어도 대조 지점을 먼저 만들어 둔다.
 */
public enum NiceVerificationPurpose {
    SIGNUP
}
