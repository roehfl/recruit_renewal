package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StageRepository extends JpaRepository<Stage, Long> {

    List<Stage> findByJobPostingIdOrderByStageOrderAscIdAsc(Long jobPostingId);

    Optional<Stage> findByIdAndJobPostingId(Long id, Long jobPostingId);

    boolean existsByJobPostingIdAndStageOrder(Long jobPostingId, Integer stageOrder);

    boolean existsByJobPostingIdAndStageOrderAndIdNot(Long jobPostingId, Integer stageOrder, Long id);

    boolean existsByJobPostingIdAndFinalStageTrue(Long jobPostingId);

    boolean existsByJobPostingIdAndFinalStageTrueAndIdNot(Long jobPostingId, Long id);
}
