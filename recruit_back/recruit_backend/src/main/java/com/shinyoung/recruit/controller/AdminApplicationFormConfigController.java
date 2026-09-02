package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.ApplicationFormConfigResponse;
import com.shinyoung.recruit.service.ApplicationFormConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings/{jobPostingId}/application-form-config")
public class AdminApplicationFormConfigController {

    private final ApplicationFormConfigService applicationFormConfigService;

    @PostMapping
    public ResponseEntity<ApiResponse<ApplicationFormConfigResponse>> saveConfig(
            @PathVariable Long jobPostingId,
            @Valid @RequestBody ApplicationFormConfigRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(applicationFormConfigService.save(jobPostingId, request)));
    }
}
