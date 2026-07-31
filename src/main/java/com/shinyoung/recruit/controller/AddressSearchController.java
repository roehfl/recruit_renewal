package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.AddressSearchResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.service.AddressSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주소 검색(juso.go.kr 프록시). 지원자 주소 입력 보조용 public 검색이다(비민감 → permitAll).
 *
 * <p>승인키(confmKey)는 서버 설정에 보관하며 클라이언트는 keyword/페이지만 보낸다.
 * 실제 매핑 경로는 {@code /api/addresses}(전역 {@code /api} prefix).
 */
@RestController
@RequiredArgsConstructor
public class AddressSearchController {

    private final AddressSearchService addressSearchService;

    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<AddressSearchResponse>> searchAddresses(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int countPerPage
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                addressSearchService.search(keyword, currentPage, countPerPage)));
    }
}
