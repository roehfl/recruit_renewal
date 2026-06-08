Medium 1 — Reconcile이 전체 PURGE_PENDING을 한 번에 조회한다

PurgeReconciliationService는 jobApplicationRepository.findByPurgeResult(PURGE_PENDING)로 전체 대상을 한 번에 가져온다.
현재는 수동 운영/소규모 전제라 괜찮지만, 장애 후 PENDING이 쌓이면 한 요청이 길어질 수 있다.

후속 하드닝으로 아래 형태가 낫다.

POST /api/admin/retention/purge-batches/reconcile?limit=100

또는 내부적으로 PageRequest를 써서 chunk 단위로 처리해라. Phase 09 종료 후 운영 안정화 과제로 넘겨도 되지만, 운영 전에 넣는 편이 안전하다.

Medium 2 — Reconcile summary audit 실패 시 이미 승격된 건은 롤백되지 않는다

Reconcile은 application별 saga를 REQUIRES_NEW로 먼저 커밋하고, 마지막에 recordRequiresNew()로 summary audit을 남긴다.

즉 summary audit 저장이 실패하면, 이미 승격된 application은 유지되지만 PURGE_RECONCILE 감사는 남지 않을 수 있다. 이건 9d의 batch complete audit 문제와 같은 유형이다.

현실적 해결책은 둘 중 하나다.

A. reconcile 시작 시점에 STARTED audit을 먼저 남기고, 완료 시 summary audit을 남긴다.
B. reconcile 전용 ledger batch를 만들고, ActivityLog는 보조 summary로 둔다.

지금 Phase 09 구조상 B는 과하다. 최소한 A 또는 “audit 실패 시 운영자가 재조회 가능한 로그/response” 정도는 후속으로 잡아라.

Low 1 — 실패코드 sanitization을 코드로 강제하지 않는다

문서상 binaryDeleteFailureCode는 sanitized code이고 길이 100이다.
현재 local storage 구현의 실패코드는 짧은 상수라 안전하지만, markBinaryDeleteFailed(failureCode)는 전달받은 값을 그대로 저장한다.

미래 S3/NAS 구현에서 긴 메시지나 경로가 failureCode로 들어오면 컬럼 길이 초과 또는 정보 노출이 생길 수 있다. 아래처럼 entity/service 경계에서 고정해라.

private String sanitizeFailureCode(String code) {
    if (code == null || code.isBlank()) return "UNKNOWN";
    String sanitized = code.replaceAll("[^A-Z0-9_]", "_");
    return sanitized.length() <= 100 ? sanitized : sanitized.substring(0, 100);
}