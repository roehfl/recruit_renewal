package com.shinyoung.recruit.service;

import java.nio.file.Path;
import java.util.List;

/**
 * 일괄 지원서 PDF zip. 실제 내용은 temp 파일에 있고 응답은 이를 스트리밍한 뒤 삭제한다.
 *
 * @param entries 담긴 지원서의 감사용 메타데이터. PDF 내용(byte[])은 들고 있지 않는다.
 */
public record ApplicationPdfZipFile(Path path, String fileName, List<Entry> entries) {

    public static final String CONTENT_TYPE = "application/zip";

    public record Entry(Long applicationId, Long jobPostingId, Long jobPositionId) {
    }
}
