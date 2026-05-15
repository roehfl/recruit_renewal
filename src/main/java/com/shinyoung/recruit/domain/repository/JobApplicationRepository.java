package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByIdAndApplicantId(Long id, Long applicantId);

    Optional<JobApplication> findByApplicantIdAndJobPostingId(Long applicantId, Long jobPostingId);

    boolean existsByApplicantIdAndJobPostingId(Long applicantId, Long jobPostingId);

    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    @Query("""
            select application
            from JobApplication application
            where (:jobPostingId is null or application.jobPosting.id = :jobPostingId)
              and (:jobPositionId is null or application.jobPosition.id = :jobPositionId)
              and (:status is null or application.status = :status)
            """)
    Page<JobApplication> searchForAdmin(
            @Param("jobPostingId") Long jobPostingId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("status") JobApplicationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    @Query("""
            select application
            from JobApplication application
            where application.jobPosting.id = :jobPostingId
              and (:jobPositionId is null or application.jobPosition.id = :jobPositionId)
              and (:status is null or application.status = :status)
            """)
    Page<JobApplication> searchByJobPostingForAdmin(
            @Param("jobPostingId") Long jobPostingId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("status") JobApplicationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    @Query("select application from JobApplication application where application.id = :applicationId")
    Optional<JobApplication> findAdminDetailById(@Param("applicationId") Long applicationId);
}
