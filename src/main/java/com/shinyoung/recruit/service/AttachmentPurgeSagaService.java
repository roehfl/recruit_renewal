package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ApplicationPiiPurgeRepository;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 첨부 바이너리 삭제 saga 의 ②③ 단계(Phase 09d-2, 설계 §5.4).
 *
 * <p>① (item 트랜잭션, {@link PurgeItemProcessor#process}): 첨부 PII 제거 + BINARY_DELETE_PENDING + PURGE_PENDING 커밋.
 * <br>② (트랜잭션 밖, 본 서비스): {@code deleteIfExists} 멱등 물리 삭제 + <b>존재 재확인</b>. 이미 없음 = 성공
 * (MISSING_AS_SUCCESS). <br>③ ({@link PurgeItemProcessor#finalizeBinaryDeletion}, REQUIRES_NEW): 소멸 확인 →
 * BINARY_DELETED(storagePath null)·전부 소멸 시 JobApplication/item <b>최종 PURGED 승격</b>, 실패 →
 * BINARY_DELETE_FAILED 유지(9e reconciliation 대상).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentPurgeSagaService {

    private final ApplicationPiiPurgeRepository applicationPiiPurgeRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final PurgeItemProcessor purgeItemProcessor;

    /**
     * saga ②③ 실행 — item 트랜잭션(①) 커밋 후 호출된다.
     *
     * @return 최종 PURGED 승격 여부(false = 바이너리 삭제 실패 잔존 — batch 는 PARTIAL_FAILED 로 집계)
     */
    public boolean completeBinaryDeletion(Long batchId, Long applicationId) {
        List<Object[]> targets = applicationPiiPurgeRepository.findBinaryDeleteTargets(applicationId, List.of(
                PhysicalFileStatus.BINARY_DELETE_PENDING, PhysicalFileStatus.BINARY_DELETE_FAILED));

        Set<Long> succeeded = new HashSet<>();
        Set<Long> failed = new HashSet<>();
        for (Object[] row : targets) {
            Long attachmentId = (Long) row[0];
            String storagePath = (String) row[1];
            if (deletePhysicalFile(applicationId, attachmentId, storagePath)) {
                succeeded.add(attachmentId);
            } else {
                failed.add(attachmentId);
            }
        }

        return purgeItemProcessor.finalizeBinaryDeletion(batchId, applicationId, succeeded, failed);
    }

    /** ② 멱등 물리 삭제 + 존재 재확인. true = 소멸 확인(이미 없음 포함). */
    private boolean deletePhysicalFile(Long applicationId, Long attachmentId, String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            // 경로가 이미 없으면 삭제할 바이너리도 없다(방어 — 정상 흐름에선 BINARY_DELETED 에서만 null).
            return true;
        }
        try {
            AttachmentStorageDeleteResult result = attachmentStorageService.deleteIfExistsWithResult(storagePath);
            if (result.failed()) {
                log.warn("Purge binary delete failed. applicationId={}, attachmentId={}, failureCode={}",
                        applicationId, attachmentId, result.failureCode());
                return false;
            }
            // 소멸 재확인(설계 §5.4) — deleteIfExists 성공이어도 잔존하면 PURGED 승격 금지.
            boolean stillExists = attachmentStorageService.exists(storagePath);
            if (stillExists) {
                log.warn("Purge binary still exists after delete. applicationId={}, attachmentId={}",
                        applicationId, attachmentId);
            }
            return !stillExists;
        } catch (RuntimeException e) {
            log.warn("Purge binary delete threw. applicationId={}, attachmentId={}", applicationId, attachmentId, e);
            return false;
        }
    }
}
