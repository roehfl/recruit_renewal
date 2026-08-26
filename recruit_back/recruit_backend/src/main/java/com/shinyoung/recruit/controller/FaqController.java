package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.PublicFaqCategoryResponse;
import com.shinyoung.recruit.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 지원자 FAQ 공개 조회. 인증 불필요이며 페이징 없이 전체를 한 번에 반환한다.
 */
@RestController
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/faqs")
    public ResponseEntity<ApiResponse<List<PublicFaqCategoryResponse>>> getFaqs() {
        return ResponseEntity.ok(ApiResponse.success(faqService.getPublicFaqs()));
    }
}
