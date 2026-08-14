package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.PurgeBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * PurgeBatch 영속 접근(Phase 09c). <b>delete 금지 mutable ledger</b>(설계 §5.4, 리뷰 2차 #10) —
 * {@link Repository} 마커로 save(상태 전이 update 포함) + 조회만 노출하고 delete 류를 두지 않는다.
 */
public interface PurgeBatchRepository extends Repository<PurgeBatch, Long> {

    PurgeBatch save(PurgeBatch purgeBatch);

    Optional<PurgeBatch> findById(Long id);

    /** 목록은 무제한 반환 금지 — 페이지네이션 필수(9c 리뷰 Low 2). */
    Page<PurgeBatch> findAllByOrderByIdDesc(Pageable pageable);
}
