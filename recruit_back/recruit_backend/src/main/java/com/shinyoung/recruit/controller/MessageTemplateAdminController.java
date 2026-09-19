package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.MessageTemplateSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MessageTemplateResponse;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.service.MessageTemplateService;
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

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/message-templates")
public class MessageTemplateAdminController {

    private final MessageTemplateService messageTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MessageTemplateResponse>>> getTemplates(
            @RequestParam(required = false) MessageType type
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.getTemplates(type)));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<MessageTemplateResponse>> getTemplate(@PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.getTemplate(templateId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageTemplateResponse>> createTemplate(
            @Valid @RequestBody MessageTemplateSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.createTemplate(request)));
    }

    @PostMapping("/{templateId}")
    public ResponseEntity<ApiResponse<MessageTemplateResponse>> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody MessageTemplateSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(messageTemplateService.updateTemplate(templateId, request)));
    }

    @PostMapping("/{templateId}/delete")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable Long templateId) {
        messageTemplateService.deleteTemplate(templateId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
