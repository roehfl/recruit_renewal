package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.RetentionHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetentionHoldRepository extends JpaRepository<RetentionHold, Long> {

    List<RetentionHold> findAllByOrderByIdDesc();

    /** active hold(미해제) 전체 — retention scan 의 RETENTION_HOLD 판정용. */
    List<RetentionHold> findByReleasedAtIsNull();

    boolean existsByApplicationIdAndReleasedAtIsNull(Long applicationId);
}
