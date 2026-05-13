package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPosting;
<<<<<<< codex/verify-codex-cloud-build-and-test-setup-job4wm
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    Page<JobPosting> findAllByOrderByCreatedAtDesc(Pageable pageable);
=======
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    List<JobPosting> findAllByOrderByCreatedAtDesc();
>>>>>>> main
}
