package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Faq;
import com.shinyoung.recruit.domain.entity.FaqCategory;
import com.shinyoung.recruit.domain.repository.FaqCategoryRepository;
import com.shinyoung.recruit.domain.repository.FaqRepository;
import com.shinyoung.recruit.dto.request.FaqCategoryReorderRequest;
import com.shinyoung.recruit.dto.request.FaqCategorySaveRequest;
import com.shinyoung.recruit.dto.request.FaqReorderRequest;
import com.shinyoung.recruit.dto.request.FaqSaveRequest;
import com.shinyoung.recruit.dto.response.FaqCategoryResponse;
import com.shinyoung.recruit.dto.response.FaqResponse;
import com.shinyoung.recruit.dto.response.PublicFaqCategoryResponse;
import com.shinyoung.recruit.dto.response.PublicFaqResponse;
import com.shinyoung.recruit.exception.FaqNotFoundException;
import com.shinyoung.recruit.exception.InvalidFaqException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FAQ 관리(카테고리 + 질문/답변). 공개 조회는 활성 항목만, 관리자는 비활성 포함 CRUD 다.
 *
 * <p>정렬값({@code sortOrder})은 요청으로 직접 받지 않는다. 생성 시 스코프 최대값+1 로 자동 부여하고,
 * 변경은 reorder API 로만 하며 배열 순서대로 0..n-1 로 정규화한다.
 * 삭제는 {@code active=false} soft delete 다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqCategoryRepository faqCategoryRepository;
    private final FaqRepository faqRepository;

    /* ===================== 공개 조회 ===================== */

    /**
     * 지원자 화면용 전체 조회. 활성 카테고리 &times; 활성 FAQ 만 정렬 순으로 묶어 반환한다.
     * 노출 가능한 FAQ 가 0건인 카테고리는 결과에서 제외한다.
     */
    public List<PublicFaqCategoryResponse> getPublicFaqs() {
        Map<FaqCategory, List<PublicFaqResponse>> grouped = new LinkedHashMap<>();
        for (Faq faq : faqRepository.findVisibleFaqs()) {
            grouped.computeIfAbsent(faq.getCategory(), key -> new ArrayList<>())
                    .add(PublicFaqResponse.from(faq));
        }
        return grouped.entrySet().stream()
                .map(entry -> PublicFaqCategoryResponse.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    /* ===================== 관리자: 카테고리 ===================== */

    public List<FaqCategoryResponse> getCategories() {
        return faqCategoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(category -> FaqCategoryResponse.from(category, faqRepository.countByCategoryAndActiveTrue(category)))
                .toList();
    }

    @Transactional
    public FaqCategoryResponse createCategory(FaqCategorySaveRequest request) {
        String name = request.name().trim();
        if (faqCategoryRepository.existsByName(name)) {
            throw new InvalidFaqException("이미 존재하는 카테고리명입니다. name=" + name);
        }
        FaqCategory saved = faqCategoryRepository.save(
                FaqCategory.create(name, nextCategorySortOrder(), request.active()));
        return FaqCategoryResponse.from(saved, 0);
    }

    @Transactional
    public FaqCategoryResponse updateCategory(Long categoryId, FaqCategorySaveRequest request) {
        FaqCategory category = getCategoryOrThrow(categoryId);
        String name = request.name().trim();
        // 자기 자신은 중복이 아니다.
        faqCategoryRepository.findByName(name)
                .filter(other -> !other.getId().equals(categoryId))
                .ifPresent(other -> {
                    throw new InvalidFaqException("이미 존재하는 카테고리명입니다. name=" + name);
                });
        category.update(name, request.active());
        return FaqCategoryResponse.from(category, faqRepository.countByCategoryAndActiveTrue(category));
    }

    /** soft delete. 이미 비활성이면 멱등 통과한다. 하위 FAQ 의 active 는 건드리지 않는다. */
    @Transactional
    public void deleteCategory(Long categoryId) {
        getCategoryOrThrow(categoryId).deactivate();
    }

    @Transactional
    public void reorderCategories(FaqCategoryReorderRequest request) {
        List<FaqCategory> categories = faqCategoryRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, FaqCategory> byId = new LinkedHashMap<>();
        categories.forEach(category -> byId.put(category.getId(), category));
        validateReorderIds(request.ids(), byId.keySet(), "카테고리");

        for (int index = 0; index < request.ids().size(); index++) {
            byId.get(request.ids().get(index)).changeSortOrder(index);
        }
    }

    /* ===================== 관리자: FAQ ===================== */

    public List<FaqResponse> getFaqs(Long categoryId) {
        FaqCategory category = getCategoryOrThrow(categoryId);
        return faqRepository.findByCategoryOrderBySortOrderAscIdAsc(category).stream()
                .map(FaqResponse::from)
                .toList();
    }

    @Transactional
    public FaqResponse createFaq(FaqSaveRequest request) {
        FaqCategory category = getCategoryOrThrow(request.categoryId());
        Faq saved = faqRepository.save(Faq.create(
                category,
                request.question(),
                request.answer(),
                nextFaqSortOrder(category),
                request.active()));
        return FaqResponse.from(saved);
    }

    /** 카테고리를 옮기면 대상 카테고리 최대값+1 로 sortOrder 를 재부여한다(기존 순서 유지 불가). */
    @Transactional
    public FaqResponse updateFaq(Long faqId, FaqSaveRequest request) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new FaqNotFoundException("FAQ를 찾을 수 없습니다. id=" + faqId));
        FaqCategory category = getCategoryOrThrow(request.categoryId());
        boolean moved = !faq.getCategory().getId().equals(category.getId());

        faq.update(
                category,
                request.question(),
                request.answer(),
                request.active(),
                moved ? nextFaqSortOrder(category) : null);
        return FaqResponse.from(faq);
    }

    /** soft delete. 이미 비활성이면 멱등 통과한다. */
    @Transactional
    public void deleteFaq(Long faqId) {
        faqRepository.findById(faqId)
                .orElseThrow(() -> new FaqNotFoundException("FAQ를 찾을 수 없습니다. id=" + faqId))
                .deactivate();
    }

    @Transactional
    public void reorderFaqs(FaqReorderRequest request) {
        FaqCategory category = getCategoryOrThrow(request.categoryId());
        Map<Long, Faq> byId = new LinkedHashMap<>();
        faqRepository.findByCategoryOrderBySortOrderAscIdAsc(category)
                .forEach(faq -> byId.put(faq.getId(), faq));
        validateReorderIds(request.ids(), byId.keySet(), "FAQ");

        for (int index = 0; index < request.ids().size(); index++) {
            byId.get(request.ids().get(index)).changeSortOrder(index);
        }
    }

    /* ===================== 내부 ===================== */

    private FaqCategory getCategoryOrThrow(Long categoryId) {
        return faqCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new FaqNotFoundException("FAQ 카테고리를 찾을 수 없습니다. id=" + categoryId));
    }

    private int nextCategorySortOrder() {
        return faqCategoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .mapToInt(FaqCategory::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private int nextFaqSortOrder(FaqCategory category) {
        return faqRepository.findByCategoryOrderBySortOrderAscIdAsc(category).stream()
                .mapToInt(Faq::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    /**
     * reorder 요청의 id 집합이 대상 전체와 정확히 일치해야 한다.
     * 부분 정렬을 허용하면 남은 항목의 sortOrder 가 어긋나 화면 순서가 깨진다.
     */
    private void validateReorderIds(List<Long> requestIds, Set<Long> targetIds, String label) {
        Set<Long> distinct = new HashSet<>(requestIds);
        if (distinct.size() != requestIds.size() || !distinct.equals(targetIds)) {
            throw new InvalidFaqException(label + " 정렬 목록이 전체 대상과 일치하지 않습니다.");
        }
    }
}
