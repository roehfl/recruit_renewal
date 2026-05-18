package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StageResultRepository extends JpaRepository<StageResult, Long> {

    boolean existsByStageIdAndJobApplicationId(Long stageId, Long jobApplicationId);

    List<StageResult> findByStageId(Long stageId);

    List<StageResult> findByStageIdAndJobApplicationIdIn(Long stageId, Collection<Long> jobApplicationIds);

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
}
