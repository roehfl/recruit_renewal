package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.SchoolCreateRequest;
import com.shinyoung.recruit.dto.request.SchoolUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.SchoolImportResponse;
import com.shinyoung.recruit.dto.response.SchoolResponse;
import com.shinyoung.recruit.service.SchoolImportService;
import com.shinyoung.recruit.service.SchoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * School admin 관리(Phase 08b/08c). 비활성 포함 페이지 목록 + 생성/수정(POST 컨벤션) + xlsx 일괄 import(upsert).
 * schoolCode 는 생성 후 불변(수정 요청에 미포함)이다.
 */
@RestController
@RequiredArgsConstructor
public class AdminSchoolController {

    private final SchoolService schoolService;
    private final SchoolImportService schoolImportService;

    @GetMapping("/admin/schools")
    public ResponseEntity<ApiResponse<PageResponse<SchoolResponse>>> getSchools(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String schoolType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(schoolService.getAdminSchools(q, schoolType, page, size)));
    }

    @PostMapping("/admin/schools")
    public ResponseEntity<ApiResponse<SchoolResponse>> createSchool(
            @Valid @RequestBody SchoolCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(schoolService.create(request)));
    }

    @PostMapping("/admin/schools/{id}")
    public ResponseEntity<ApiResponse<SchoolResponse>> updateSchool(
            @PathVariable Long id,
            @Valid @RequestBody SchoolUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(schoolService.update(id, request)));
    }

    @PostMapping("/admin/schools/import")
    public ResponseEntity<ApiResponse<SchoolImportResponse>> importSchools(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(schoolImportService.importSchools(file)));
    }
}
