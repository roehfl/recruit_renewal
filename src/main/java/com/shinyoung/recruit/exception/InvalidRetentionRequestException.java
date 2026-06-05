package com.shinyoung.recruit.exception;

/**
 * Retention write/dry-run 의 서비스 레벨 공통 방어 위반(actor 누락, 조회 가드 등) — 400.
 * 관리자 행위 감사가 ANONYMOUS 로 기록되는 것을 막는다(9c 리뷰 Medium 2).
 */
public class InvalidRetentionRequestException extends RuntimeException {
    public InvalidRetentionRequestException(String message) {
        super(message);
    }
}
