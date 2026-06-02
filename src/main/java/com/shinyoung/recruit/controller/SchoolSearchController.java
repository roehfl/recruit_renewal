package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * School 자동완성/검색(Phase 08b). 지원자 학력 입력 보조용 public 검색이다(비민감 → permitAll).
 * 활성 학교만 prefix 우선 + contains 로 top-N 반환한다.
 */
@RestController
@RequiredArgsConstructor
public class SchoolSearchController {

    private final SchoolService schoolService;

    @GetMapping("/schools")
    public ResponseEntity<ApiResponse<List<SchoolSearchResponse>>> searchSchools(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String schoolType
    ) {
        return ResponseEntity.ok(ApiResponse.success(schoolService.search(q, schoolType)));
    }
}
