package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.AdminAttachmentResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationAnswerResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationStageResultResponse;
import com.shinyoung.recruit.dto.response.AdminAwardResponse;
import com.shinyoung.recruit.dto.response.AdminBasicInfoResponse;
import com.shinyoung.recruit.dto.response.AdminCareerResponse;
import com.shinyoung.recruit.dto.response.AdminCertificateResponse;
import com.shinyoung.recruit.dto.response.AdminEducationResponse;
import com.shinyoung.recruit.dto.response.AdminGapPeriodResponse;
import com.shinyoung.recruit.dto.response.AdminLanguageResponse;
import com.shinyoung.recruit.dto.response.AdminMilitaryResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.service.AdminApplicationSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminApplicationSectionController {

    private final AdminApplicationSectionService adminApplicationSectionService;

    @GetMapping("/admin/applications/{applicationId}/educations")
    public ResponseEntity<ApiResponse<List<AdminEducationResponse>>> getEducations(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getEducations(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/careers")
    public ResponseEntity<ApiResponse<AdminCareerResponse>> getCareers(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getCareers(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/certificates")
    public ResponseEntity<ApiResponse<List<AdminCertificateResponse>>> getCertificates(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getCertificates(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/languages")
    public ResponseEntity<ApiResponse<List<AdminLanguageResponse>>> getLanguages(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getLanguages(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/basic-info")
    public ResponseEntity<ApiResponse<AdminBasicInfoResponse>> getBasicInfo(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getBasicInfo(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/military")
    public ResponseEntity<ApiResponse<AdminMilitaryResponse>> getMilitary(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getMilitary(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/awards")
    public ResponseEntity<ApiResponse<List<AdminAwardResponse>>> getAwards(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getAwards(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/gap-periods")
    public ResponseEntity<ApiResponse<List<AdminGapPeriodResponse>>> getGapPeriods(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getGapPeriods(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/attachments")
    public ResponseEntity<ApiResponse<List<AdminAttachmentResponse>>> getAttachments(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getAttachments(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/answers")
    public ResponseEntity<ApiResponse<List<AdminApplicationAnswerResponse>>> getAnswers(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getAnswers(applicationId)));
    }

    @GetMapping("/admin/applications/{applicationId}/stage-results")
    public ResponseEntity<ApiResponse<List<AdminApplicationStageResultResponse>>> getStageResults(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminApplicationSectionService.getStageResults(applicationId)));
    }
}
