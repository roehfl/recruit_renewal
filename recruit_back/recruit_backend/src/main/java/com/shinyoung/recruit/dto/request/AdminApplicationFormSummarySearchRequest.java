package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ApplicationFormConfigState;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.ReceptionStatus;

/**
 * 지원서 설정 현황판 검색 조건. 모두 선택이며 null 이면 해당 조건을 적용하지 않는다.
 * editableOnly 는 접수 시작 전이면서 마감되지 않은 공고만 남긴다.
 */
public record AdminApplicationFormSummarySearchRequest(
        JobPostingStatus status,
        ReceptionStatus receptionStatus,
        ApplicationFormConfigState configState,
        Boolean editableOnly,
        String keyword
) {
    public boolean isEditableOnly() {
        return Boolean.TRUE.equals(editableOnly);
    }

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
