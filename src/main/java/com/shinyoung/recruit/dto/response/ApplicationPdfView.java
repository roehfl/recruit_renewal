package com.shinyoung.recruit.dto.response;

import java.util.List;

/**
 * Application PDF 템플릿용 표시 모델(generic). 서비스가 각 admin 응답 DTO를 label/value로 평탄화해 담고,
 * 템플릿은 이 구조만 순회하므로 모든 값이 {@code th:text}로만 렌더된다(HTML injection 차단).
 *
 * <p>{@code ci}/{@code ciHash}/{@code password}는 어떤 필드에도 담지 않는다.
 */
public record ApplicationPdfView(Header header, List<Section> sections) {

    public record Header(
            Long applicationId,
            String applicantName,
            String phoneNumber,
            String email,
            String jobPostingTitle,
            String jobPositionName,
            String status,
            String submittedAt
    ) {
    }

    public record Section(String title, List<RecordRow> records, String emptyMessage) {
    }

    public record RecordRow(List<Field> fields) {
    }

    public record Field(String label, String value) {
    }
}
