package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ActivityLog;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * ActivityLog 영속 접근(Phase 09a).
 *
 * <p><b>append-only</b> — {@code JpaRepository} 대신 {@link Repository} 마커를 상속해 <b>insert(save) + 조회만</b>
 * 노출한다. delete/update/saveAll(갱신) 류 메서드를 의도적으로 두지 않는다(감사 증적 불변성). 조회 finder 는
 * read API(09b)에서 확장한다.
 */
public interface ActivityLogRepository extends Repository<ActivityLog, Long> {

    ActivityLog save(ActivityLog activityLog);

    Optional<ActivityLog> findById(Long id);

    long count();
}
