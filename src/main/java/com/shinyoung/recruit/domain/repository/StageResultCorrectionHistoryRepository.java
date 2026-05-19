package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.StageResultCorrectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StageResultCorrectionHistoryRepository extends JpaRepository<StageResultCorrectionHistory, Long> {

    List<StageResultCorrectionHistory> findByStageResultIdOrderByCorrectedAtDescIdDesc(Long stageResultId);
}
