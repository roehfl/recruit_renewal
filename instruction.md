남은 판단 포인트: 동시성

현재 구현은 commit에서 변경 대상 row만 PESSIMISTIC_WRITE로 refresh/lock하고, lock 후 최신 DB 값 기준으로 token을 다시 비교한다. upload끼리 동시에 들어오는 케이스에서는 늦게 들어온 쪽이 갱신된 updatedAt을 보고 STALE이 될 가능성이 높다. 구현 방향은 이전 지적 대비 명백히 좋아졌다.

하지만 이건 전체 StageResult write model의 완전한 lost update 방지는 아니다.

이유는 기존 StageResultService.updateResult()와 bulkUpdateResults()가 여전히 일반 조회 후 엔티티를 변경하는 방식이고, StageResult 엔티티에는 @Version이 없다.

즉, 아래 케이스는 아직 이론상 가능하다.

1. 기존 수동 수정 API 트랜잭션 A가 StageResult를 먼저 읽음.
2. upload commit 트랜잭션 B가 PESSIMISTIC_WRITE lock 후 token 검증 통과.
3. B가 update/commit.
4. A가 오래된 엔티티 상태로 나중에 flush/commit.
5. A가 B의 변경을 덮어쓸 수 있음. @Version이 없기 때문.

이건 upload-vs-upload 문제가 아니라 upload와 기존 비-locking writer 간 경쟁이다. 문서에는 “lost update 자체는 PESSIMISTIC_WRITE 잠금으로 차단된다”고 적혀 있는데, 이 표현은 현재 코드 기준으로는 너무 강하다.

최종 판단

Phase 7d 범위를 “Excel upload 간 동시 commit 방지”로 한정하면 PASS 가능.

다만 문서/요구사항이 “StageResult에 대한 모든 관리 변경 경로와의 lost update 방지”라면 아직 FAIL이다. 그 수준까지 요구하려면 StageResult에 @Version을 추가하는 게 맞다. migration이 부담이면 최소한 기존 updateResult() / bulkUpdateResults()도 같은 locking 정책을 공유해야 한다.

권장 수정

최소 수정은 문서 정정:

PESSIMISTIC_WRITE는 upload commit 내부의 변경 대상 row를 잠그고 lock 후 token을 재검증해 upload-vs-upload 경쟁에서 stale overwrite를 막는다.
다만 StageResult 엔티티에 @Version이 없고 기존 수동 update 경로는 non-locking writer이므로, 모든 StageResult write path 간의 완전한 lost update 방지는 후속 @Version 도입 전까지 보장하지 않는다.

더 안전한 수정은 코드 보강:

StageResult에 @Version 필드를 추가하고 DB migration을 반영한다.
기존 updateResult, bulkUpdateResults, upload commit 모두 OptimisticLockException/ObjectOptimisticLockingFailureException을 409 계열로 매핑한다.
이후 PESSIMISTIC_WRITE는 제거하거나, upload 대량 처리 중 충돌 UX 개선용으로만 유지한다.