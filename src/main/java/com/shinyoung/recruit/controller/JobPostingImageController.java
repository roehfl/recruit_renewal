package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.JobPostingImageAltTextUpdateRequest;
import com.shinyoung.recruit.dto.request.JobPostingImageOrderRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.service.JobPostingImageService;
import com.shinyoung.recruit.service.PostingImageResource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings/{jobPostingId}/images")
public class JobPostingImageController {

    private final JobPostingImageService jobPostingImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> addImage(
            @PathVariable Long jobPostingId,
            @RequestPart("file") MultipartFile file,
            @RequestParam String altText,
            @RequestParam(required = false) Integer sortOrder
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                jobPostingImageService.addImage(jobPostingId, file, altText, sortOrder)));
    }

    @PostMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Long>> updateAltText(
            @PathVariable Long jobPostingId,
            @PathVariable Long imageId,
            @RequestBody JobPostingImageAltTextUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                jobPostingImageService.updateAltText(jobPostingId, imageId, request.altText())));
    }

    @PostMapping("/{imageId}/delete")
    public ResponseEntity<ApiResponse<Long>> deleteImage(
            @PathVariable Long jobPostingId,
            @PathVariable Long imageId
    ) {
        jobPostingImageService.deleteImage(jobPostingId, imageId);
        return ResponseEntity.ok(ApiResponse.success(imageId));
    }

    @PostMapping("/order")
    public ResponseEntity<ApiResponse<Long>> reorder(
            @PathVariable Long jobPostingId,
            @RequestBody JobPostingImageOrderRequest request
    ) {
        jobPostingImageService.reorder(jobPostingId, request.imageIds());
        return ResponseEntity.ok(ApiResponse.success(jobPostingId));
    }

    @GetMapping("/{imageId}/file")
    public ResponseEntity<Resource> serveImage(
            @PathVariable Long jobPostingId,
            @PathVariable Long imageId
    ) {
        return toImageResponse(jobPostingImageService.loadAdminImage(jobPostingId, imageId));
    }

    static ResponseEntity<Resource> toImageResponse(PostingImageResource image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(image.resource());
    }
}
