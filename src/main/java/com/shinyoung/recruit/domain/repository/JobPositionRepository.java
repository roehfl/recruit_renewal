package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    Optional<JobPosition> findByIdAndJobPostingId(Long id, Long jobPostingId);
}
