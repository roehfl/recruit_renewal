package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApplicationFormPageRepository extends JpaRepository<ApplicationFormPage, Long> {

    List<ApplicationFormPage> findByJobPostingIdOrderBySortOrderAscIdAsc(Long jobPostingId);

    @EntityGraph(attributePaths = {"items"})
    @Query("""
            select page
            from ApplicationFormPage page
            where page.jobPosting.id = :jobPostingId
            order by page.sortOrder asc, page.id asc
            """)
    List<ApplicationFormPage> findByJobPostingIdWithItems(@Param("jobPostingId") Long jobPostingId);

    /**
     * 여러 공고의 레이아웃 배치 현황을 한 번에 읽는다(N+1 방지).
     * 페이지 수는 pageNo 의 distinct 개수로, 배치 섹션은 sectionType 집합으로 계산한다.
     */
    @Query("""
            select new com.shinyoung.recruit.domain.repository.ApplicationFormLayoutItemView(
                page.jobPosting.id,
                page.pageNo,
                item.sectionType
            )
            from ApplicationFormPage page
            join page.items item
            where page.jobPosting.id in :jobPostingIds
            """)
    List<ApplicationFormLayoutItemView> findLayoutItemsByJobPostingIds(
            @Param("jobPostingIds") Collection<Long> jobPostingIds
    );

    boolean existsByJobPostingId(Long jobPostingId);

    void deleteByJobPostingId(Long jobPostingId);
}
