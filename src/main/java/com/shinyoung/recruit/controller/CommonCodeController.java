package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.CommonCodeResponse;
import com.shinyoung.recruit.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CommonCode public read(Phase 08a). 프론트 드롭다운 소스. 비민감 라벨이라 공개(permitAll)다.
 * 활성 코드만 sortOrder 순으로 반환한다.
 */
@RestController
@RequiredArgsConstructor
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<CommonCodeResponse>>> getCodes(
            @RequestParam String groupCode
    ) {
        return ResponseEntity.ok(ApiResponse.success(commonCodeService.getActiveCodes(groupCode)));
    }
}
