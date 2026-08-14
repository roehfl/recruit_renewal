package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPostingImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingImageRepository extends JpaRepository<JobPostingImage, Long> {

    List<JobPostingImage> findByJobPostingIdOrderBySortOrderAscIdAsc(Long jobPostingId);

    Optional<JobPostingImage> findByIdAndJobPostingId(Long id, Long jobPostingId);

    long countByJobPostingId(Long jobPostingId);
}
