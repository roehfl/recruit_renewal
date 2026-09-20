package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.NoticeSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.NoticeDetailResponse;
import com.shinyoung.recruit.dto.response.NoticeListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.NoticeSearchType;
import com.shinyoung.recruit.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/board")

public class BoardController {
    private final NoticeService noticeService;

    public BoardController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/notices")
    public ResponseEntity<ApiResponse<PageResponse<NoticeListResponse>>> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL", required = false) NoticeSearchType searchType,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getNotices(page, size, searchType, keyword)));
    }

    @GetMapping("/notices/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeDetailResponse>> getNotice(@PathVariable Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(NoticeDetailResponse.from(noticeService.getNotice(noticeId))));
    }


    @PostMapping("/notices")
    public ResponseEntity<ApiResponse<Long>> addNotices(@Valid @RequestBody NoticeSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.create(request)));
    }

    @PostMapping("/notices/{noticeId}")
    public ResponseEntity<ApiResponse<Long>> updateNotice(
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.update(noticeId, request)));
    }

    @PostMapping("/notices/{noticeId}/delete")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(@PathVariable Long noticeId) {
        noticeService.delete(noticeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notices/{noticeId}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreNotice(@PathVariable Long noticeId) {
        noticeService.restore(noticeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notices/{noticeId}/pin")
    public ResponseEntity<ApiResponse<Void>> pinNotice(@PathVariable Long noticeId) {
        noticeService.changePinned(noticeId, true);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notices/{noticeId}/unpin")
    public ResponseEntity<ApiResponse<Void>> unpinNotice(@PathVariable Long noticeId) {
        noticeService.changePinned(noticeId, false);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

}



