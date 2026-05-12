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



}



