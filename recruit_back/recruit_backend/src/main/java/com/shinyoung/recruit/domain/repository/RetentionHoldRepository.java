package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.RetentionHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RetentionHoldRepository extends JpaRepository<RetentionHold, Long> {

    List<RetentionHold> findAllByOrderByIdDesc();

    /** active hold(미해제) 전체 — retention scan 의 RETENTION_HOLD 판정용. */
    List<RetentionHold> findByReleasedAtIsNull();

    boolean existsByApplicationIdAndReleasedAtIsNull(Long applicationId);

    /**
     * 일괄 hold 존재 조회(Phase 10, 지원자 검색·상세 N+1 회피). reason 은 민감 자유 텍스트라
     * 절대 선택하지 않는다 — applicationId 만 scalar 로 가져온다.
     */
    @Query("select h.applicationId from RetentionHold h where h.applicationId in :applicationIds and h.releasedAt is null")
    Set<Long> findActiveApplicationIds(@Param("applicationIds") Collection<Long> applicationIds);
}
