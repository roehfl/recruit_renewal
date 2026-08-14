package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AttachmentStorageHealthScanResponse;
import com.shinyoung.recruit.service.AttachmentStorageHealthScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminAttachmentStorageHealthController {

    private final AttachmentStorageHealthScanService scanService;

    @PostMapping("/admin/attachments/storage-health/scan")
    public ResponseEntity<ApiResponse<AttachmentStorageHealthScanResponse>> scan() {
        return ResponseEntity.ok(ApiResponse.success(scanService.scanDryRun()));
    }
}
