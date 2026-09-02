package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.ApplicationFormConfigState;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;

import java.time.LocalDateTime;

/** 지원서 설정 현황판의 한 행. 섹션 사용/필수 개수는 한 칸에 함께 보여주므로 묶어서 내려준다. */
public record AdminApplicationFormSummaryResponse(
        Long jobPostingId,
        String title,
        JobPostingType postingType,
        JobPostingStatus status,
        ReceptionStatus receptionStatus,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        SectionSummary sectionSummary,
        int activeQuestionCount,
        int requiredQuestionCount,
        boolean layoutStored,
        int pageCount,
        ApplicationFormConfigState configState,
        /** 접수 시작 전 && 미마감. configState 와 직교하며 화면에서는 자물쇠로 덧붙인다. */
        boolean editable,
        LocalDateTime updatedAt
) {
    public record SectionSummary(int enabledCount, int requiredCount) {
    }
}
