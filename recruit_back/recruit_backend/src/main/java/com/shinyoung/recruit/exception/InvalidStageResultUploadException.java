package com.shinyoung.recruit.exception;

/**
 * Excel upload(StageResult) 파일 레벨 검증 실패 시 발생(잘못된 확장자/크기 초과/헤더 불일치/행수 초과/판독 불가 등).
 * 행 단위 검증 결과는 응답 body로 반환하고, 파일 자체를 처리할 수 없는 경우만 이 예외로 400을 던진다.
 */
public class InvalidStageResultUploadException extends RuntimeException {

    public InvalidStageResultUploadException(String message) {
        super(message);
    }
}
