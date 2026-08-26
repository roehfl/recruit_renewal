package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Faq;
import com.shinyoung.recruit.domain.entity.FaqCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FaqRepositoryTest {

    @Autowired
    private FaqCategoryRepository faqCategoryRepository;

    @Autowired
    private FaqRepository faqRepository;

    @Test
    void findVisibleFaqs_는_활성_카테고리의_활성_FAQ만_정렬순으로_반환한다() {
        FaqCategory second = faqCategoryRepository.save(FaqCategory.create("채용 절차", 1, true));
        FaqCategory first = faqCategoryRepository.save(FaqCategory.create("지원서 관련", 0, true));
        FaqCategory hidden = faqCategoryRepository.save(FaqCategory.create("비노출 카테고리", 2, false));

        faqRepository.save(Faq.create(first, "질문 A2", "답변", 1, true));
        faqRepository.save(Faq.create(first, "질문 A1", "답변", 0, true));
        faqRepository.save(Faq.create(first, "숨긴 질문", "답변", 2, false));
        faqRepository.save(Faq.create(second, "질문 B1", "답변", 0, true));
        faqRepository.save(Faq.create(hidden, "비노출 카테고리 질문", "답변", 0, true));

        assertThat(faqRepository.findVisibleFaqs())
                .extracting(Faq::getQuestion)
                .containsExactly("질문 A1", "질문 A2", "질문 B1");
    }

    @Test
    void countByCategoryAndActiveTrue_는_활성_FAQ만_센다() {
        FaqCategory category = faqCategoryRepository.save(FaqCategory.create("면접 전형", 0, true));
        faqRepository.save(Faq.create(category, "질문 1", "답변", 0, true));
        faqRepository.save(Faq.create(category, "질문 2", "답변", 1, false));

        assertThat(faqRepository.countByCategoryAndActiveTrue(category)).isEqualTo(1);
    }

    @Test
    void findAllByOrderBySortOrderAscIdAsc_는_비활성_카테고리도_포함한다() {
        faqCategoryRepository.save(FaqCategory.create("두번째", 1, true));
        faqCategoryRepository.save(FaqCategory.create("첫번째", 0, false));

        assertThat(faqCategoryRepository.findAllByOrderBySortOrderAscIdAsc())
                .extracting(FaqCategory::getName)
                .containsExactly("첫번째", "두번째");
    }
}
