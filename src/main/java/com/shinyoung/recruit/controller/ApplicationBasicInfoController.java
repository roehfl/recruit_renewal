package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.BasicInfoSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.BasicInfoResponse;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.ApplicationBasicInfoService;
import com.shinyoung.recruit.service.CurrentApplicantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationBasicInfoController {

    private final ApplicationBasicInfoService applicationBasicInfoService;
    private final CurrentApplicantService currentApplicantService;

    @GetMapping("/applications/{applicationId}/basic-info")
    public ResponseEntity<ApiResponse<BasicInfoResponse>> getBasicInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        BasicInfoResponse response = applicationBasicInfoService.getBasicInfo(applicantId, applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/applications/{applicationId}/basic-info")
    public ResponseEntity<ApiResponse<BasicInfoResponse>> saveBasicInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody BasicInfoSaveRequest request
    ) {
        Long applicantId = currentApplicantService.getCurrentApplicantId(userDetails);
        BasicInfoResponse response = applicationBasicInfoService.saveBasicInfo(applicantId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
