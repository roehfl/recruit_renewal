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

    Optional<JobPostingQuestion> findByIdAndJobPostingId(Long id, Long jobPostingId);

    boolean existsByJobPostingIdAndSortOrder(Long jobPostingId, Integer sortOrder);

    boolean existsByJobPostingIdAndSortOrderAndIdNot(Long jobPostingId, Integer sortOrder, Long id);

    boolean existsByJobPostingId(Long jobPostingId);

    boolean existsByJobPostingIdAndRequiredTrue(Long jobPostingId);

    long countByJobPostingId(Long jobPostingId);

    @Query("""
            select new com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount(
                question.jobPosting.id,
                count(question.id),
                sum(case when question.required = true then 1 else 0 end)
            )
            from JobPostingQuestion question
            where question.jobPosting.id in :jobPostingIds
            group by question.jobPosting.id
            """)
    List<JobPostingQuestionPolicyCount> countQuestionPolicyByJobPostingIds(
            @Param("jobPostingIds") Collection<Long> jobPostingIds
    );
}
