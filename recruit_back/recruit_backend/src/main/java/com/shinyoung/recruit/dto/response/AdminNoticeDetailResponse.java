package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.common.util.HtmlTextUtils;
import com.shinyoung.recruit.domain.entity.Notice;

import java.time.LocalDateTime;

/**
 * 관리자 공지 상세(편집용). 삭제된 공지도 조회된다.
 *
 * <p>{@code contentHtml} 은 관리자 에디터가 그대로 렌더하므로 지원자 상세와 똑같이 정제해서 내려준다.
 * 정제는 멱등이라 다시 저장해도 내용이 더 깎이지 않는다.
 */
public record AdminNoticeDetailResponse(
        Long id,
        String title,
        String contentHtml,
        boolean pinned,
        boolean deleted,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {
    public static AdminNoticeDetailResponse from(Notice notice) {
        return new AdminNoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                HtmlTextUtils.sanitize(notice.getContentHtml()),
                notice.isPinned(),
                notice.isDeleted(),
                notice.getCreatedAt(),
                notice.getCreatedBy(),
                notice.getUpdatedAt(),
                notice.getUpdatedBy()
        );
    }
}
