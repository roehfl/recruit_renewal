package com.shinyoung.recruit.exception;

/** 일괄 PDF 다운로드 요청 건수가 허용 상한을 넘었을 때. 400으로 매핑된다. */
public class PdfBulkLimitExceededException extends RuntimeException {

    public static final String CODE = "PDF_BULK_LIMIT_EXCEEDED";

    public PdfBulkLimitExceededException(int requestedCount, int maxCount) {
        super("한 번에 최대 " + maxCount + "건까지 다운로드할 수 있습니다. (요청 " + requestedCount + "건)");
    }
}
