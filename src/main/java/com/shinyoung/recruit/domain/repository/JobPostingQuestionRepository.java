package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingQuestionRepository extends JpaRepository<JobPostingQuestion, Long> {

    List<JobPostingQuestion> findByJobPostingIdOrderBySortOrderAscIdAsc(Long jobPostingId);

    List<JobPostingQuestion> findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(Long jobPostingId);

    Optional<JobPostingQuestion> findByIdAndJobPostingId(Long id, Long jobPostingId);

    boolean existsByJobPostingIdAndActiveTrueAndSortOrder(Long jobPostingId, Integer sortOrder);

    boolean existsByJobPostingIdAndActiveTrueAndSortOrderAndIdNot(Long jobPostingId, Integer sortOrder, Long id);

    long countByJobPostingIdAndActiveTrue(Long jobPostingId);
}
