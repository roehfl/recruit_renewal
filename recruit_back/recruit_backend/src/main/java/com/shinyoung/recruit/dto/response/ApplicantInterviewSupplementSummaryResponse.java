package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;

/** 마이페이지 지원 목록의 "추가사항 입력" 칸. 버튼 활성화는 open(서버 시각 판정)으로 정한다. */
public record ApplicantInterviewSupplementSummaryResponse(
        Long applicationId,
        Long stageId,
        String stageName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        boolean open,
        long remainingSeconds,
        int questionCount,
        int answeredCount
) {
}
