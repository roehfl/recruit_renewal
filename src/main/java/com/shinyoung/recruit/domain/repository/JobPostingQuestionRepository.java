package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobPostingQuestionRepository extends JpaRepository<JobPostingQuestion, Long> {

    List<JobPostingQuestion> findByJobPostingIdOrderBySortOrderAscIdAsc(Long jobPostingId);

    List<JobPostingQuestion> findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(Long jobPostingId);

    Optional<JobPostingQuestion> findByIdAndJobPostingId(Long id, Long jobPostingId);

    boolean existsByJobPostingIdAndActiveTrueAndSortOrder(Long jobPostingId, Integer sortOrder);

    boolean existsByJobPostingIdAndActiveTrueAndSortOrderAndIdNot(Long jobPostingId, Integer sortOrder, Long id);

    boolean existsByJobPostingIdAndActiveTrue(Long jobPostingId);

    boolean existsByJobPostingIdAndActiveTrueAndRequiredTrue(Long jobPostingId);

    long countByJobPostingIdAndActiveTrue(Long jobPostingId);

    @Query("""
            select new com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount(
                question.jobPosting.id,
                count(question.id),
                sum(case when question.required = true then 1 else 0 end)
            )
            from JobPostingQuestion question
            where question.jobPosting.id in :jobPostingIds
              and question.active = true
            group by question.jobPosting.id
            """)
    List<JobPostingQuestionPolicyCount> countActiveQuestionPolicyByJobPostingIds(
            @Param("jobPostingIds") Collection<Long> jobPostingIds
    );
}
