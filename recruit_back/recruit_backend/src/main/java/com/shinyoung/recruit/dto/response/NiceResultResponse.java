package com.shinyoung.recruit.dto.response;

/**
 * 팝업 결과 화면에 내려가는 값.
 *
 * <p><b>ci 를 담지 않는다.</b> CI 는 서버 세션에만 두고 브라우저로 내리지 않는다 —
 * 이것이 클라이언트가 보낸 ci 를 믿던 기존 구조와의 핵심 차이다.
 */
public record NiceResultResponse(String status, String name, String phoneNumber) {

    public static NiceResultResponse success(String name, String phoneNumber) {
        return new NiceResultResponse("SUCCESS", name, phoneNumber);
    }
}
