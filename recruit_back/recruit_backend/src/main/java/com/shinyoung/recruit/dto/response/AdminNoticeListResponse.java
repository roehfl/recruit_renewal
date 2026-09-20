package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Notice;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 관리자 공지 목록 행. 삭제된 공지도 포함하므로 {@code deleted} 를 함께 내려준다.
 *
 * <p>{@code createdBy}·{@code updatedBy} 는 AuditorAware 빈이 없어 현재 항상 null 이다.
 */
public record AdminNoticeListResponse(
        Long id,
        String title,
        boolean pinned,
        boolean deleted,
        String createdAt,
        String createdBy,
        String updatedAt,
        String updatedBy
) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static AdminNoticeListResponse from(Notice notice) {
        return new AdminNoticeListResponse(
                notice.getId(),
                notice.getTitle(),
                notice.isPinned(),
                notice.isDeleted(),
                format(notice.getCreatedAt()),
                notice.getCreatedBy(),
                format(notice.getUpdatedAt()),
                notice.getUpdatedBy()
        );
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FORMATTER);
    }
}
