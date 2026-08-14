package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ClientEventLogCleanupResponse;
import com.shinyoung.recruit.dto.response.ClientEventLogResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.security.auth.RoleNames;
import com.shinyoung.recruit.service.ClientEventLogCleanupService;
import com.shinyoung.recruit.service.ClientEventLogReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 관리자 client event 조회 API(Phase 09f-3). 접근은 SecurityConfig narrow matcher
 * ({@code GET /api/admin/client-events/**} → RECRUIT_ADMIN/PRIVACY_ADMIN)가 게이팅하고,
 * 민감 필드(ip/ua/principalHash/stackSummary) 원문은 권한별 projection으로 추가 게이팅한다
 * ({@code AdminAuditController} 선례).
 */
@RestController
@RequiredArgsConstructor
public class AdminClientEventLogController {

    private static final String ROLE_PRIVACY_ADMIN = RoleNames.PRIVACY_ADMIN;

    private final ClientEventLogReadService clientEventLogReadService;
    private final ClientEventLogCleanupService clientEventLogCleanupService;

    @GetMapping("/admin/client-events")
    public ResponseEntity<ApiResponse<PageResponse<ClientEventLogResponse>>> searchEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ClientEventType eventType,
            @RequestParam(required = false) ClientEventSeverity severity,
            @RequestParam(required = false) ClientEventSource source,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) String clientSessionId,
            @RequestParam(required = false) String relatedCorrelationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(clientEventLogReadService.search(
                eventType, severity, source, applicationId, jobPostingId, clientSessionId,
                relatedCorrelationId, from, to, page, size, includeSensitive(userDetails))));
    }

    @GetMapping("/admin/client-events/{id}")
    public ResponseEntity<ApiResponse<ClientEventLogResponse>> getEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                clientEventLogReadService.getEvent(id, includeSensitive(userDetails))));
    }

    /** retention cleanup 수동 트리거 — 삭제(write)라 ROLE_PRIVACY_ADMIN 전용(SecurityConfig matcher, 설계 9장). */
    @PostMapping("/admin/client-events/cleanup")
    public ResponseEntity<ApiResponse<ClientEventLogCleanupResponse>> cleanup() {
        return ResponseEntity.ok(ApiResponse.success(
                new ClientEventLogCleanupResponse(clientEventLogCleanupService.cleanup())));
    }

    /** 민감 필드 원문은 ROLE_PRIVACY_ADMIN 전용. principal 부재 시 항상 마스킹(심층 방어). */
    private boolean includeSensitive(CustomUserDetails userDetails) {
        if (userDetails == null) {
            return false;
        }
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_PRIVACY_ADMIN::equals);
    }
}
