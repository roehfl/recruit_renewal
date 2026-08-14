package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.QuestionTemplateCreateRequest;
import com.shinyoung.recruit.dto.request.QuestionTemplateUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.QuestionTemplateResponse;
import com.shinyoung.recruit.service.QuestionTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/question-templates")
public class QuestionTemplateController {

    private final QuestionTemplateService questionTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<QuestionTemplateResponse>>> getTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(ApiResponse.success(questionTemplateService.getTemplates(active, page, size)));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<QuestionTemplateResponse>> getTemplate(@PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success(questionTemplateService.getTemplate(templateId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionTemplateResponse>> createTemplate(
            @Valid @RequestBody QuestionTemplateCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(questionTemplateService.createTemplate(request)));
    }

    @PostMapping("/{templateId}")
    public ResponseEntity<ApiResponse<QuestionTemplateResponse>> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody QuestionTemplateUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(questionTemplateService.updateTemplate(templateId, request)));
    }

    @PostMapping("/{templateId}/deactivate")
    public ResponseEntity<ApiResponse<QuestionTemplateResponse>> deactivateTemplate(@PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success(questionTemplateService.deactivateTemplate(templateId)));
    }
}
