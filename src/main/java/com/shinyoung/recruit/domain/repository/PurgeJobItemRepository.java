package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.PurgeJobItem;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * PurgeJobItem 영속 접근(Phase 09c). <b>delete 금지 mutable ledger</b> — save/saveAll + 조회만 노출.
 */
public interface PurgeJobItemRepository extends Repository<PurgeJobItem, Long> {

    PurgeJobItem save(PurgeJobItem purgeJobItem);

    List<PurgeJobItem> saveAll(Iterable<PurgeJobItem> purgeJobItems);

    List<PurgeJobItem> findByPurgeBatchIdOrderByIdAsc(Long purgeBatchId);
}
