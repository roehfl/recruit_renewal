package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.condition.MessageHistoryCondition;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.service.MessageHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 메시지 발송 이력 API: 목록·상세. 상태·건수는 조회할 때 수신자 채널 상태로 계산한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/messages/history")
public class MessageHistoryAdminController {

    private final MessageHistoryService messageHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MessageSendSummaryResponse>>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MessageType type,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Boolean test,
            @RequestParam(required = false) MessageOrigin origin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        MessageHistoryCondition condition = new MessageHistoryCondition(from, to, type, jobPostingId, test, origin);
        return ResponseEntity.ok(ApiResponse.success(messageHistoryService.search(condition, page, size)));
    }

    @GetMapping("/{sendId}")
    public ResponseEntity<ApiResponse<MessageSendDetailResponse>> detail(@PathVariable Long sendId) {
        return ResponseEntity.ok(ApiResponse.success(messageHistoryService.detail(sendId)));
    }
}
