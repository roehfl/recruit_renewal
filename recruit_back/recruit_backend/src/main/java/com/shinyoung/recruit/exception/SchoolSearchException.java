package com.shinyoung.recruit.exception;

/**
 * 외부 학교 검색 OpenAPI(NEIS / 대학 표준데이터) 호출 실패(인증키·baseUrl 미설정, 네트워크/타임아웃,
 * 상위 API 오류코드, 파싱 실패 등). 502(Bad Gateway)로 매핑된다.
 * 내부 상세(인증키/오류코드)는 로깅만 하고 클라이언트에는 일반 메시지를 노출한다.
 */
public class SchoolSearchException extends RuntimeException {

    public SchoolSearchException(String message) {
        super(message);
    }

    public SchoolSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
