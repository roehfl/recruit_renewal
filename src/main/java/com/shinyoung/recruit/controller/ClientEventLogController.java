// src/main/java/com/shinyoung/recruit/controller/ClientEventLogController.java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ClientEventLogRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ClientEventLogIngestResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ClientEventLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * client event 수집 API(Phase 09f-1, 설계 6.1). public write endpoint —
 * permitAll(로그인 전/세션 만료/회원가입 화면 오류도 수집)이며 JSON-only 계약으로 고정한다.
 * 인증 정보/IP/User-Agent는 service가 서버에서만 추출한다(FE body 값 신뢰 금지).
 */
@RestController
@RequiredArgsConstructor
public class ClientEventLogController {

    private final ClientEventLogService clientEventLogService;

    @PostMapping(value = "/client-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ClientEventLogIngestResponse>> record(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ClientEventLogRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                clientEventLogService.record(request, userDetails, servletRequest)));
    }
}
