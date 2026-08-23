package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Notice;

import java.time.LocalDateTime;

public record NoticeDetailResponse(
        Long id,
        String title,
        String contentHtml,
        boolean pinned,
        LocalDateTime createdAt
) {
    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContentHtml(),
                notice.isPinned(),
                notice.getCreatedAt()
        );
    }
}
