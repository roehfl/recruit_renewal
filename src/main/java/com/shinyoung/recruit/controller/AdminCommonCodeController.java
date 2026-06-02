package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.CommonCodeCreateRequest;
import com.shinyoung.recruit.dto.request.CommonCodeUpdateRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.CommonCodeResponse;
import com.shinyoung.recruit.service.CommonCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CommonCode admin CRUD(Phase 08a). 비활성 포함 조회 + 생성 + 수정(soft delete 포함).
 * code/groupCode 는 생성 후 불변이라 수정 대상이 아니다.
 */
@RestController
@RequiredArgsConstructor
public class AdminCommonCodeController {

    private final CommonCodeService commonCodeService;

    @GetMapping("/admin/codes")
    public ResponseEntity<ApiResponse<List<CommonCodeResponse>>> getCodes(
            @RequestParam(required = false) String groupCode
    ) {
        return ResponseEntity.ok(ApiResponse.success(commonCodeService.getAdminCodes(groupCode)));
    }

    @PostMapping("/admin/codes")
    public ResponseEntity<ApiResponse<CommonCodeResponse>> createCode(
            @Valid @RequestBody CommonCodeCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(commonCodeService.create(request)));
    }

    @PostMapping("/admin/codes/{id}")
    public ResponseEntity<ApiResponse<CommonCodeResponse>> updateCode(
            @PathVariable Long id,
            @Valid @RequestBody CommonCodeUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(commonCodeService.update(id, request)));
    }
}
