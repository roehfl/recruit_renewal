package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    Optional<JobPosition> findByIdAndJobPostingId(Long id, Long jobPostingId);

    @Query("""
            select position.jobPosting.id as jobPostingId,
                   count(position.id) as positionCount
            from JobPosition position
            where position.jobPosting.id in :jobPostingIds
            group by position.jobPosting.id
            """)
    List<JobPositionCountProjection> countByJobPostingIds(@Param("jobPostingIds") List<Long> jobPostingIds);

    @Query("""
            select position
            from JobPosition position
            join fetch position.jobPosting jobPosting
            where jobPosting.id in :jobPostingIds
            order by jobPosting.id asc, position.sortOrder asc
            """)
    List<JobPosition> findByJobPostingIdInOrderByJobPostingIdAscSortOrderAsc(
            @Param("jobPostingIds") List<Long> jobPostingIds
    );
}
