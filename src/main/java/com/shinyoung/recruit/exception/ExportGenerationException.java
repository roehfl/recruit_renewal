package com.shinyoung.recruit.exception;

/**
 * Excel workbook 생성/스트리밍 중 I/O 등으로 export 파일 생성에 실패했을 때 발생.
 */
public class ExportGenerationException extends RuntimeException {

    public ExportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
