package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * ClientEventLog 영속 접근(Phase 09f).
 *
 * <p><b>insert-only</b> — {@code JpaRepository} 대신 {@link Repository} 마커를 상속해 insert + 조회만
 * 노출한다({@code ActivityLogRepository} 선례). update/단건 delete를 두지 않는다. retention bulk delete는
 * 09f-4에서 {@code @Modifying @Query}로 추가한다.
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
}
