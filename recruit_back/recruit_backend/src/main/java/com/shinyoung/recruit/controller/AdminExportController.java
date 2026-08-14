package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.AdminDatasetExportService;
import com.shinyoung.recruit.service.ApplicationExportService;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.ExcelExportFile;
import com.shinyoung.recruit.service.ExportAuditContext;
import com.shinyoung.recruit.service.ExportAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 운영자 Excel export 엔드포인트(read-only, admin 전용). Phase 07a는 applications download를 담당한다.
 *
 * <p>대응 list 엔드포인트와 동일한 필터를 쓰되 page/size는 무시하고 전체 행을 내보낸다.
 * applications export는 연락처(phoneNumber/email) 평문을 포함하는 PII surface이므로 생성 시 audit 로그를 남긴다.
 */
@RestController
@RequiredArgsConstructor
public class AdminExportController {

    /**
     * applications export/PDF는 PII 평문 노출 경로다. 향후 role matrix에서 "목록 조회 admin"과
     * "PII export admin"을 분리하기 위한 정책 상수(현재 SecurityConfig 세분화는 하지 않음).
     */
    public static final String EXPORT_APPLICATION_PII = "EXPORT_APPLICATION_PII";

    private final ApplicationExportService applicationExportService;
    private final AdminDatasetExportService adminDatasetExportService;
    private final ExcelExportResponseFactory excelExportResponseFactory;
    private final ExportAuditLogger exportAuditLogger;
    private final CurrentEmployeeService currentEmployeeService;

    @GetMapping("/admin/applications/export")
    public ResponseEntity<StreamingResponseBody> exportApplications(
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Long jobPositionId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        return export(jobPostingId, jobPositionId, status, userDetails, request);
    }

    @GetMapping("/admin/job-postings/{jobPostingId}/applications/export")
    public ResponseEntity<StreamingResponseBody> exportApplicationsByJobPosting(
            @PathVariable Long jobPostingId,
            @RequestParam(required = false) Long jobPositionId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        return export(jobPostingId, jobPositionId, status, userDetails, request);
    }

    private ResponseEntity<StreamingResponseBody> export(
            Long jobPostingId,
            Long jobPositionId,
            String status,
            CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        // audit filter 에는 raw status 가 아니라 canonical 값(enum name)을 남긴다(9b 리뷰 Medium 2).
        JobApplicationStatus parsedStatus = applicationExportService.parseStatus(status);
        ExcelExportFile file = applicationExportService.exportApplications(jobPostingId, jobPositionId, status);
        // egress fail-close(Phase 09b): 감사 기록 실패 시 응답 없이 전파 — temp xlsx 누수 방지(리뷰 2차 #3).
        try {
            exportAuditLogger.logApplicationsExport(
                    auditContext(actor, userDetails, request),
                    jobPostingId,
                    jobPositionId,
                    parsedStatus == null ? null : parsedStatus.name(),
                    file
            );
            return excelExportResponseFactory.toResponse(file);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }

    @GetMapping("/admin/stages/{stageId}/results/export")
    public ResponseEntity<StreamingResponseBody> exportStageResults(
            @PathVariable Long stageId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        ExcelExportFile file = adminDatasetExportService.exportStageResults(stageId);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("stageId", stageId);
        try {
            exportAuditLogger.logExport("STAGE_RESULTS", auditContext(actor, userDetails, request), filters, file);
            return excelExportResponseFactory.toResponse(file);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }

    @GetMapping("/admin/job-postings/{jobPostingId}/interviews/export")
    public ResponseEntity<StreamingResponseBody> exportInterviews(
            @PathVariable Long jobPostingId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        ExcelExportFile file = adminDatasetExportService.exportInterviews(jobPostingId, stageId, status, from, to);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("jobPostingId", jobPostingId);
        filters.put("stageId", stageId);
        filters.put("status", status == null ? null : status.name());
        filters.put("from", from == null ? null : from.toString());
        filters.put("to", to == null ? null : to.toString());
        try {
            exportAuditLogger.logExport("INTERVIEWS", auditContext(actor, userDetails, request), filters, file);
            return excelExportResponseFactory.toResponse(file);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }

    @GetMapping("/admin/stages/{stageId}/interview-evaluations/export")
    public ResponseEntity<StreamingResponseBody> exportStageEvaluations(
            @PathVariable Long stageId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        ExcelExportFile file = adminDatasetExportService.exportStageEvaluations(stageId);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("stageId", stageId);
        try {
            exportAuditLogger.logExport(
                    "INTERVIEW_EVALUATIONS", auditContext(actor, userDetails, request), filters, file);
            return excelExportResponseFactory.toResponse(file);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }

    private void deleteQuietly(ExcelExportFile file) {
        try {
            Files.deleteIfExists(file.path());
        } catch (IOException ignored) {
            // temp 파일 정리 실패는 원인 예외 전파를 막지 않는다.
        }
    }

    private ExportAuditContext auditContext(String actor, CustomUserDetails userDetails, HttpServletRequest request) {
        String authority = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return new ExportAuditContext(
                actor,
                authority,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                requestId
        );
    }
}
