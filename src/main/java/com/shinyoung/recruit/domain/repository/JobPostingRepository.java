package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Page<JobPosting> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    @Query("select jobPosting from JobPosting jobPosting where jobPosting.id = :id")
    Optional<JobPosting> findDetailById(@Param("id") Long id);

    @Query("""
            select jobPosting.id as id,
                   jobPosting.title as title,
                   jobPosting.postingType as postingType,
                   jobPosting.summary as summary,
                   jobPosting.receptionStartDateTime as receptionStartDateTime,
                   jobPosting.receptionEndDateTime as receptionEndDateTime,
                   jobPosting.pinned as pinned
            from JobPosting jobPosting
            where jobPosting.status = :status
              and jobPosting.visible = true
              and (jobPosting.displayStartDateTime is null or jobPosting.displayStartDateTime <= :now)
              and (jobPosting.displayEndDateTime is null or jobPosting.displayEndDateTime >= :now)
            order by jobPosting.pinned desc,
                     case
                         when :now between jobPosting.receptionStartDateTime and jobPosting.receptionEndDateTime then 1
                         when :now < jobPosting.receptionStartDateTime then 2
                         else 3
                     end asc,
                     jobPosting.displayOrder asc,
                     jobPosting.receptionEndDateTime asc,
                     jobPosting.publishedAt desc,
                     jobPosting.id desc
            """)
    Page<JobPostingPublicListProjection> findPublicList(
            @Param("status") JobPostingStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    Optional<JobPosting> findByIdAndStatus(Long id, JobPostingStatus status);

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    @Query("""
            select jobPosting
            from JobPosting jobPosting
            where jobPosting.id = :id
              and jobPosting.status = :status
              and jobPosting.visible = true
              and (jobPosting.displayStartDateTime is null or jobPosting.displayStartDateTime <= :now)
              and (jobPosting.displayEndDateTime is null or jobPosting.displayEndDateTime >= :now)
            """)
    Optional<JobPosting> findPublicDetailById(
            @Param("id") Long id,
            @Param("status") JobPostingStatus status,
            @Param("now") LocalDateTime now
    );
}
