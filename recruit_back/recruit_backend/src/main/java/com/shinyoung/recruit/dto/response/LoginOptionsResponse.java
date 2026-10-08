package com.shinyoung.recruit.dto.response;

/** 로그인 화면이 팝업을 띄울지 알기 위한 공개 설정. 비밀값이 아니다. */
public record LoginOptionsResponse(boolean twoFactorEnabled) {
}
