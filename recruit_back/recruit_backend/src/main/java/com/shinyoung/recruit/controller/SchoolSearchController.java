package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.service.SchoolSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 학교 자동완성/검색. 지원자 학력 입력 보조용 public 검색이다(비민감 → permitAll).
 *
 * <p>외부 OpenAPI(NEIS / 대학 학과정보 표준데이터) 프록시이며, 인증키는 서버 설정에 보관하고
 * 클라이언트는 검색어와 학교 구분만 보낸다. 실제 매핑 경로는 {@code /api/schools}(전역 {@code /api} prefix).
 */
@RestController
@RequiredArgsConstructor
public class SchoolSearchController {

    private final SchoolSearchService schoolSearchService;

    @GetMapping("/schools")
    public ResponseEntity<ApiResponse<List<SchoolSearchResponse>>> searchSchools(
            @RequestParam(required = false) String q,
            @RequestParam EducationLevel educationLevel
    ) {
        return ResponseEntity.ok(ApiResponse.success(schoolSearchService.search(q, educationLevel)));
    }
}
