package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Faq;
import com.shinyoung.recruit.domain.entity.FaqCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findByCategoryOrderBySortOrderAscIdAsc(FaqCategory category);

    /**
     * 공개 조회: 활성 카테고리의 활성 FAQ 전체를 카테고리 정렬 순으로 한 번에 읽는다.
     * fetch join 으로 카테고리별 추가 조회(N+1)를 막는다.
     */
    @Query("""
            select f from Faq f
            join fetch f.category c
            where f.active = true and c.active = true
            order by c.sortOrder asc, c.id asc, f.sortOrder asc, f.id asc
            """)
    List<Faq> findVisibleFaqs();

    int countByCategoryAndActiveTrue(FaqCategory category);
}
