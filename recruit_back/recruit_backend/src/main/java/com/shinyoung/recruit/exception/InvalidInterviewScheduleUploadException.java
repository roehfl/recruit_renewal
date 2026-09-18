package com.shinyoung.recruit.exception;

/**
 * 면접 스케줄 Excel 업로드를 파일 단위로 처리할 수 없을 때 발생(확장자/크기/헤더/행 수/판독 불가, 업로드할 수 없는 단계 상태 등).
 * 행 단위 검증 결과는 응답 body로 반환하고, 파일 자체를 받을 수 없는 경우만 이 예외로 400을 던진다.
 */
public class InvalidInterviewScheduleUploadException extends RuntimeException {

    public InvalidInterviewScheduleUploadException(String message) {
        super(message);
    }
}
