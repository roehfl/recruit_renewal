package com.shinyoung.recruit.dto.response;

import java.util.List;

/**
 * 주소 검색 응답(juso.go.kr 정제 DTO). 외부 원본 스키마 대신 프론트 계약을 안정적으로 고정한다.
 *
 * @param totalCount   전체 결과 수(juso 원본). <b>페이징 가능 범위가 아니다</b> — juso는 totalCount와 별개로
 *                     조회 범위 상한을 두므로 {@code totalCount / countPerPage}로 페이지 수를 계산하면
 *                     조회 불가능한 페이지가 나온다. 페이지네이션은 반드시 {@code maxPage}를 쓴다.
 * @param currentPage  현재 페이지(1-base)
 * @param countPerPage 페이지당 개수
 * @param maxPage      실제 조회 가능한 마지막 페이지. {@code min(ceil(totalCount/countPerPage),
 *                     floor(maxSearchRange/countPerPage))}. 결과 없음/비정상 응답이면 0.
 * @param addresses    주소 목록
 */
public record AddressSearchResponse(
        int totalCount,
        int currentPage,
        int countPerPage,
        int maxPage,
        List<AddressItem> addresses
) {

    /**
     * 개별 주소 항목.
     *
     * @param roadAddr 전체 도로명주소
     * @param jibunAddr 지번주소
     * @param zipNo     우편번호
     * @param siNm      시도명
     * @param sggNm     시군구명
     * @param emdNm     읍면동명
     * @param bdNm      건물명
     * @param engAddr   영문 도로명주소
     */
    public record AddressItem(
            String roadAddr,
            String jibunAddr,
            String zipNo,
            String siNm,
            String sggNm,
            String emdNm,
            String bdNm,
            String engAddr
    ) {
    }
}
