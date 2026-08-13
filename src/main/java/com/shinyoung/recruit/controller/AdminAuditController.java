package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AuditActivityResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.security.auth.RoleNames;
import com.shinyoung.recruit.service.AuditActivityReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 관리자 감사 로그 read API(Phase 09b). 접근은 SecurityConfig 의 narrow matcher
 * ({@code GET /api/admin/audit/**} → RECRUIT_ADMIN/PRIVACY_ADMIN)가 게이팅하고,
 * 민감 필드(ip/ua) 원문은 컨트롤러에서 권한별 projection(마스킹 vs 원문)으로 추가 게이팅한다(ADR-0007).
 */
@RestController
@RequiredArgsConstructor
public class AdminAuditController {

    private static final String ROLE_PRIVACY_ADMIN = RoleNames.PRIVACY_ADMIN;

    private final AuditActivityReadService auditActivityReadService;

    @GetMapping("/admin/audit/activities")
    public ResponseEntity<ApiResponse<PageResponse<AuditActivityResponse>>> searchActivities(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditActionResult actionResult,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(auditActivityReadService.search(
                actorId, actionType, actionResult, targetType, jobPostingId, applicationId,
                from, to, page, size, includeSensitive(userDetails))));
    }

    @GetMapping("/admin/audit/activities/{id}")
    public ResponseEntity<ApiResponse<AuditActivityResponse>> getActivity(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                auditActivityReadService.getActivity(id, includeSensitive(userDetails))));
    }

    /** ip/ua 원문은 ROLE_PRIVACY_ADMIN 전용(ADR-0007). principal 부재 시 항상 마스킹(심층 방어). */
    private boolean includeSensitive(CustomUserDetails userDetails) {
        if (userDetails == null) {
            return false;
        }
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_PRIVACY_ADMIN::equals);
    }
}
