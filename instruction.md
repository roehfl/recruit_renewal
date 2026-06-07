Major 1 — storagePath null/blank인 삭제 대상이 “성공” 처리된다

AttachmentPurgeSagaService.deletePhysicalFile()에서 storagePath == null || blank이면 true를 반환한다.

문제는 이 메서드가 BINARY_DELETE_PENDING / BINARY_DELETE_FAILED 대상에 대해 호출된다는 점이다.
그 결과, 어떤 이유로든 BINARY_DELETE_PENDING 상태인데 storagePath가 null/blank인 row가 있으면 “삭제 성공”으로 분류되고, finalizeBinaryDeletion()이 allCleared로 판단해 JobApplication.markPurged()와 PurgeJobItem.promoteToPurged()까지 진행할 수 있다.

이건 “소멸 확인 후에만 PURGED” 원칙에 어긋난다. BINARY_DELETED가 아닌 삭제 대상 row에서 storagePath가 없다는 것은 성공이 아니라 데이터 불일치/재처리 대상으로 봐야 한다.

수정 권장:

private boolean deletePhysicalFile(Long applicationId, Long attachmentId, String storagePath) {
    if (storagePath == null || storagePath.isBlank()) {
        log.warn("Purge binary delete target has empty storagePath. applicationId={}, attachmentId={}",
                applicationId, attachmentId);
        return false;
    }
    ...
}

정상적인 “이미 소멸 완료” 상태는 BINARY_DELETED여야 하고, 이 상태는 애초에 삭제 대상 조회에서 제외되어야 한다.

Medium 1 — health scan이 BINARY_* 파일을 ORPHAN으로 오탐할 수 있다

문서상 9d-2에서는 BINARY_*를 health scan 대상에서 제외하고, 본격 확장은 9e로 미룬다고 되어 있다.

그런데 실제 AttachmentStorageHealthScanService.scanRows()는 STORED, DELETED, SOFT_DELETED, MISSING만 row scan에 포함한다. BINARY_DELETE_PENDING / BINARY_DELETE_FAILED는 제외된다.

반면 physical scan은 스토리지의 실제 파일을 계속 수집하고, addOrphanIssues()는 stored/deleted/missing row에 매칭되지 않는 파일을 orphan으로 본다.

즉 BINARY_DELETE_PENDING 또는 BINARY_DELETE_FAILED row가 storagePath를 가진 상태에서 파일이 남아 있으면, 9e 재처리 대상인데도 현재 health scan에서는 ORPHAN_PHYSICAL_FILE로 오탐될 수 있다.

9e에서 확장할 계획이라도, 현재 9d-2 상태에서는 최소한 BINARY_* row의 storageKey를 “known but deferred” 집합으로 잡아서 orphan 판정에서 제외해야 한다.

Medium 2 — 실제 파일 삭제 성공 경로 테스트가 약하다

PurgeExecutionServiceTest는 STORED 첨부 row를 직접 저장해서 saga 성공을 검증한다. 하지만 실제 파일을 storage root에 생성하지는 않는다. 그래서 성공 경로는 대부분 “파일이 이미 없음 = success”인 MISSING_AS_SUCCESS 케이스를 검증하는 형태다.

물론 멱등성 관점에서 “이미 없음 = 성공”은 중요하다. 하지만 9d-2의 핵심은 실제 존재하는 바이너리를 삭제하고, exists() 재확인 후 BINARY_DELETED로 승격하는 것이다. 현재 테스트는 이 핵심 경로를 충분히 실증하지 못한다.

추가 테스트를 넣어라.

1. AttachmentStorageService.store 또는 실제 storageRoot 파일 생성
2. ApplicationAttachment.storagePath가 실제 파일을 가리키게 함
3. execute 수행
4. 파일이 실제로 Files.exists=false인지 검증
5. attachment.status=BINARY_DELETED, storagePath=null, binaryDeletedAt!=null 검증
Low 1 — BINARY_DELETED의 null storagePath 오탐 방지 계획은 맞지만 테스트가 없다

문서에는 9e health scan 확장 시 BINARY_DELETED의 null storagePath를 invalid로 오탐하지 말라고 적혀 있다.
이건 맞는 지적이다. 9e에서 반드시 BINARY_DELETED + storagePath null은 정상으로 분류하는 테스트를 넣어야 한다.

Low 2 — BINARY_DELETE_FAILED의 상세 실패 사유는 아직 원장에 없다

현재 batch에는 binaryDeleteFailedCount가 들어갔고, attachment status는 BINARY_DELETE_FAILED로 남는다.
하지만 어떤 이유로 실패했는지는 row 수준에 남지 않는다. 지금은 log에만 의존한다. 9e에서 재처리/reconciliation을 하려면 attachment 또는 purge item 쪽에 sanitized failureCode/reasonMessage를 둘지 결정해야 한다.