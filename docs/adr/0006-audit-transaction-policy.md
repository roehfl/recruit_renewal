# 감사 기록은 "증명하는 사실"에 따라 트랜잭션을 가른다 (커밋변경=in-tx / 실패·거부·충돌·스킵=REQUIRES_NEW / 반출=fail-close)

Phase 09 의 영속 `ActivityLog` 기록은 단일 트랜잭션 전략을 쓰지 않고, **그 row 가 무엇을 증명하는가** 에 따라 세 경로로 나눈다. ① **커밋된 도메인 변경의 성공 증적**(StageResult 변경/발표/확정, evaluation reopen 성공, application admin 상태변경, upload commit 성공, RetentionPolicy 변경, purge 성공 상태전이)은 **비즈니스 트랜잭션 안에서** insert 한다 — 감사 insert 가 실패하면 비즈니스도 rollback 한다(원자적). afterCommit 기록은 비즈니스만 커밋되고 감사가 누락될 수 있으므로 primary audit 에 쓰지 않는다. ② **실패·거부·충돌·스킵 증적**(version/optimistic-lock conflict, validation failure, authorization denied, purge failure/skip, reopen 실패, upload 실패)은 비즈니스 트랜잭션이 rollback 돼도 남아야 하므로 **`REQUIRES_NEW`** 별도 트랜잭션으로 기록한다. ③ **정보 반출**(export/PDF/admin download)은 도메인 변경 트랜잭션과 묶지 않고 별도 tx 로 기록하며, **fail-close** — 감사 commit 이 성공해야 산출물(파일/PDF)을 반환한다. emission 은 AOP blanket 이 아니라 명시적 `ActivityLogService.recordInCurrentTx()` / `recordRequiresNew()` 2경로다.

## Status

accepted (2026-06-04, Phase 09a 구현 완료로 전환 — `ActivityLogService.recordInCurrentTx`/`recordRequiresNew` 2경로 구현됨. 최초 proposed: 2026-06-04, Phase 09 design / grill-with-docs)

## Considered Options

- **전부 같은 트랜잭션(원자적)** — 거부. 단순하지만 비즈니스 rollback 시 *실패/충돌 증적까지 같이 rollback* 되어 "실패했다"는 감사가 사라진다(version-conflict 가 대표 사례).
- **전부 afterCommit / 비동기** — 거부. 비즈니스는 커밋됐는데 감사 row 만 누락되는 창이 생겨 "커밋된 변경의 증적"이라는 primary audit 의 의미가 깨진다.
- **사실 기준 3-way 분리(채택)** — 커밋변경=in-tx, 실패계열=REQUIRES_NEW, 반출=fail-close.

## Consequences

- 커밋변경 경로에서는 감사 테이블 장애가 정당한 업무 쓰기를 막을 수 있다(감사가 hard invariant). 의도된 트레이드오프다.
- 반출 fail-close: 감사 commit 후 스트리밍이 깨지면 "반출함" row 만 남고 실제 수신은 안 된 상태가 될 수 있다 — 누락보다 안전한 **보수적 over-record** 로 허용한다.
- `recordRequiresNew()` 는 Spring self-invocation 프록시 함정을 피하기 위해 **별도 bean 메서드**여야 한다.
- 향후 "성능 때문에 afterCommit 으로 바꾸자"는 압력이 생길 수 있으나, 그 변경은 커밋변경 증적의 원자성을 깨므로 본 ADR 의 명시적 재검토가 선행되어야 한다.
- `ActivityLog` 는 batch 단위 coarse index(특히 purge)로만 쓰고, per-item 상세는 `PurgeJobItem` 등 도메인 원장이 보유한다(중복 기록 금지).
