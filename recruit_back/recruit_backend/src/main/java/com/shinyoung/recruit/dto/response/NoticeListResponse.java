package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Notice;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record NoticeListResponse(Long id, String title, boolean pinned, String createdAt) {

    public static NoticeListResponse from(Notice notice) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return new NoticeListResponse(notice.getId(), notice.getTitle(), notice.isPinned(), notice.getCreatedAt().format(formatter));
    }
}
