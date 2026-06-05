package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.PurgeJobItemRepository;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchResponse;
import com.shinyoung.recruit.exception.PurgeBatchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PurgeBatch 조회(Phase 09c). 09c 의 batch/item 에는 식별자·집계·reasonCode 만 있어(지원자 PII 없음)
 * RECRUIT/PRIVACY 동일 응답이다 — execute(09d) 의 실패 상세 원문이 생기면 권한별 projection 분기를 도입한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurgeBatchReadService {

    private final PurgeBatchRepository purgeBatchRepository;
    private final PurgeJobItemRepository purgeJobItemRepository;

    public List<PurgeBatchResponse> getBatches() {
        return purgeBatchRepository.findAllByOrderByIdDesc().stream()
                .map(PurgeBatchResponse::from)
                .toList();
    }

    public PurgeBatchDetailResponse getBatch(Long batchId) {
        PurgeBatch batch = purgeBatchRepository.findById(batchId)
                .orElseThrow(() -> new PurgeBatchNotFoundException("PurgeBatch를 찾을 수 없습니다."));
        return PurgeBatchDetailResponse.of(batch, purgeJobItemRepository.findByPurgeBatchIdOrderByIdAsc(batchId));
    }
}
