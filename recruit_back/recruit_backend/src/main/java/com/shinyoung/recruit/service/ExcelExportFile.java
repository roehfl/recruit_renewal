package com.shinyoung.recruit.service;

import java.nio.file.Path;

/**
 * 생성된 export xlsx temp 파일 핸들. service가 read-only 트랜잭션 안에서 생성까지만 담당하고,
 * controller/response layer가 이 파일을 스트리밍한 뒤 전송 완료 후 삭제한다(service가 먼저 삭제하지 않는다).
 */
public record ExcelExportFile(Path path, String fileName, long rowCount) {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
}
