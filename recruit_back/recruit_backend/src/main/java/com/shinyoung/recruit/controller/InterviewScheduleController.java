package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.InterviewScheduleSearchRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewScheduleRowResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.InterviewScheduleUploadResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.CurrentEmployeeService;
import com.shinyoung.recruit.service.ExcelExportFile;
import com.shinyoung.recruit.service.ExportAuditContext;
import com.shinyoung.recruit.service.ExportAuditLogger;
import com.shinyoung.recruit.service.InterviewScheduleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 관리자 면접 스케줄링(엑셀 전용 입력). 조회 표·템플릿·다운로드·업로드가 같은 9열 양식을 쓴다.
 *
 * <p>업로드는 검증을 모두 통과해야만 단계의 기존 면접을 교체하고 즉시 확정한다. 행 오류는 400 + 오류 목록으로 돌려준다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings/{jobPostingId}/interview-schedules")
public class InterviewScheduleController {

    private final InterviewScheduleService interviewScheduleService;
    private final ExcelExportResponseFactory excelExportResponseFactory;
    private final ExportAuditLogger exportAuditLogger;
    private final CurrentEmployeeService currentEmployeeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminInterviewScheduleRowResponse>>> getSchedules(
            @PathVariable Long jobPostingId,
            @ModelAttribute InterviewScheduleSearchRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(interviewScheduleService.getSchedules(jobPostingId, request)));
    }

    /** 조회 조건 그대로 행을 채운 xlsx. 성명이 실리는 개인정보 반출이라 export 감사 로그를 남긴다. */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable Long jobPostingId,
            @ModelAttribute InterviewScheduleSearchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        ExcelExportFile file = interviewScheduleService.exportSchedules(jobPostingId, request);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("jobPostingId", jobPostingId);
        filters.put("stageId", request.stageId());
        filters.put("applicationType", request.applicationType() == null ? null : request.applicationType().name());
        filters.put("jobPositionId", request.jobPositionId());
        filters.put("workLocation", request.workLocation());
        filters.put("groupName", request.groupName());
        // egress fail-close: 감사 기록 실패 시 응답 없이 전파 — temp xlsx 누수 방지를 위해 정리 후 던진다.
        try {
            exportAuditLogger.logExport(
                    "INTERVIEW_SCHEDULES", auditContext(actor, userDetails, httpRequest), filters, file);
            return excelExportResponseFactory.toResponse(file);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
    }

    /** 헤더만 있는 업로드 양식(데이터 0행, 개인정보 없음). */
    @GetMapping("/upload-template")
    public ResponseEntity<StreamingResponseBody> uploadTemplate(@PathVariable Long jobPostingId) {
        return excelExportResponseFactory.toResponse(interviewScheduleService.generateTemplate(jobPostingId));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<InterviewScheduleUploadResponse>> upload(
            @PathVariable Long jobPostingId,
            @RequestParam Long stageId,
            @RequestParam("file") MultipartFile file
    ) {
        InterviewScheduleUploadResponse response = interviewScheduleService.upload(jobPostingId, stageId, file);
        if (response.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("업로드 검증에 실패하여 반영하지 않았습니다.", response));
        }
        return ResponseEntity.ok(ApiResponse.success(response));
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
