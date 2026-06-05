package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.PurgeJobItemRepository;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchResponse;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import com.shinyoung.recruit.exception.PurgeBatchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PurgeBatch 조회(Phase 09c). 09c 의 batch/item 에는 식별자·집계·reasonCode 만 있어(지원자 PII 없음)
 * RECRUIT/PRIVACY 동일 응답이다 — execute(09d) 의 실패 상세 원문이 생기면 권한별 projection 분기를 도입한다.
 * 목록은 무제한 반환 금지 — page/size 가드(9c 리뷰 Low 2, audit read API 와 동일 상한).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurgeBatchReadService {

    static final int MAX_PAGE_SIZE = 100;

    private final PurgeBatchRepository purgeBatchRepository;
    private final PurgeJobItemRepository purgeJobItemRepository;

    public PageResponse<PurgeBatchResponse> getBatches(int page, int size) {
        if (page < 0) {
            throw new InvalidRetentionRequestException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRetentionRequestException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        return PageResponse.from(purgeBatchRepository.findAllByOrderByIdDesc(PageRequest.of(page, size))
                .map(PurgeBatchResponse::from));
    }

    public PurgeBatchDetailResponse getBatch(Long batchId) {
        PurgeBatch batch = purgeBatchRepository.findById(batchId)
                .orElseThrow(() -> new PurgeBatchNotFoundException("PurgeBatch를 찾을 수 없습니다."));
        return PurgeBatchDetailResponse.of(batch, purgeJobItemRepository.findByPurgeBatchIdOrderByIdAsc(batchId));
    }
}
