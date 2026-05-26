package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByJobPostingIdOrderByStartDateTimeAsc(Long jobPostingId);

    List<Interview> findByJobPostingIdAndStageIdOrderByStartDateTimeAsc(Long jobPostingId, Long stageId);

    List<Interview> findByJobPostingIdAndStatusOrderByStartDateTimeAsc(Long jobPostingId, InterviewStatus status);

    @Query("""
            select interview
            from Interview interview
            join fetch interview.jobPosting jobPosting
            join fetch interview.stage stage
            where jobPosting.id = :jobPostingId
              and (:stageId is null or stage.id = :stageId)
              and (:status is null or interview.status = :status)
              and (:from is null or interview.endDateTime > :from)
              and (:to is null or interview.startDateTime < :to)
            order by interview.startDateTime asc, interview.id asc
            """)
    List<Interview> searchAdminInterviews(
            @Param("jobPostingId") Long jobPostingId,
            @Param("stageId") Long stageId,
            @Param("status") InterviewStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select interview
            from Interview interview
            join fetch interview.jobPosting
            join fetch interview.stage
            where interview.id = :interviewId
            """)
    Optional<Interview> findAdminDetailById(@Param("interviewId") Long interviewId);
}
