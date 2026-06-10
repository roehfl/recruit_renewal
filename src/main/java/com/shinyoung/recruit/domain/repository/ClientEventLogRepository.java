package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ClientEventLog 영속 접근(Phase 09f).
 *
 * <p><b>insert-only</b> — {@code JpaRepository} 대신 {@link Repository} 마커를 상속해 insert + 조회만
 * 노출한다({@code ActivityLogRepository} 선례). update/단건 delete를 두지 않는다. retention bulk delete는
 * {@link #deleteByReceivedAtBefore}(09f-4)로 제공한다.
 *
 * <p>{@code saveAndFlush}는 중복 race 흡수에 필수다(설계 6.2, 리뷰 Major 4) — {@code save()}만 쓰면
 * unique violation이 commit 시점에 터져 service의 catch를 타지 못하고 전역 409 매핑으로 샌다.
 */
public interface ClientEventLogRepository extends Repository<ClientEventLog, Long> {

    ClientEventLog save(ClientEventLog clientEventLog);

    ClientEventLog saveAndFlush(ClientEventLog clientEventLog);

    Optional<ClientEventLog> findById(Long id);

    long count();

    boolean existsByClientSessionIdAndClientEventId(String clientSessionId, String clientEventId);

    /** retention cleanup 전용 bulk delete(09f-4). 엔티티 로딩 없이 단일 DELETE 문으로 삭제 건수를 반환한다. */
    @Modifying
    @Query("DELETE FROM ClientEventLog c WHERE c.receivedAt < :threshold")
    int deleteByReceivedAtBefore(@Param("threshold") LocalDateTime threshold);

    /** client event 검색(09f-3 read API). receivedAt 범위는 필수(가드는 서비스에서). 최신순 고정 정렬. */
    @Query("""
            SELECT c FROM ClientEventLog c
            WHERE c.receivedAt >= :from AND c.receivedAt <= :to
              AND (:eventType IS NULL OR c.eventType = :eventType)
              AND (:severity IS NULL OR c.severity = :severity)
              AND (:source IS NULL OR c.source = :source)
              AND (:applicationId IS NULL OR c.applicationId = :applicationId)
              AND (:jobPostingId IS NULL OR c.jobPostingId = :jobPostingId)
              AND (:clientSessionId IS NULL OR c.clientSessionId = :clientSessionId)
              AND (:relatedCorrelationId IS NULL OR c.relatedCorrelationId = :relatedCorrelationId)
            ORDER BY c.receivedAt DESC, c.id DESC
            """)
    Page<ClientEventLog> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("eventType") ClientEventType eventType,
            @Param("severity") ClientEventSeverity severity,
            @Param("source") ClientEventSource source,
            @Param("applicationId") Long applicationId,
            @Param("jobPostingId") Long jobPostingId,
            @Param("clientSessionId") String clientSessionId,
            @Param("relatedCorrelationId") String relatedCorrelationId,
            Pageable pageable
    );
}
