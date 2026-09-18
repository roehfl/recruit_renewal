package com.shinyoung.recruit.dto.response;

import java.util.List;

/** 지원현황 엑셀 컬럼 카탈로그의 그룹 1개(모달의 체크박스 묶음). 순서 = 카탈로그 선언 순서. */
public record ApplicationExportColumnGroupResponse(String group, List<Column> columns) {

    /** @param key 요청 {@code columns} 에 그대로 쓰는 값({@code ApplicationExportColumn} 이름) */
    public record Column(String key, String label, boolean defaultSelected) {
    }
}
