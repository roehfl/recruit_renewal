package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ActivityLog 영속 접근(Phase 09a).
 *
 * <p><b>append-only</b> — {@code JpaRepository} 대신 {@link Repository} 마커를 상속해 <b>insert(save) + 조회만</b>
 * 노출한다. delete/update/saveAll(갱신) 류 메서드를 의도적으로 두지 않는다(감사 증적 불변성). 조회 finder 는
 * read API(09b)가 사용한다.
 */
public interface ActivityLogRepository extends Repository<ActivityLog, Long> {

    ActivityLog save(ActivityLog activityLog);

    Optional<ActivityLog> findById(Long id);

    long count();

    /**
     * 감사 로그 검색(09b read API). occurredAt 범위는 필수(가드는 서비스에서), 나머지 필터는 null 허용.
     * 최신순 고정 정렬.
     */
    @Query("""
            SELECT a FROM ActivityLog a
            WHERE a.occurredAt >= :from AND a.occurredAt <= :to
              AND (:actorId IS NULL OR a.actorId = :actorId)
              AND (:actionType IS NULL OR a.actionType = :actionType)
              AND (:actionResult IS NULL OR a.actionResult = :actionResult)
              AND (:targetType IS NULL OR a.targetType = :targetType)
              AND (:jobPostingId IS NULL OR a.jobPostingId = :jobPostingId)
              AND (:applicationId IS NULL OR a.applicationId = :applicationId)
            ORDER BY a.occurredAt DESC, a.id DESC
            """)
    Page<ActivityLog> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("actorId") String actorId,
            @Param("actionType") AuditActionType actionType,
            @Param("actionResult") AuditActionResult actionResult,
            @Param("targetType") AuditTargetType targetType,
            @Param("jobPostingId") Long jobPostingId,
            @Param("applicationId") Long applicationId,
            Pageable pageable
    );
}
