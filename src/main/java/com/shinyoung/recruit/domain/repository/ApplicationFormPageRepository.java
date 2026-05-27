package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    boolean existsByJobPostingId(Long jobPostingId);

    void deleteByJobPostingId(Long jobPostingId);
}
