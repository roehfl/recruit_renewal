package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.request.MessageTestSendRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.dto.response.MessageTargetResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResponse;
import com.shinyoung.recruit.dto.response.MessageVariableResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.MessageSendService;
import com.shinyoung.recruit.service.MessageTargetService;
import com.shinyoung.recruit.service.MessageTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 메시지 발송 화면 API: 변수 카탈로그·대상자 조회·테스트 발송·발송 접수. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/messages")
public class MessageSendAdminController {

    private final MessageTemplateService messageTemplateService;
    private final MessageTargetService messageTargetService;
    private final MessageSendService messageSendService;

    @GetMapping("/variables")
    public ResponseEntity<ApiResponse<List<MessageVariableResponse>>> getVariables() {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.getVariables()));
    }

    @GetMapping("/targets")
    public ResponseEntity<ApiResponse<MessageTargetResponse>> getTargets(
            @RequestParam MessageType type,
            @RequestParam Long jobPostingId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) StageResultStatus resultStatus,
            @RequestParam(required = false) String interviewGroup,
            @RequestParam(required = false) JobApplicationStatus applicationStatus
    ) {
        MessageTargetCondition condition = new MessageTargetCondition(
                type, jobPostingId, stageId, resultStatus, interviewGroup, applicationStatus);
        return ResponseEntity.ok(ApiResponse.success(messageTargetService.getTargets(condition)));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<MessageTestSendResponse>> testSend(
            @Valid @RequestBody MessageTestSendRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageSendService.testSend(request, userDetails)));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<MessageSendResultResponse>> send(
            @Valid @RequestBody MessageSendRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageSendService.send(request, userDetails)));
    }
}
