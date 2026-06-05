Major 1 — StageResult.comment / StageResultCorrectionHistory 미소거 상태에서 PURGED 가능

가장 큰 문제다.

현재 9d-1 문서도 이걸 “인벤토리 갭 flag”로 알고 있다. StageResult.comment와 StageResultCorrectionHistory의 comment 계열이 미분류라 9d-2/9e에서 처리 권고라고 적혀 있다.

그런데 코드상 PurgeItemProcessor는 첨부 outstanding이 없으면 즉시 application.markPurged()를 호출한다. 즉, StageResult.comment나 correction history에 지원자 원문이 남아 있어도 PURGED가 될 수 있다.

실제로 StageResult.comment는 자유 텍스트다.
StageResultCorrectionHistory에도 reason, previousComment, newComment가 있다.
ADR에서도 PII 가능성이 있는 자유입력 reason/comment는 소거 대상이라고 되어 있다.

이건 단순 문서 한계로 넘기면 안 된다. PURGED라는 최종 marker를 쓰는 순간 관계형 PII가 모두 제거됐다는 의미가 되기 때문이다.

수정 방향:

@Modifying(flushAutomatically = true)
@Query("""
    update StageResult r
    set r.comment = null, r.createdBy = null, r.updatedBy = null
    where r.jobApplication.id = :applicationId
""")
int purgeStageResultComments(Long applicationId);
@Modifying(flushAutomatically = true)
@Query("""
    update StageResultCorrectionHistory h
    set h.reason = '__PURGED__',
        h.previousComment = null,
        h.newComment = null,
        h.createdBy = null,
        h.updatedBy = null
    where h.stageResult.jobApplication.id = :applicationId
""")
int purgeStageResultCorrectionHistories(Long applicationId);

reason은 nullable=false라 placeholder가 맞고, previousComment/newComment는 nullable이면 nullify가 맞다. 이걸 ApplicationPiiPurgeRepository와 ApplicationPiiPurgeService.purgeRelationalPii()에 포함시키고 field-level 테스트에 추가해라.

Medium 1 — batch complete audit 실패 시 RUNNING batch가 남을 수 있다

PurgeBatchLifecycleService.completeExecute()는 batch 상태 변경과 PURGE_EXECUTE ActivityLog 기록을 같은 REQUIRES_NEW 트랜잭션에서 처리한다.
그런데 PurgeExecutionService는 item 루프가 끝난 뒤 completeExecute()를 호출하고, 이 호출은 루프 내부 try/catch 밖에 있다.

즉 item들은 이미 각각 commit됐는데, 마지막 complete/audit에서 예외가 나면 batch 상태는 RUNNING으로 남을 수 있다. 관계형 PII는 이미 지워졌는데 batch가 완료되지 않은 상태가 된다.

실제 실패 가능성은 낮아도 파기 시스템에서는 위험한 형태다. 수정 방향은 둘 중 하나다.

batch complete 상태 전이를 먼저 독립 tx로 확정하고, audit은 별도 tx로 남긴다.
completeExecute() 실패를 orchestrator에서 catch해서 failExecute() 또는 별도 completeAuditFailed 상태/로그를 남긴다.

현재 enum에 별도 상태가 없으니 최소한 completeExecute() 실패 시 batch가 RUNNING으로 방치되지 않게 해야 한다.

Medium 2 — bulk execute source dry-run의 status 검증이 없다

현재 bulk execute는 source batch가 존재하고 mode == DRY_RUN인지만 확인한다.
하지만 source dry-run은 최소한 COMPLETED 상태여야 한다. RUNNING, FAILED, 미래의 PARTIAL_FAILED 같은 상태를 근거로 execute하면 안 된다.

PurgeBatch는 status를 가지고 있다.
그러니 아래 검증을 추가해라.

if (source.getMode() != PurgeBatchMode.DRY_RUN || source.getStatus() != PurgeBatchStatus.COMPLETED) {
    throw new InvalidRetentionRequestException("sourceDryRunBatchId는 COMPLETED DRY_RUN batch여야 합니다.");
}

그리고 테스트에 DRY_RUN/RUNNING, EXECUTE/COMPLETED 둘 다 거부 케이스를 넣어라.

Low 1 — FAILED item에 reason 정보가 없다

recordFailure()는 PurgeJobItem.executeFailed()만 저장하고, reasonCode나 failure message는 없다.
현재 PurgeJobItem 구조상 FAILED도 reasonCode를 담을 수 있는데, executeFailed()는 null로 만든다.

09e reconciliation에서 왜 실패했는지 추적하려면 최소한 reasonCode 하나는 있어야 한다. 예를 들어 VALIDATION_FAILED, BINARY_DELETE_FAILED, PURGE_ITEM_FAILED 같은 코드가 필요하다. 새 enum을 추가하기 부담되면 reasonMessage 컬럼을 09e에서 추가할 수 있도록 TODO로 고정해라.

Low 2 — 같은 dry-run 재실행 ledger는 의도됐지만 UI에서 혼란 가능성이 있다

같은 dry-run으로 재실행하면 새 execute batch와 새 item들이 생긴다. 테스트도 이 동작을 인정하고 ALREADY_PURGED skip을 확인한다.

ledger 의미상 맞다. 다만 운영 UI에서는 “같은 dry-run을 여러 번 실행했다”는 연결성이 필요하다. sourceDryRunBatchId는 batch에 있으니 목록/상세에서 이걸 강조하면 된다. 이미 응답에는 sourceDryRunBatchId가 포함되어 있다.