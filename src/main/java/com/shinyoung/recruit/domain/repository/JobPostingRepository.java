package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    Page<JobPosting> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
