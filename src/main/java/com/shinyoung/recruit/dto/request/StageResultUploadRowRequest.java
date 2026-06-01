package com.shinyoung.recruit.dto.request;

/**
 * upload-template sheet의 단일 데이터 행을 파싱한 raw 모델. 셀은 모두 문자열로 읽고(로케일 의존 제거),
 * 타입/허용값 해석은 service의 검증 단계에서 수행한다.
 *
 * <p>{@code stageResultId}/{@code applicationId}/{@code applicantName}/{@code stageResultUpdatedAt}는
 * read-only echo·동시성 토큰이고, {@code resultStatus}/{@code score}/{@code comment}만 편집 대상이다.
 * {@code stageId}는 row가 아니라 path로만 판단하므로 컬럼으로 두지 않는다.
 *
 * @param rowNumber           스프레드시트 행 번호(1-based, header=1)
 * @param stageResultId       매칭 키(raw)
 * @param applicationId       교차검증용(raw)
 * @param applicantName       사람 눈 확인용(매칭 미사용)
 * @param stageResultUpdatedAt 낙관적 동시성 토큰(raw ISO-8601 문자열)
 * @param resultStatus        편집 대상(raw)
 * @param score               편집 대상(raw)
 * @param comment             편집 대상(raw)
 * @param formulaCellPresent  행 내 셀 중 formula 셀이 있으면 true(formula injection 방어)
 * @param tokenCellNotString  토큰 셀이 문자열이 아닌 numeric/date면 true
 */
public record StageResultUploadRowRequest(
        int rowNumber,
        String stageResultId,
        String applicationId,
        String applicantName,
        String stageResultUpdatedAt,
        String resultStatus,
        String score,
        String comment,
        boolean formulaCellPresent,
        boolean tokenCellNotString
) {
}
