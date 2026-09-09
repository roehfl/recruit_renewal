package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 일괄 지원서 PDF 다운로드 요청. 목록에서 체크한 지원서 id 목록을 받는다.
 *
 * <p>조회 성격이지만 id 를 20개까지 실어야 해 query string 대신 body 를 쓴다.
 * 중복 id 제거와 건수 상한 검증은 서비스가 담당한다(같은 id 를 반복 전송해 상한을 우회하지 못하게).
 */
public record ApplicationPdfBulkRequest(
        @NotEmpty(message = "다운로드할 지원서를 선택하세요.")
        List<Long> applicationIds
) {
}
