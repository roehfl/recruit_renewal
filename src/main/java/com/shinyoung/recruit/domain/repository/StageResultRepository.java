package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.dto.response.FunnelStageResultRow;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StageResultRepository extends JpaRepository<StageResult, Long> {

    @Query("""
            select new com.shinyoung.recruit.dto.response.FunnelStageResultRow(
                result.jobApplication.id,
                result.stage.id,
                result.resultStatus,
                result.decidedAt)
            from StageResult result
            where result.stage.jobPosting.id = :jobPostingId
              and result.jobApplication.submittedAt is not null
            """)
    List<FunnelStageResultRow> findFunnelStageResults(@Param("jobPostingId") Long jobPostingId);

    boolean existsByStageIdAndJobApplicationId(Long stageId, Long jobApplicationId);

    List<StageResult> findByStageId(Long stageId);

    List<StageResult> findByStageIdAndJobApplicationIdIn(Long stageId, Collection<Long> jobApplicationIds);

    Optional<StageResult> findByStageIdAndJobApplicationId(Long stageId, Long jobApplicationId);

    Optional<StageResult> findByIdAndStageId(Long id, Long stageId);

    List<StageResult> findByStageIdAndIdIn(Long stageId, Collection<Long> ids);

    boolean existsByStageIdAndResultStatus(Long stageId, StageResultStatus status);

    long countByStageId(Long stageId);

    long countByStageIdAndResultStatus(Long stageId, StageResultStatus resultStatus);

    @Query("""
            select result
            from StageResult result
            join fetch result.stage stage
            join fetch result.jobApplication application
            join fetch application.applicant applicant
            join fetch application.jobPosition jobPosition
            where stage.id = :stageId
            order by application.submittedAt desc, application.id desc
            """)
    List<StageResult> findByStageIdForAdminList(@Param("stageId") Long stageId);

    @Query("""
            select result
            from StageResult result
            join fetch result.stage stage
            where result.jobApplication.id = :jobApplicationId
              and stage.id in :stageIds
            """)
    List<StageResult> findByJobApplicationIdAndStageIdInForTimeline(
            @Param("jobApplicationId") Long jobApplicationId,
            @Param("stageIds") Collection<Long> stageIds
    );

    default List<StageResult> findVisibleByJobApplicationIdForApplicant(Long jobApplicationId) {
        return findVisibleByJobApplicationIdForApplicant(
                jobApplicationId,
                List.of(StageStatus.RESULT_ANNOUNCED, StageStatus.CLOSED)
        );
    }

    @Query("""
            select result
            from StageResult result
            join fetch result.stage stage
            where result.jobApplication.id = :jobApplicationId
              and stage.status in :visibleStatuses
            order by stage.stageOrder asc, stage.id asc
            """)
    List<StageResult> findVisibleByJobApplicationIdForApplicant(
            @Param("jobApplicationId") Long jobApplicationId,
            @Param("visibleStatuses") Collection<StageStatus> visibleStatuses
    );

    default List<StageResult> findVisibleByJobApplicationIdsForApplicantSummary(Collection<Long> jobApplicationIds) {
        return findVisibleByJobApplicationIdsForApplicantSummary(
                jobApplicationIds,
                List.of(StageStatus.RESULT_ANNOUNCED, StageStatus.CLOSED)
        );
    }

    @Query("""
            select result
            from StageResult result
            join fetch result.stage stage
            join fetch result.jobApplication application
            where application.id in :jobApplicationIds
              and stage.status in :visibleStatuses
            order by application.id asc, stage.stageOrder asc, stage.id asc
            """)
    List<StageResult> findVisibleByJobApplicationIdsForApplicantSummary(
            @Param("jobApplicationIds") Collection<Long> jobApplicationIds,
            @Param("visibleStatuses") Collection<StageStatus> visibleStatuses
    );
}
