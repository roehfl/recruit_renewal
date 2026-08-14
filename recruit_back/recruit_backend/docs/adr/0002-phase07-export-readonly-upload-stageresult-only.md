# Phase 07 export는 비변경이고, Excel upload은 StageResult만 변경하며 InterviewEvaluation은 제외한다

Phase 07(Export/PDF/Statistics)에서 export·statistics·PDF는 도메인 상태를 절대 바꾸지 않는 read-only다. 유일한 쓰기 경로는 Excel upload이며, 그 대상을 `StageResult`로만 한정하고 기존 `StageResultService.bulkUpdateResults`를 경유해 기존 불변식을 상속한다. `InterviewEvaluation`의 Excel upload(쓰기)는 영구 제외한다 — Phase 06이 "평가는 배정 면접관 본인만 작성"하고 "면접관끼리 서로 평가를 못 본다(평가 독립성)"는 경계를 세웠기 때문에, admin이 엑셀로 평가 등급을 일괄 입력/수정하면 두 경계가 모두 깨진다.

## Status

accepted (2026-05-29, Phase 07 design). 초기 `CONTEXT.md`는 upload 대상을 StageResult + InterviewEvaluation으로 적었으나 본 ADR로 StageResult only로 축소.

## Considered Options

- **admin 대리입력 허용** — 종이 평가지를 admin이 대량 키인하는 시나리오를 인정하고 `enteredByAdmin` 출처 표식 + audit로 처리. 거부(보류). Phase 06의 평가 독립성/작성 주체 경계를 완화해야 하므로, 종이 운영이 확정 요구사항이 될 때만 재검토한다.
- **StageResult only** — 채택. 평가 변경은 면접관 제출 + admin reopen 경로로만 두고, Phase 07 쓰기는 StageResult bulk 변경에 한정.

## Consequences

- 면접 평가 점수를 admin이 엑셀로 대량 적재하는 기능은 Phase 07에 없다.
- 향후 필요해지면 Phase 06 경계(평가 작성 주체·독립성)의 명시적 재설계가 선행되어야 한다.
- export는 평가 데이터를 읽기로 내보낼 수 있으나(읽기 전용), 변경하지 않는다.
