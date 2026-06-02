Blocking 문제
1. @Version 추가와 “migration 없음”이 충돌한다

현재 StageResult에는 @Version private Long version;이 추가됐다. 이건 DB에 version 컬럼이 필요하다는 뜻이다.

그런데 문서 첫머리는 아직 “새 entity/table/migration 없음”이라고 되어 있다.
또 리뷰 반영 항목에도 “PESSIMISTIC_WRITE 2안, migration 불필요”라는 문장이 그대로 남아 있다.

반면 뒤쪽 Known limitations에는 기존 DB 적용 시 컬럼 backfill 운영 절차가 필요하다고 적혀 있다.

즉 문서가 서로 모순된다.

ALTER TABLE stage_result
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

최소한 이 DDL 또는 그에 준하는 운영 절차가 명시되어야 한다. Flyway/Liquibase를 안 쓰는 프로젝트라면 docs/codex/ops나 docs/sql에 수동 반영 SQL이라도 남겨야 한다.

그리고 엔티티에도 명시적으로 아래처럼 가는 게 낫다.

@Version
@Column(nullable = false)
private Long version;

지금은 @Version만 있고 @Column(nullable = false)는 없다.

2. Optimistic lock 실패는 upload audit에 안 남을 가능성이 높다

upload commit controller는 uploadService.commit()이 정상적으로 StageResultUploadCommitResponse를 반환한 뒤에야 uploadAuditLogger.logUploadCommit(...)을 호출한다.

그런데 @Version 충돌은 service transaction commit/flush 시 예외로 터질 수 있고, 이 경우 controller의 audit 호출 라인까지 도달하지 않는다. GlobalExceptionHandler가 409는 반환하지만, upload audit outcome에는 안 남는다.

Phase 07d가 “유일한 쓰기 경로”이고 upload commit audit을 남긴다는 요구라면, 충돌 실패도 audit 대상이다. 최소한 OPTIMISTIC_LOCK_CONFLICT 같은 outcome으로 audit을 남겨야 한다.

수정 방향은 둘 중 하나다.

1. controller에서 ObjectOptimisticLockingFailureException을 잡아 audit 후 다시 throw/409 응답
2. upload audit을 AOP/필터/exception handler 쪽으로 옮겨 실패 attempt까지 기록