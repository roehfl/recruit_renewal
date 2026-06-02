package com.shinyoung.recruit.exception;

/**
 * Application PDF 생성(Thymeleaf 렌더 / openhtmltopdf) 실패 시 발생. 500으로 매핑한다.
 */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
