package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.AdminNoticeDetailResponse;
import com.shinyoung.recruit.dto.response.AdminNoticeListResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.NoticeSearchType;
import com.shinyoung.recruit.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 공지 조회. 삭제된 공지까지 본다.
 *
 * <p>권한은 SecurityConfig 의 {@code /api/admin/**} 매처(ADMIN / RECRUIT_ADMIN)가 건다.
 * 쓰기(등록·수정·삭제·복구)는 {@link BoardController} 의 {@code POST /board/notices...} 가 맡는다.
 */
@RestController
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping("/admin/notices")
    public ResponseEntity<ApiResponse<PageResponse<AdminNoticeListResponse>>> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL", required = false) NoticeSearchType searchType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(defaultValue = "false") boolean pinnedOnly
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                noticeService.getAdminNotices(page, size, searchType, keyword, deleted, pinnedOnly)));
    }

    @GetMapping("/admin/notices/{noticeId}")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> getNotice(@PathVariable Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(
                AdminNoticeDetailResponse.from(noticeService.getAdminNotice(noticeId))));
    }
}
