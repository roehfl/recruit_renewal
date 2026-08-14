package com.shinyoung.recruit.exception;

/**
 * 통계 조회 요청이 올바르지 않을 때 발생(예: 지원하지 않는 dimension 값). 400으로 매핑된다.
 */
public class InvalidStatisticsRequestException extends RuntimeException {

    public InvalidStatisticsRequestException(String message) {
        super(message);
    }
}
