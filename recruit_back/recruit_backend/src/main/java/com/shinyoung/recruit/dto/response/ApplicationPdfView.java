package com.shinyoung.recruit.dto.response;

import java.util.List;

/**
 * Application PDF 템플릿용 표시 모델. 서비스가 각 admin 응답 DTO를 표시 문자열로 평탄화해 담고,
 * 템플릿은 이 구조만 순회하므로 모든 값이 {@code th:text}로만 렌더된다(HTML injection 차단).
 *
 * <p>열 헤더({@code columns})를 행에서 분리해 섹션 레벨로 올렸다. 라벨이 값에 묶여 있던 이전 구조로는
 * 관리자 화면과 같은 가로 표를 만들 수 없었기 때문이다. 대부분의 섹션은 {@code Layout#TABLE} 하나로 렌더되고,
 * 불규칙 병합이 필요한 기본 정보만 {@link BasicInfo}로 분리해 전용 fragment가 담당한다.
 *
 * <p>{@code ci}/{@code ciHash}/{@code password}는 어떤 필드에도 담지 않는다.
 */
public record ApplicationPdfView(Header header, BasicInfo basicInfo, List<Section> sections) {

    /** 지원사항 표 + 파일명/감사에 쓰는 식별 정보. */
    public record Header(
            Long applicationId,
            String applicantName,
            String jobPostingTitle,
            String applicationType,
            String jobPositionName,
            String workLocation,
            String status,
            String submittedAt
    ) {
    }

    /**
     * 기본 정보 표. 사진 칸이 {@code rowspan}으로 걸리고 이름/연락처가 2단으로 중첩되어
     * generic 표로 표현되지 않으므로 별도 record로 둔다.
     *
     * @param photoDataUri 사진 base64 data URI. 없으면 null(템플릿이 "사진 없음"으로 표시)
     */
    public record BasicInfo(
            String photoDataUri,
            String nameKorean,
            String nameEnglish,
            String mobilePhone,
            String emergencyPhone,
            String nationality,
            String veteran,
            String birthDate,
            String disability,
            String email,
            String address
    ) {
    }

    /**
     * @param note         표 위에 붙는 안내 문구(화면의 "※ ..." 주석). 없으면 null
     * @param columns      표의 열 헤더. QA 레이아웃에서는 비어 있다.
     * @param columnWidths 화면 colgroup 과 같은 열 너비(예: "45%"). 비어 있으면 균등 분할한다.
     * @param rows         각 행의 값 목록. 표는 {@code columns}와 길이가 같고, QA는 [질문, 답변] 2개다.
     */
    public record Section(
            String title,
            String note,
            Layout layout,
            List<String> columns,
            List<String> columnWidths,
            List<Row> rows,
            String emptyMessage
    ) {
    }

    public enum Layout {
        /** 열 헤더 1회 + 행 N개의 가로 표. 값은 왼쪽 정렬. */
        TABLE,
        /** 학기별 성적 표. 구조는 TABLE 과 같고 화면처럼 값을 가운데 정렬한다. */
        GRADE,
        /** 질문 아래 답변이 이어지는 문답 형식(자기소개서). */
        QA
    }

    public record Row(List<String> values) {
    }
}
