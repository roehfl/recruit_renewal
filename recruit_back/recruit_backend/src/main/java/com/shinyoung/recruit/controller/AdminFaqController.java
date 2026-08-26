package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.FaqCategoryReorderRequest;
import com.shinyoung.recruit.dto.request.FaqCategorySaveRequest;
import com.shinyoung.recruit.dto.request.FaqReorderRequest;
import com.shinyoung.recruit.dto.request.FaqSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.FaqCategoryResponse;
import com.shinyoung.recruit.dto.response.FaqResponse;
import com.shinyoung.recruit.service.FaqService;
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
 * FAQ 관리자 CRUD. 권한은 SecurityConfig 의 {@code /api/admin/**} 매처(ADMIN / RECRUIT_ADMIN)가 건다.
 *
 * <p>CORS 허용 메서드가 GET/POST 뿐이라 수정·삭제·정렬도 모두 POST 를 쓴다.
 * 삭제는 {@code /delete} 로 soft delete 한다.
 */
@RestController
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService faqService;

    /* ===================== 카테고리 ===================== */

    @GetMapping("/admin/faq-categories")
    public ResponseEntity<ApiResponse<List<FaqCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(faqService.getCategories()));
    }

    @PostMapping("/admin/faq-categories")
    public ResponseEntity<ApiResponse<FaqCategoryResponse>> createCategory(
            @Valid @RequestBody FaqCategorySaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(faqService.createCategory(request)));
    }

    @PostMapping("/admin/faq-categories/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderCategories(
            @Valid @RequestBody FaqCategoryReorderRequest request
    ) {
        faqService.reorderCategories(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/admin/faq-categories/{categoryId}")
    public ResponseEntity<ApiResponse<FaqCategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody FaqCategorySaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(faqService.updateCategory(categoryId, request)));
    }

    @PostMapping("/admin/faq-categories/{categoryId}/delete")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long categoryId) {
        faqService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /* ===================== FAQ ===================== */

    @GetMapping("/admin/faqs")
    public ResponseEntity<ApiResponse<List<FaqResponse>>> getFaqs(@RequestParam Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(faqService.getFaqs(categoryId)));
    }

    @PostMapping("/admin/faqs")
    public ResponseEntity<ApiResponse<FaqResponse>> createFaq(@Valid @RequestBody FaqSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(faqService.createFaq(request)));
    }

    @PostMapping("/admin/faqs/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderFaqs(@Valid @RequestBody FaqReorderRequest request) {
        faqService.reorderFaqs(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/admin/faqs/{faqId}")
    public ResponseEntity<ApiResponse<FaqResponse>> updateFaq(
            @PathVariable Long faqId,
            @Valid @RequestBody FaqSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(faqService.updateFaq(faqId, request)));
    }

    @PostMapping("/admin/faqs/{faqId}/delete")
    public ResponseEntity<ApiResponse<Void>> deleteFaq(@PathVariable Long faqId) {
        faqService.deleteFaq(faqId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
