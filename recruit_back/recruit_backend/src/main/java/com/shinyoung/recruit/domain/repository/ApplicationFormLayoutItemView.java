package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;

/** 여러 공고의 레이아웃 배치 현황을 한 번에 읽기 위한 조회 전용 뷰. */
public record ApplicationFormLayoutItemView(
        Long jobPostingId,
        Integer pageNo,
        ApplicationSectionType sectionType
) {
}
