package com.shinyoung.recruit.exception;

/**
 * Export 대상 행 수가 허용 최대치를 초과했을 때 발생. workbook 생성 전 count 선검증에서 던진다.
 */
public class ExportRowLimitExceededException extends RuntimeException {

    public static final String CODE = "EXPORT_ROW_LIMIT_EXCEEDED";

    public ExportRowLimitExceededException(long actualCount, long maxRows) {
        super(CODE + ": export 대상 " + actualCount + "행이 허용 최대 " + maxRows + "행을 초과했습니다.");
    }
}
