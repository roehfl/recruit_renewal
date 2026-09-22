package com.shinyoung.recruit.enumeration;

/**
 * 본인확인을 요구한 흐름. 요청 발급 시 기록하고 결과 소비 시 대조한다.
 *
 * <p>용도를 대조하지 않으면 한 흐름용으로 받은 인증 결과를 다른 흐름에 그대로 밀어 넣을 수 있다
 * (가입용 인증으로 아이디 찾기, 아이디 찾기용 인증으로 가입).
 */
public enum NiceVerificationPurpose {
    SIGNUP,
    FIND_EMAIL
}
