package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Page<JobPosting> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    @Query("select jobPosting from JobPosting jobPosting where jobPosting.id = :id")
    Optional<JobPosting> findDetailById(@Param("id") Long id);

    Page<JobPostingPublicListProjection> findAllByStatusOrderByCreatedAtDesc(JobPostingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    Optional<JobPosting> findByIdAndStatus(Long id, JobPostingStatus status);
}
