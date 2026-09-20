# 개인정보 파기(강제 파기·자동 파기·관리자 화면) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 삭제 요청을 받은 지원자를 관리자가 화면에서 즉시 파기하고, 보존기간 5년이 지난 지원서는 매일 새벽 자동으로 파기되게 만든다.

**Architecture:** 기존 파기 파이프라인(`PurgeBatch` → `PurgeJobItem` → `PurgeItemProcessor` → 첨부 saga)을 그대로 재사용한다. 강제 파기는 적격성 판정만 2단계로 줄인 경로를 추가하고, 자동 파기는 기존 dry-run → execute 2단계를 스케줄러가 호출한다. 파기 로직 자체는 건드리지 않는다.

**Tech Stack:** Spring Boot 4 · Java 17 · JPA(JPQL) · Spring Security · JUnit5/MockMvc(H2 MODE=MySQL) / Vue 3.5 · TypeScript · ant-design-vue 4 · Pinia · Vitest

**근거 설계서:** `docs/superpowers/specs/2026-09-20-privacy-purge-design.md`
**대상 도메인 카드:** `docs/domains/privacy-audit.md`

---

## 작업 규칙 (전 Task 공통)

- **커밋하지 않는다.** 이 저장소는 사용자 요청 없이 `git commit`·`git push`를 하지 않는다. 각 Task의 마지막 단계는 커밋이 아니라 **검증 명령 실행**이다.
- 백엔드 테스트는 변경 범위만 돌린다. 실행 위치는 `recruit_back/recruit_backend/`.
  ```bash
  AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.ForcedPurge*" --no-daemon
  ```
  Windows PowerShell이면 `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "..." --no-daemon`.
  감사 HMAC 키는 테스트 설정에 이미 있어 따로 넣지 않는다.
- 프론트 명령 실행 위치는 `recruit_front/`.
- 새 파일을 만들면 줄바꿈이 LF여야 한다. 작성 후 확인:
  ```bash
  tr -cd '\r' < <파일> | wc -c
  ```
  결과가 `0`이 아니면 CRLF를 LF로 바꾼다.
- 관리자 화면에서 이메일·휴대폰을 **마스킹하지 않는다**. 마스킹은 로그와 감사 metadata에만 적용한다.
- 로그에 지원자 이름·이메일·휴대폰을 남기지 않는다. id와 건수만 남긴다.

---

## 파일 구조

### S1 — 강제 파기 백엔드

| 파일 | 책임 |
|---|---|
| `{BE}/enumeration/ForcedPurgeReason.java` | 강제 파기 사유 3값(선택형) |
| `{BE}/enumeration/AuditActionType.java` | `PURGE_FORCED` 값 추가 |
| `{BE}/service/ForcedPurgeMetadata.java` | 감사 metadata record(PII-free) |
| `{BE}/service/AuditMetadata.java` | permits에 `ForcedPurgeMetadata` 추가 |
| `{BE}/domain/entity/PurgeBatch.java` | `startForced` 팩토리 추가 |
| `{BE}/service/PurgeItemProcessor.java` | `process(..., boolean forced)` — forced면 판정 2단계 |
| `{BE}/service/PurgeBatchLifecycleService.java` | `startForced`·`completeForced`·`failForced` |
| `{BE}/service/ForcedPurgeService.java` | 강제 파기 오케스트레이션(무트랜잭션) |
| `{BE}/service/DataSubjectLookupService.java` | 지원자 검색·상세 조회(읽기 전용) |
| `{BE}/domain/repository/ApplicantRepository.java` | 이름·휴대폰·이메일 검색 JPQL |
| `{BE}/dto/request/ForcedPurgeRequest.java` | `{ applicantId, reasonCode, confirm }` |
| `{BE}/dto/response/DataSubjectSummaryResponse.java` | 검색 결과 행 |
| `{BE}/dto/response/DataSubjectDetailResponse.java` | 상세(지원서별 판정 포함) |
| `{BE}/exception/ApplicantNotFoundException.java` | 404 |
| `{BE}/exception/GlobalExceptionHandler.java` | 위 예외 → 404 매핑 |
| `{BE}/controller/AdminRetentionController.java` | 엔드포인트 3개 |
| `{BE}/config/SecurityConfig.java` | 좁은 매처 3개(broad 앞) |

### S2 — 자동 파기 백엔드

| 파일 | 책임 |
|---|---|
| `{BE}/domain/entity/RetentionScheduleSetting.java` | 단일 행 설정(on/off, 마지막 실행) |
| `{BE}/domain/repository/RetentionScheduleSettingRepository.java` | 조회·저장 |
| `{BE}/enumeration/RetentionScheduleRunResult.java` | 실행 결과 5값 |
| `{BE}/domain/repository/JobApplicationRepository.java` | `findEarliestUnpurgedClosedAt` |
| `{BE}/service/RetentionScheduleService.java` | 설정 조회·토글·예정일 계산·실행 결과 기록 |
| `{BE}/service/RetentionPurgeScheduler.java` | `@Scheduled` 게이트 2단 + dry-run → execute |
| `{BE}/service/AuditRequestContextResolver.java` | `SYSTEM` 예약 actorId → `ActorType.SYSTEM` |
| `{BE}/dto/request/RetentionScheduleRequest.java` | `{ enabled }` |
| `{BE}/dto/response/RetentionScheduleResponse.java` | 설정 + 예정일 + 마지막 실행 |
| `{BE}/controller/AdminRetentionController.java` | 엔드포인트 2개 |
| `{BE}/config/SecurityConfig.java` | 매처 2개 |
| `recruit_back/recruit_backend/docs/ops/phase-10-retention-schedule-ddl.sql` | 운영 수동 DDL |

### S3 — 관리자 화면

| 파일 | 책임 |
|---|---|
| `{FE}/types/admin/retention.ts` | 요청·응답 타입 |
| `{FE}/api/admin/retentionApi.ts` | 호출 모듈 |
| `{FE}/views/admin/retention/retentionLabel.ts` | 사유코드·판정·실행결과 라벨 매핑 |
| `{FE}/views/admin/retention/AdminRetentionView.vue` | 화면 뼈대(카드 2개 + 탭 2개) |
| `{FE}/views/admin/retention/RetentionPolicyCard.vue` | 보존 정책 요약·편집 |
| `{FE}/views/admin/retention/RetentionScheduleCard.vue` | 자동 파기 on/off·예정일·마지막 실행 |
| `{FE}/views/admin/retention/DataSubjectSearchPanel.vue` | 검색 + 결과 표 |
| `{FE}/views/admin/retention/DataSubjectDrawer.vue` | 지원자 상세 + 파기 버튼 |
| `{FE}/views/admin/retention/ForcedPurgeConfirmModal.vue` | 확인 모달 |
| `{FE}/views/admin/retention/PurgeBatchPanel.vue` | 파기 이력 표 + 드로어 |
| `{FE}/routes/adminRoutes.ts` | `/admin/retention` 라우트 |

경로 약어: `{BE}` = `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit`,
`{BT}` = `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit`,
`{FE}` = `recruit_front/src`.

---

# S1 — 강제 파기 백엔드

## Task 1: 사유 enum · 감사 타입 · metadata

**Files:**
- Create: `{BE}/enumeration/ForcedPurgeReason.java`
- Create: `{BE}/service/ForcedPurgeMetadata.java`
- Modify: `{BE}/enumeration/AuditActionType.java`
- Modify: `{BE}/service/AuditMetadata.java`
- Test: `{BT}/service/AuditMetadataContractTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/AuditMetadataContractTest.java`의 `EXPECTED_COMPONENTS` 맵에 아래 항목을 추가한다.
(파일에 이미 다른 record들이 같은 형태로 등록돼 있다. 기존 스타일을 그대로 따른다.)

```java
"ForcedPurgeMetadata", List.of(
        "purgeBatchId", "applicantRefHash", "reasonCode",
        "totalCount", "purgedCount", "pendingCount", "skippedCount", "failedCount")
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.AuditMetadataContractTest" --no-daemon
```
Expected: FAIL — `ForcedPurgeMetadata` 클래스가 없어 컴파일 실패 또는 permits 불일치.

- [ ] **Step 3: enum 값 추가**

`{BE}/enumeration/AuditActionType.java`의 `PURGE_RECONCILE` 다음에 추가:

```java
    /** 정보주체 삭제 요청 등으로 보존기간과 무관하게 실행하는 강제 파기(Phase 10). */
    PURGE_FORCED
```

- [ ] **Step 4: 사유 enum 생성**

`{BE}/enumeration/ForcedPurgeReason.java`:

```java
package com.shinyoung.recruit.enumeration;

/**
 * 강제 파기 사유(Phase 10). 자유 텍스트를 받지 않는 이유 — ActivityLog 와 파기 대장은 파기 대상이 아니라
 * 거기에 들어간 지원자 PII 는 영구히 남는다. 상세 경위는 오프라인 접수 대장이 맡는다.
 */
public enum ForcedPurgeReason {
    /** 본인(정보주체)의 삭제 요청. */
    DATA_SUBJECT_REQUEST,
    /** 중복 가입·오입력 계정 정리. */
    DUPLICATE_ACCOUNT,
    /** 그 밖의 사유. */
    OTHER
}
```

- [ ] **Step 5: metadata record 생성**

`{BE}/service/ForcedPurgeMetadata.java`:

```java
package com.shinyoung.recruit.service;

/**
 * 강제 파기 감사 metadata(PII-free 집계만, Phase 10). 사람 식별은 {@code applicantRefHash}(HMAC)로만 한다 —
 * 이름·이메일·휴대폰·loginId 는 넣지 않는다.
 */
public record ForcedPurgeMetadata(
        long purgeBatchId,
        String applicantRefHash,
        String reasonCode,
        long totalCount,
        long purgedCount,
        long pendingCount,
        long skippedCount,
        long failedCount
) implements AuditMetadata {
}
```

- [ ] **Step 6: permits 추가**

`{BE}/service/AuditMetadata.java`의 permits 목록 마지막에 `ForcedPurgeMetadata`를 추가한다(`PurgeReconcileMetadata` 다음, 쉼표 위치 주의).

- [ ] **Step 7: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.AuditMetadataContractTest" --no-daemon
```
Expected: PASS

---

## Task 2: `PurgeItemProcessor` forced 판정 경로

**Files:**
- Modify: `{BE}/service/PurgeItemProcessor.java`
- Modify: `{BE}/service/PurgeExecutionService.java` (호출부 시그니처)
- Test: `{BT}/service/PurgeItemProcessorForcedTest.java` (신규)

> **중요:** 오버로드를 만들지 말 것. `process(3인자)`가 같은 빈의 `process(4인자)`를 호출하면 self-invocation이라 `@Transactional(REQUIRES_NEW)` 프록시가 적용되지 않는다. **기존 메서드 시그니처를 4인자로 바꾸고 호출부를 고친다.**

- [ ] **Step 1: 실패하는 테스트 작성**

`{BT}/service/PurgeItemProcessorForcedTest.java`를 만든다. 기존 `{BT}/service/PurgeItemProcessorTest.java`의 픽스처 구성 방식(스프링 통합 테스트, 공고·지원서·전형 저장)을 그대로 따른다.

```java
@Test
@DisplayName("forced=true 면 보존기간이 남아 있어도 파기한다")
void forcedIgnoresRetentionNotDue() {
    // given: 방금 마감된 공고(closedAt = now)의 제출 지원서 — 일반 경로면 RETENTION_NOT_DUE
    Long applicationId = 준비한_지원서_id;
    Long batchId = 준비한_forced_batch_id;

    PurgeItemProcessor.PurgeItemOutcome outcome =
            purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

    assertThat(outcome.status()).isIn(PurgeItemStatus.PURGED, PurgeItemStatus.PENDING);
    assertThat(jobApplicationRepository.findById(applicationId).orElseThrow().getPurgeResult()).isNotNull();
}

@Test
@DisplayName("forced=true 여도 active hold 가 있으면 SKIPPED(RETENTION_HOLD)")
void forcedRespectsHold() {
    retentionHoldRepository.save(RetentionHold.of(applicationId, "소송 진행", "tester"));

    PurgeItemProcessor.PurgeItemOutcome outcome =
            purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

    assertThat(outcome.status()).isEqualTo(PurgeItemStatus.SKIPPED);
    assertThat(outcome.reasonCode()).isEqualTo(AuditReasonCode.RETENTION_HOLD);
}

@Test
@DisplayName("forced=true 여도 이미 파기된 건은 SKIPPED(ALREADY_PURGED)")
void forcedRespectsAlreadyPurged() {
    purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

    PurgeItemProcessor.PurgeItemOutcome second =
            purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

    assertThat(second.status()).isEqualTo(PurgeItemStatus.SKIPPED);
    assertThat(second.reasonCode()).isEqualTo(AuditReasonCode.ALREADY_PURGED);
}

@Test
@DisplayName("forced=true 는 전형이 종료되지 않은 진행 중 지원서도 파기한다")
void forcedIgnoresNotTerminal() {
    // given: finalStage 가 PENDING 상태인 지원서
    PurgeItemProcessor.PurgeItemOutcome outcome =
            purgeItemProcessor.process(batchId, 진행중_지원서_id, LocalDateTime.now(clock), true);

    assertThat(outcome.status()).isIn(PurgeItemStatus.PURGED, PurgeItemStatus.PENDING);
}
```

`RetentionHold.of(...)`의 정확한 팩토리 이름은 `{BE}/domain/entity/RetentionHold.java`를 열어 확인하고 맞춘다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.PurgeItemProcessorForcedTest" --no-daemon
```
Expected: FAIL — `process`가 3인자라 컴파일 실패.

- [ ] **Step 3: 시그니처 변경과 forced 판정 추가**

`{BE}/service/PurgeItemProcessor.java`:

```java
    /**
     * @param forced 정보주체 삭제 요청 등 강제 파기(Phase 10). true 면 적격성 판정을
     *               ALREADY_PURGED·RETENTION_HOLD 두 가지로 줄인다 — 보존기간·anchor·전형 종료 여부를 보지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurgeItemOutcome process(Long batchId, Long applicationId, LocalDateTime scanAt, boolean forced) {
```

기존 eligibility 재검증 블록을 아래로 바꾼다.

```java
        // 실행 시 eligibility 재검증(dry-run 결과 불신뢰). 탈락 = SKIPPED + 사유(drift 기록).
        RetentionEligibilityService.EligibilityDecision decision = forced
                ? forcedDecision(application, applicationId)
                : retentionEligibilityService.evaluate(
                        application,
                        jobPosting,
                        retentionPolicyService.selectPolicy(jobPostingId, scanAt),
                        retentionHoldRepository.existsByApplicationIdAndReleasedAtIsNull(applicationId),
                        finalStages(jobPostingId),
                        finalStageResult(jobPostingId, applicationId),
                        scanAt
                );
```

클래스 하단(`anonymizeApplicantIfRefZero` 위)에 판정 메서드를 추가한다.

```java
    /**
     * 강제 파기 판정(Phase 10) — 보존기간·anchor·전형 상태를 보지 않는다. 남기는 두 가지는
     * ① 이미 파기된 건(두 번 지울 것이 없다) ② 보류(법적 보존 의무가 삭제 요구권보다 우선).
     * 정책·전형 조회를 하지 않으므로 불필요한 쿼리도 나가지 않는다.
     */
    private RetentionEligibilityService.EligibilityDecision forcedDecision(
            JobApplication application, Long applicationId) {
        if (application.getPurgeResult() != null) {
            return new RetentionEligibilityService.EligibilityDecision(AuditReasonCode.ALREADY_PURGED);
        }
        if (retentionHoldRepository.existsByApplicationIdAndReleasedAtIsNull(applicationId)) {
            return new RetentionEligibilityService.EligibilityDecision(AuditReasonCode.RETENTION_HOLD);
        }
        return new RetentionEligibilityService.EligibilityDecision(null);
    }
```

- [ ] **Step 4: 호출부 수정**

`{BE}/service/PurgeExecutionService.java`의 호출을 4인자로 바꾼다.

```java
                    PurgeItemProcessor.PurgeItemOutcome outcome =
                            purgeItemProcessor.process(batch.getId(), applicationId, batch.getScanAt(), false);
```

- [ ] **Step 5: 기존 테스트의 호출부도 수정**

`{BT}/service/` 아래에서 `purgeItemProcessor.process(` 호출을 찾아 전부 4인자(`false`)로 고친다.

```bash
grep -rn "purgeItemProcessor.process(" recruit_back/recruit_backend/src/test/java
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.PurgeItemProcessor*" --tests "com.shinyoung.recruit.service.PurgeExecutionServiceTest" --no-daemon
```
Expected: PASS (신규 4건 + 기존 전부)

---

## Task 3: `PurgeBatch.startForced` · lifecycle 메서드

**Files:**
- Modify: `{BE}/domain/entity/PurgeBatch.java`
- Modify: `{BE}/service/PurgeBatchLifecycleService.java`
- Test: `{BT}/service/PurgeBatchLifecycleServiceForcedTest.java` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("startForced 는 triggerType=DATA_SUBJECT_REQUEST, mode=EXECUTE 로 RUNNING batch 를 만든다")
void startForcedCreatesBatch() {
    PurgeBatch batch = purgeBatchLifecycleService.startForced("tester");

    assertThat(batch.getMode()).isEqualTo(PurgeBatchMode.EXECUTE);
    assertThat(batch.getTriggerType()).isEqualTo(PurgeTriggerType.DATA_SUBJECT_REQUEST);
    assertThat(batch.getStatus()).isEqualTo(PurgeBatchStatus.RUNNING);
    assertThat(batch.getSourceDryRunBatchId()).isNull();
}

@Test
@DisplayName("completeForced 는 집계를 확정하고 PURGE_FORCED 감사를 남긴다")
void completeForcedRecordsAudit() {
    PurgeBatch batch = purgeBatchLifecycleService.startForced("tester");

    purgeBatchLifecycleService.completeForced(
            batch.getId(), "ref-hash", ForcedPurgeReason.DATA_SUBJECT_REQUEST,
            2, 2, 0, 0, 0, "tester");

    PurgeBatch saved = purgeBatchRepository.findById(batch.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PurgeBatchStatus.COMPLETED);
    assertThat(saved.getPurgedCount()).isEqualTo(2);

    List<ActivityLog> logs = activityLogRepository.findAll();
    assertThat(logs).anySatisfy(log -> {
        assertThat(log.getActionType()).isEqualTo(AuditActionType.PURGE_FORCED);
        assertThat(log.getActionResult()).isEqualTo(AuditActionResult.SUCCESS);
        assertThat(log.getMetadataJson()).contains("ref-hash").doesNotContain("@");
    });
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.PurgeBatchLifecycleServiceForcedTest" --no-daemon
```
Expected: FAIL — `startForced` 없음.

- [ ] **Step 3: 엔티티 팩토리 추가**

`{BE}/domain/entity/PurgeBatch.java`의 `startExecute` 다음에:

```java
    /** 강제 파기 시작(Phase 10). 근거 dry-run 이 없고 트리거가 정보주체 요청이다. */
    public static PurgeBatch startForced(LocalDateTime scanAt, LocalDateTime startedAt, String requestedBy) {
        return new PurgeBatch(
                PurgeBatchMode.EXECUTE, PurgeTriggerType.DATA_SUBJECT_REQUEST, scanAt, startedAt, requestedBy);
    }
```

- [ ] **Step 4: lifecycle 메서드 추가**

`{BE}/service/PurgeBatchLifecycleService.java`에 세 메서드를 추가한다. 기존 `startExecute`/`completeExecute`/`failExecute`와 같은 모양이며 감사 actionType과 metadata만 다르다.

```java
    /** 강제 파기 batch 시작(Phase 10) — item 처리 전에 RUNNING 으로 커밋해 둔다(원장 선기록). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurgeBatch startForced(String actor) {
        LocalDateTime now = LocalDateTime.now(clock);
        return purgeBatchRepository.save(PurgeBatch.startForced(now, now, actor));
    }

    /** 강제 파기 집계 완료(+PURGE_FORCED 감사, in-tx). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurgeBatchDetailResponse completeForced(
            Long batchId,
            String applicantRefHash,
            ForcedPurgeReason reason,
            long totalCount,
            long purgedCount,
            long pendingCount,
            long skippedCount,
            long failedCount,
            String actor
    ) {
        PurgeBatch batch = findBatch(batchId);
        batch.completeExecute(totalCount, purgedCount, pendingCount, skippedCount, failedCount,
                pendingCount, LocalDateTime.now(clock));

        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.PURGE_FORCED)
                .actionResult(batch.getStatus() == PurgeBatchStatus.PARTIAL_FAILED
                        ? AuditActionResult.FAILURE
                        : AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.PURGE_BATCH)
                .targetId(String.valueOf(batchId))
                .reasonMessage(batch.getStatus() == PurgeBatchStatus.PARTIAL_FAILED
                        ? "forced purge partial failure: failedCount=" + failedCount
                                + ", pendingCount=" + pendingCount
                        : null)
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(new ForcedPurgeMetadata(
                        batchId, applicantRefHash, reason.name(),
                        totalCount, purgedCount, pendingCount, skippedCount, failedCount))
                .build());

        return PurgeBatchDetailResponse.of(batch, purgeJobItemRepository.findByPurgeBatchIdOrderByIdAsc(batchId));
    }

    /** 강제 파기 오케스트레이션 실패 — batch FAILED 확정 + 실패 증적. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failForced(Long batchId, String actor) {
        PurgeBatch batch = findBatch(batchId);
        batch.fail(LocalDateTime.now(clock));

        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.PURGE_FORCED)
                .actionResult(AuditActionResult.FAILURE)
                .targetType(AuditTargetType.PURGE_BATCH)
                .targetId(String.valueOf(batchId))
                .reasonMessage("forced purge batch failed")
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }
```

`completeExecute`의 마지막 인자는 `binaryDeleteFailedCount`다. 강제 파기에서 바이너리가 남은 건(`pendingCount`)은 그대로 실패로 집계해 `PARTIAL_FAILED`가 되게 한다 — "DB PURGED + 파일 잔존"을 성공으로 보지 않는 기존 원칙과 같다.

- [ ] **Step 5: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.PurgeBatchLifecycle*" --no-daemon
```
Expected: PASS

---

## Task 4: 지원자 검색·상세 조회

**Files:**
- Modify: `{BE}/domain/repository/ApplicantRepository.java`
- Create: `{BE}/service/DataSubjectLookupService.java`
- Create: `{BE}/dto/response/DataSubjectSummaryResponse.java`
- Create: `{BE}/dto/response/DataSubjectDetailResponse.java`
- Create: `{BE}/exception/ApplicantNotFoundException.java`
- Modify: `{BE}/exception/GlobalExceptionHandler.java`
- Test: `{BT}/service/DataSubjectLookupServiceTest.java` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("검색 조건이 모두 비면 400")
void searchRequiresAtLeastOneCondition() {
    assertThatThrownBy(() -> dataSubjectLookupService.search(null, "  ", null))
            .isInstanceOf(InvalidRetentionRequestException.class);
}

@Test
@DisplayName("휴대폰은 하이픈을 제거하고 부분 일치로 찾는다")
void searchNormalizesPhone() {
    List<DataSubjectSummaryResponse> found = dataSubjectLookupService.search(null, "010-1234-5678", null);

    assertThat(found).extracting(DataSubjectSummaryResponse::applicantId).contains(지원자_id);
}

@Test
@DisplayName("이미 익명화된 계정은 결과에서 제외한다")
void searchExcludesPurgedAccounts() {
    applicant.purgePersonalData("PURGED:" + UUID.randomUUID());
    applicantRepository.save(applicant);

    assertThat(dataSubjectLookupService.search("홍길동", null, null)).isEmpty();
}

@Test
@DisplayName("상세는 지원서별 적격성 판정을 함께 돌려준다")
void detailIncludesEligibility() {
    DataSubjectDetailResponse detail = dataSubjectLookupService.getDetail(지원자_id);

    assertThat(detail.applications()).hasSize(1);
    assertThat(detail.applications().get(0).reasonCode()).isEqualTo(AuditReasonCode.RETENTION_NOT_DUE.name());
    assertThat(detail.hasActiveHold()).isFalse();
}

@Test
@DisplayName("없는 지원자는 404")
void detailNotFound() {
    assertThatThrownBy(() -> dataSubjectLookupService.getDetail(999_999L))
            .isInstanceOf(ApplicantNotFoundException.class);
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.DataSubjectLookupServiceTest" --no-daemon
```
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 검색 JPQL 추가**

`{BE}/domain/repository/ApplicantRepository.java`:

```java
    /**
     * 파기 대상자 검색(Phase 10). 세 조건 AND, 전부 부분 일치. 휴대폰은 저장값과 입력값 모두
     * 하이픈·공백을 제거해 비교한다(기존 JobApplicationRepository 검색 규칙과 동일).
     * 이미 익명화된 계정(ciHash 가 PURGED: 접두)은 제외한다.
     */
    @Query("""
            select a from Applicant a
             where (:name is null or a.userName like concat('%', :name, '%'))
               and (:phoneNumber is null
                    or replace(replace(a.phoneNumber, '-', ''), ' ', '') like concat('%', :phoneNumber, '%'))
               and (:email is null or a.email like concat('%', :email, '%'))
               and (a.ciHash is null or a.ciHash not like 'PURGED:%')
             order by a.id desc""")
    List<Applicant> searchDataSubjects(
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("email") String email,
            Pageable pageable);
```

`Pageable`은 `org.springframework.data.domain.Pageable`이다. 상한 50은 서비스가 `PageRequest.of(0, 50)`으로 넘긴다.

- [ ] **Step 4: 404 예외 추가**

`{BE}/exception/ApplicantNotFoundException.java`:

```java
package com.shinyoung.recruit.exception;

/** 지원자(Applicant)를 찾을 수 없을 때. 404. */
public class ApplicantNotFoundException extends RuntimeException {
    public ApplicantNotFoundException(String message) {
        super(message);
    }
}
```

`{BE}/exception/GlobalExceptionHandler.java`의 `handleRetentionNotFound` `@ExceptionHandler` 목록에 `ApplicantNotFoundException.class`를 추가한다.

- [ ] **Step 5: 응답 DTO 생성**

`{BE}/dto/response/DataSubjectSummaryResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;

/**
 * 파기 대상자 검색 결과 행(Phase 10). 관리자 화면 규칙에 따라 이메일·휴대폰을 마스킹하지 않는다
 * (마스킹은 로그·감사 metadata 에만 적용).
 */
public record DataSubjectSummaryResponse(
        Long applicantId,
        String name,
        String email,
        String phoneNumber,
        long applicationCount,
        long purgedApplicationCount,
        LocalDateTime lastAppliedAt,
        boolean hasActiveHold
) {
}
```

`{BE}/dto/response/DataSubjectDetailResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파기 대상자 상세(Phase 10). 지원서별 적격성 판정을 함께 준다 — 화면은 이 값 하나로
 * "진행 중"(APPLICATION_NOT_TERMINAL)·"보류"(RETENTION_HOLD)·"이미 파기"(ALREADY_PURGED)를 표시한다.
 * hold 사유 원문은 주지 않는다(민감 자유 텍스트).
 */
public record DataSubjectDetailResponse(
        Long applicantId,
        String name,
        String email,
        String phoneNumber,
        boolean hasActiveHold,
        List<ApplicationRow> applications
) {
    public record ApplicationRow(
            Long applicationId,
            String jobPostingTitle,
            String status,
            LocalDateTime submittedAt,
            String purgeResult,
            boolean eligible,
            String reasonCode
    ) {
    }
}
```

- [ ] **Step 6: 조회 서비스 생성**

`{BE}/service/DataSubjectLookupService.java`:

```java
package com.shinyoung.recruit.service;

import ...;

/**
 * 파기 대상자 조회(Phase 10, 읽기 전용). 검색은 조건 1개 이상 필수 + 상한 50건 —
 * 전체 나열을 막는다(PII 대량 노출 방지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataSubjectLookupService {

    private static final int SEARCH_LIMIT = 50;

    private final ApplicantRepository applicantRepository;
    private final ApplicationPiiPurgeRepository applicationPiiPurgeRepository;
    private final RetentionHoldRepository retentionHoldRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final RetentionEligibilityService retentionEligibilityService;
    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final Clock clock;

    public List<DataSubjectSummaryResponse> search(String name, String phoneNumber, String email) {
        String normalizedName = trimToNull(name);
        String normalizedPhone = digitsToNull(phoneNumber);
        String normalizedEmail = trimToNull(email);
        if (normalizedName == null && normalizedPhone == null && normalizedEmail == null) {
            throw new InvalidRetentionRequestException("검색 조건을 1개 이상 입력해야 합니다.");
        }

        return applicantRepository
                .searchDataSubjects(normalizedName, normalizedPhone, normalizedEmail,
                        PageRequest.of(0, SEARCH_LIMIT))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public DataSubjectDetailResponse getDetail(Long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new ApplicantNotFoundException("지원자를 찾을 수 없습니다."));

        LocalDateTime scanAt = LocalDateTime.now(clock);
        List<JobApplication> applications = applicationPiiPurgeRepository.findByApplicantId(applicantId);
        boolean hasActiveHold = applications.stream()
                .anyMatch(application ->
                        retentionHoldRepository.existsByApplicationIdAndReleasedAtIsNull(application.getId()));

        List<DataSubjectDetailResponse.ApplicationRow> rows = applications.stream()
                .map(application -> toRow(application, scanAt))
                .toList();

        return new DataSubjectDetailResponse(
                applicant.getId(), applicant.getUserName(), applicant.getEmail(), applicant.getPhoneNumber(),
                hasActiveHold, rows);
    }
    // toSummary·toRow·trimToNull·digitsToNull 은 아래 Step 7 참고
}
```

- [ ] **Step 7: 헬퍼 메서드 구현**

같은 클래스 하단에 추가한다. 판정은 `RetentionDryRunService`가 쓰는 방식과 동일하게 조립한다(정책 선택 → finalStage → finalStageResult).

```java
    private DataSubjectSummaryResponse toSummary(Applicant applicant) {
        List<JobApplication> applications = applicationPiiPurgeRepository.findByApplicantId(applicant.getId());
        long purged = applications.stream().filter(a -> a.getPurgeResult() != null).count();
        LocalDateTime lastAppliedAt = applications.stream()
                .map(JobApplication::getSubmittedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        boolean hasActiveHold = applications.stream()
                .anyMatch(a -> retentionHoldRepository.existsByApplicationIdAndReleasedAtIsNull(a.getId()));

        return new DataSubjectSummaryResponse(
                applicant.getId(), applicant.getUserName(), applicant.getEmail(), applicant.getPhoneNumber(),
                applications.size(), purged, lastAppliedAt, hasActiveHold);
    }

    private DataSubjectDetailResponse.ApplicationRow toRow(JobApplication application, LocalDateTime scanAt) {
        Long jobPostingId = application.getJobPosting().getId();
        List<Stage> finalStages = stageRepository
                .findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId).stream()
                .filter(Stage::isFinalStage)
                .toList();
        StageResult finalStageResult = finalStages.size() == 1
                ? stageResultRepository
                        .findByStageIdAndJobApplicationIdIn(finalStages.get(0).getId(), List.of(application.getId()))
                        .stream().findFirst().orElse(null)
                : null;

        RetentionEligibilityService.EligibilityDecision decision = retentionEligibilityService.evaluate(
                application,
                application.getJobPosting(),
                retentionPolicyService.selectPolicy(jobPostingId, scanAt),
                retentionHoldRepository.existsByApplicationIdAndReleasedAtIsNull(application.getId()),
                finalStages,
                finalStageResult,
                scanAt);

        return new DataSubjectDetailResponse.ApplicationRow(
                application.getId(),
                application.getJobPostingTitleSnapshot(),
                application.getStatus().name(),
                application.getSubmittedAt(),
                application.getPurgeResult() == null ? null : application.getPurgeResult().name(),
                decision.eligible(),
                decision.reasonCode() == null ? null : decision.reasonCode().name());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 휴대폰은 숫자만 남긴다. 숫자가 없으면 조건 미입력으로 본다. */
    private String digitsToNull(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
```

- [ ] **Step 8: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.DataSubjectLookupServiceTest" --no-daemon
```
Expected: PASS (5건)

---

## Task 5: `ForcedPurgeService`

**Files:**
- Create: `{BE}/dto/request/ForcedPurgeRequest.java`
- Create: `{BE}/service/ForcedPurgeService.java`
- Modify: `{BE}/service/PurgeItemProcessor.java` (지원 이력 0건 계정 익명화 메서드)
- Test: `{BT}/service/ForcedPurgeServiceTest.java` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("confirm 이 true 가 아니면 400")
void requiresConfirm() {
    assertThatThrownBy(() -> forcedPurgeService.forcePurge(
            new ForcedPurgeRequest(applicantId, ForcedPurgeReason.DATA_SUBJECT_REQUEST, false), "tester"))
            .isInstanceOf(InvalidRetentionRequestException.class);
}

@Test
@DisplayName("active hold 가 하나라도 있으면 400 으로 거부한다")
void rejectsWhenHoldExists() {
    retentionHoldRepository.save(보류_생성(applicationId, "소송"));

    assertThatThrownBy(() -> forcedPurgeService.forcePurge(
            new ForcedPurgeRequest(applicantId, ForcedPurgeReason.DATA_SUBJECT_REQUEST, true), "tester"))
            .isInstanceOf(InvalidRetentionRequestException.class);
}

@Test
@DisplayName("보존기간이 남아 있어도 지원자의 모든 지원서를 파기하고 계정을 익명화한다")
void purgesAllApplicationsAndAnonymizesAccount() {
    PurgeBatchDetailResponse response = forcedPurgeService.forcePurge(
            new ForcedPurgeRequest(applicantId, ForcedPurgeReason.DATA_SUBJECT_REQUEST, true), "tester");

    assertThat(response.batch().purgedCount()).isEqualTo(2);
    Applicant applicant = applicantRepository.findById(applicantId).orElseThrow();
    assertThat(applicant.getUserName()).isNull();
    assertThat(applicant.getEmail()).isNull();
    assertThat(applicant.getLoginId()).isNull();
    assertThat(applicant.getCiHash()).startsWith("PURGED:");
}

@Test
@DisplayName("지원 이력이 0건인 계정도 익명화한다")
void anonymizesAccountWithoutApplications() {
    PurgeBatchDetailResponse response = forcedPurgeService.forcePurge(
            new ForcedPurgeRequest(지원없는_지원자_id, ForcedPurgeReason.DUPLICATE_ACCOUNT, true), "tester");

    assertThat(response.batch().totalCount()).isZero();
    assertThat(applicantRepository.findById(지원없는_지원자_id).orElseThrow().getCiHash()).startsWith("PURGED:");
}

@Test
@DisplayName("이미 파기된 지원자를 다시 요청하면 전부 SKIPPED 로 끝난다")
void secondRequestSkips() {
    forcedPurgeService.forcePurge(new ForcedPurgeRequest(applicantId, ForcedPurgeReason.OTHER, true), "tester");

    PurgeBatchDetailResponse second = forcedPurgeService.forcePurge(
            new ForcedPurgeRequest(applicantId, ForcedPurgeReason.OTHER, true), "tester");

    assertThat(second.batch().skippedCount()).isEqualTo(2);
    assertThat(second.batch().purgedCount()).isZero();
}

@Test
@DisplayName("없는 지원자는 404")
void notFound() {
    assertThatThrownBy(() -> forcedPurgeService.forcePurge(
            new ForcedPurgeRequest(999_999L, ForcedPurgeReason.OTHER, true), "tester"))
            .isInstanceOf(ApplicantNotFoundException.class);
}

@Test
@DisplayName("actor 가 비면 거부한다(ANONYMOUS 감사 차단)")
void requiresActor() {
    assertThatThrownBy(() -> forcedPurgeService.forcePurge(
            new ForcedPurgeRequest(applicantId, ForcedPurgeReason.OTHER, true), "  "))
            .isInstanceOf(InvalidRetentionRequestException.class);
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.ForcedPurgeServiceTest" --no-daemon
```
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 요청 DTO 생성**

`{BE}/dto/request/ForcedPurgeRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ForcedPurgeReason;
import jakarta.validation.constraints.NotNull;

/**
 * 강제 파기 요청(Phase 10). 사유는 선택형 enum 만 받는다 — 자유 텍스트는 감사 로그에 PII 를 남긴다.
 * {@code confirm} 은 비가역 파기의 명시적 확인.
 */
public record ForcedPurgeRequest(
        @NotNull(message = "applicantId는 필수입니다.")
        Long applicantId,

        @NotNull(message = "파기 사유는 필수입니다.")
        ForcedPurgeReason reasonCode,

        @NotNull(message = "confirm은 필수입니다.")
        Boolean confirm
) {
}
```

- [ ] **Step 4: 지원 이력 0건 계정 익명화 메서드 추가**

`{BE}/service/PurgeItemProcessor.java`에 추가한다. `ForcedPurgeService`(다른 빈)에서 호출하므로 프록시가 적용된다.

```java
    /**
     * 지원 이력이 없는 계정의 익명화(Phase 10 강제 파기). 지원서가 0건이면 item 처리 경로를 타지 않아
     * ref-count 익명화가 돌지 않으므로 별도 경로를 둔다. 이미 익명화된 계정은 건너뛴다.
     *
     * @return 실제로 익명화했으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean anonymizeApplicant(Long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new IllegalStateException("Applicant not found. id=" + applicantId));
        if (applicant.getCiHash() != null && applicant.getCiHash().startsWith("PURGED:")) {
            return false;
        }
        applicant.purgePersonalData("PURGED:" + UUID.randomUUID());
        return true;
    }
```

`ApplicantRepository` 의존성을 이 클래스에 추가한다(`private final ApplicantRepository applicantRepository;`).
`"PURGED:"` 접두 문자열은 기존 `anonymizeApplicantIfRefZero`가 쓰는 값과 같아야 한다 — 그 메서드를 열어 확인하고, 상수로 이미 빠져 있으면 그 상수를 쓴다.

- [ ] **Step 5: 오케스트레이션 서비스 생성**

`{BE}/service/ForcedPurgeService.java`:

```java
package com.shinyoung.recruit.service;

import ...;

/**
 * 강제 파기(정보주체 삭제 요청) 오케스트레이션(Phase 10).
 *
 * <p><b>클래스 트랜잭션이 없다</b> — batch lifecycle 과 item 처리(REQUIRES_NEW)가 각각 독립 커밋되는
 * 비원자 구조로, 기존 {@link PurgeExecutionService} 와 같다.
 *
 * <p>단위는 지원자 1명 전체다. 일부만 지우면 계정에 이름·연락처가 남아 "지체없이 삭제" 약속을 못 지킨다.
 * 보류(hold)가 하나라도 있으면 거부한다 — 법적 보존 의무가 삭제 요구권보다 우선한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForcedPurgeService {

    private final ApplicantRepository applicantRepository;
    private final ApplicationPiiPurgeRepository applicationPiiPurgeRepository;
    private final RetentionHoldRepository retentionHoldRepository;
    private final PurgeBatchLifecycleService purgeBatchLifecycleService;
    private final PurgeItemProcessor purgeItemProcessor;
    private final AttachmentPurgeSagaService attachmentPurgeSagaService;
    private final AuditHmac auditHmac;

    public PurgeBatchDetailResponse forcePurge(ForcedPurgeRequest request, String actor) {
        String resolvedActor = requireActor(actor);
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new InvalidRetentionRequestException("강제 파기는 confirm=true 명시가 필요합니다.");
        }
        Applicant applicant = applicantRepository.findById(request.applicantId())
                .orElseThrow(() -> new ApplicantNotFoundException("지원자를 찾을 수 없습니다."));

        List<Long> applicationIds = applicationPiiPurgeRepository.findByApplicantId(applicant.getId()).stream()
                .map(JobApplication::getId)
                .toList();
        // 보류가 하나라도 있으면 batch 를 만들기 전에 거부한다(원장에 흔적을 남기지 않는다).
        boolean hasActiveHold = applicationIds.stream()
                .anyMatch(retentionHoldRepository::existsByApplicationIdAndReleasedAtIsNull);
        if (hasActiveHold) {
            throw new InvalidRetentionRequestException("파기 보류가 걸려 있어 파기할 수 없습니다.");
        }

        PurgeBatch batch = purgeBatchLifecycleService.startForced(resolvedActor);

        long purged = 0;
        long pending = 0;
        long skipped = 0;
        long failed = 0;
        try {
            for (Long applicationId : applicationIds) {
                try {
                    PurgeItemProcessor.PurgeItemOutcome outcome =
                            purgeItemProcessor.process(batch.getId(), applicationId, batch.getScanAt(), true);
                    switch (outcome.status()) {
                        case PURGED -> purged++;
                        case PENDING -> {
                            if (completeBinaryDeletionSafely(batch.getId(), applicationId)) {
                                purged++;
                            } else {
                                pending++;
                            }
                        }
                        case SKIPPED -> skipped++;
                        default -> failed++;
                    }
                } catch (RuntimeException e) {
                    log.error("Forced purge item failed. batchId={}, applicationId={}",
                            batch.getId(), applicationId, e);
                    purgeItemProcessor.recordFailure(batch.getId(), applicationId);
                    failed++;
                }
            }
            // 지원서가 모두 파기됐으면(또는 0건이면) 계정 PII 도 지운다.
            if (failed == 0 && pending == 0 && skipped == 0) {
                purgeItemProcessor.anonymizeApplicant(applicant.getId());
            }
        } catch (RuntimeException e) {
            purgeBatchLifecycleService.failForced(batch.getId(), resolvedActor);
            throw e;
        }

        return purgeBatchLifecycleService.completeForced(
                batch.getId(),
                auditHmac.hmacHex("APPLICANT:" + applicant.getId()),
                request.reasonCode(),
                applicationIds.size(), purged, pending, skipped, failed,
                resolvedActor);
    }

    /** saga 예외는 PENDING 유지로 흡수 — 잔여는 reconcile 이 수습한다(기존 execute 와 동일). */
    private boolean completeBinaryDeletionSafely(Long batchId, Long applicationId) {
        try {
            return attachmentPurgeSagaService.completeBinaryDeletion(batchId, applicationId);
        } catch (RuntimeException e) {
            log.error("Attachment purge saga failed. batchId={}, applicationId={}", batchId, applicationId, e);
            return false;
        }
    }

    /** 비가역 파기는 관리자 행위 — actor 부재 시 ANONYMOUS 감사 차단. */
    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }
}
```

`auditHmac.hmacHex("APPLICANT:" + id)`의 접두는 기존 `AuditHmac` 규약(`APPLICANT:`)이다. 다른 접두를 쓰면 같은 사람을 과거 로그와 연결할 수 없다.

계정 익명화 조건이 `failed == 0 && pending == 0 && skipped == 0`인 이유: 남아 있는 지원서가 하나라도 있으면 그 지원서에서 지원자를 역참조할 수 있어야 하므로 계정 PII를 먼저 지우면 안 된다. `skipped`에는 이미 파기된 건도 포함되지만, 이 경우 `purgeItemProcessor.anonymizeApplicant`가 어차피 "이미 익명화"로 건너뛰므로 손해가 없다 — 단, 두 번째 요청에서 계정이 아직 익명화되지 않은 예외적 상태를 구제하려면 아래 조건으로 바꾼다.

```java
            boolean allCleared = applicationPiiPurgeRepository.findByApplicantId(applicant.getId()).stream()
                    .allMatch(application -> application.getPurgeResult() != null);
            if (allCleared) {
                purgeItemProcessor.anonymizeApplicant(applicant.getId());
            }
```

**이 두 번째 형태를 쓴다.** 상태를 다시 읽어 판단하므로 집계 카운터의 의미에 의존하지 않고, 지원서 0건(빈 스트림 → `allMatch` true)도 자연히 포함된다.

- [ ] **Step 6: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.ForcedPurgeServiceTest" --no-daemon
```
Expected: PASS (7건)

---

## Task 6: 강제 파기 엔드포인트와 권한

**Files:**
- Modify: `{BE}/controller/AdminRetentionController.java`
- Modify: `{BE}/config/SecurityConfig.java`
- Test: `{BT}/controller/AdminRetentionControllerTest.java`

> **주의:** `SecurityConfig`에는 이미 `GET /api/admin/retention/**` → RECRUIT·PRIVACY 매처가 있다. `data-subjects` GET을 PRIVACY 전용으로 만들려면 **그 매처보다 앞**에 넣어야 한다. POST `purge-batches/force`는 전용 매처가 없으면 broad `/api/admin/**`(ADMIN·RECRUIT)로 열린다.

- [ ] **Step 1: 실패하는 권한 테스트 작성**

`{BT}/controller/AdminRetentionControllerTest.java`에 기존 권한 행렬 테스트와 같은 스타일로 추가한다.

```java
@Test
@DisplayName("GET data-subjects — 미인증 401")
void searchUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/admin/retention/data-subjects").param("name", "홍길동"))
            .andExpect(status().isUnauthorized());
}

@Test
@WithMockUser(authorities = {"ROLE_RECRUIT_ADMIN"})
@DisplayName("GET data-subjects — RECRUIT_ADMIN 만으로는 403")
void searchForbiddenForRecruitAdmin() throws Exception {
    mockMvc.perform(get("/api/admin/retention/data-subjects").param("name", "홍길동"))
            .andExpect(status().isForbidden());
}

@Test
@WithMockUser(authorities = {"ROLE_PRIVACY_ADMIN"})
@DisplayName("GET data-subjects — 조건이 없으면 400")
void searchRequiresCondition() throws Exception {
    mockMvc.perform(get("/api/admin/retention/data-subjects"))
            .andExpect(status().isBadRequest());
}

@Test
@WithMockUser(authorities = {"ROLE_ADMIN", "ROLE_RECRUIT_ADMIN"})
@DisplayName("POST purge-batches/force — PRIVACY 없으면 403")
void forceForbidden() throws Exception {
    mockMvc.perform(post("/api/admin/retention/purge-batches/force")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"applicantId\":1,\"reasonCode\":\"DATA_SUBJECT_REQUEST\",\"confirm\":true}")
                    .with(csrf()))
            .andExpect(status().isForbidden());
}

@Test
@WithMockUser(authorities = {"ROLE_PRIVACY_ADMIN"})
@DisplayName("POST purge-batches/force — confirm 이 false 면 400")
void forceRequiresConfirm() throws Exception {
    mockMvc.perform(post("/api/admin/retention/purge-batches/force")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"applicantId\":1,\"reasonCode\":\"DATA_SUBJECT_REQUEST\",\"confirm\":false}")
                    .with(csrf()))
            .andExpect(status().isBadRequest());
}
```

기존 파일에서 인증 주체를 만드는 방식(`@WithMockUser` 또는 커스텀 principal)을 먼저 확인하고 동일하게 맞춘다. `CurrentEmployeeService.getCurrentEmployeeActor`가 임직원 principal을 요구하므로, 200을 기대하는 테스트는 기존 execute 테스트가 쓰는 주체 구성을 그대로 복사한다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --no-daemon
```
Expected: FAIL — 404(엔드포인트 없음) 또는 권한 불일치.

- [ ] **Step 3: 컨트롤러 엔드포인트 추가**

`{BE}/controller/AdminRetentionController.java`에 필드 2개와 엔드포인트 3개를 추가한다.

```java
    private final DataSubjectLookupService dataSubjectLookupService;
    private final ForcedPurgeService forcedPurgeService;

    // ---- 파기 대상자(정보주체) 조회·강제 파기 ----

    /** 이름·휴대폰·이메일로 파기 대상자 검색(조건 1개 이상 필수, 상한 50건). */
    @GetMapping("/data-subjects")
    public ResponseEntity<ApiResponse<List<DataSubjectSummaryResponse>>> searchDataSubjects(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String email
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dataSubjectLookupService.search(name, phoneNumber, email)));
    }

    /** 지원자 1명의 지원서 목록과 지원서별 적격성 판정. hold 사유 원문은 주지 않는다. */
    @GetMapping("/data-subjects/{applicantId}")
    public ResponseEntity<ApiResponse<DataSubjectDetailResponse>> getDataSubject(@PathVariable Long applicantId) {
        return ResponseEntity.ok(ApiResponse.success(dataSubjectLookupService.getDetail(applicantId)));
    }

    /**
     * 강제 파기(정보주체 삭제 요청). 보존기간과 무관하게 지원자 1명의 모든 지원서와 계정을 파기한다.
     * 보류가 걸려 있으면 400.
     */
    @PostMapping("/purge-batches/force")
    public ResponseEntity<ApiResponse<PurgeBatchDetailResponse>> forcePurge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ForcedPurgeRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(forcedPurgeService.forcePurge(request, actor)));
    }
```

- [ ] **Step 4: SecurityConfig 매처 추가**

`{BE}/config/SecurityConfig.java`에서 `.requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/execute")` 줄 **바로 위**에 아래 두 줄을 넣는다.

```java
                // 강제 파기(Phase 10) — 비가역 + 보존기간 무시라 PRIVACY 전용.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/force").hasAuthority(RoleNames.PRIVACY_ADMIN)
```

그리고 `.requestMatchers(HttpMethod.GET, "/api/admin/retention/holds/**")` 줄 **바로 아래**에 넣는다.

```java
                // 파기 대상자 조회는 지원자 원문 PII 를 반환한다 — broad GET retention/** 보다 먼저 PRIVACY 로 좁힌다.
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/data-subjects/**").hasAuthority(RoleNames.PRIVACY_ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/admin/retention/data-subjects").hasAuthority(RoleNames.PRIVACY_ADMIN)
```

두 줄 모두 `.requestMatchers(HttpMethod.GET, "/api/admin/retention/**")`보다 **위**에 있어야 한다.

- [ ] **Step 5: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --no-daemon
```
Expected: PASS

- [ ] **Step 6: S1 전체 회귀 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.Purge*" --tests "com.shinyoung.recruit.service.Retention*" --tests "com.shinyoung.recruit.service.ForcedPurge*" --tests "com.shinyoung.recruit.service.DataSubject*" --tests "com.shinyoung.recruit.service.Audit*" --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --no-daemon
```
Expected: PASS

---

# S2 — 자동 파기 백엔드

## Task 7: 설정 엔티티와 DDL

**Files:**
- Create: `{BE}/enumeration/RetentionScheduleRunResult.java`
- Create: `{BE}/domain/entity/RetentionScheduleSetting.java`
- Create: `{BE}/domain/repository/RetentionScheduleSettingRepository.java`
- Create: `recruit_back/recruit_backend/docs/ops/phase-10-retention-schedule-ddl.sql`
- Test: `{BT}/domain/repository/RetentionScheduleSettingRepositoryTest.java` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("단일 행 설정을 저장하고 다시 읽는다")
void saveAndFind() {
    RetentionScheduleSetting setting = RetentionScheduleSetting.initial();
    setting.updateEnabled(true, "tester", LocalDateTime.now());

    retentionScheduleSettingRepository.save(setting);

    RetentionScheduleSetting found = retentionScheduleSettingRepository.findById(1L).orElseThrow();
    assertThat(found.isEnabled()).isTrue();
    assertThat(found.getUpdatedBy()).isEqualTo("tester");
}

@Test
@DisplayName("실행 결과를 기록한다")
void recordRun() {
    RetentionScheduleSetting setting = retentionScheduleSettingRepository.save(RetentionScheduleSetting.initial());

    setting.recordRun(RetentionScheduleRunResult.SKIPPED_NOT_DUE, null, LocalDateTime.now());
    retentionScheduleSettingRepository.flush();

    RetentionScheduleSetting found = retentionScheduleSettingRepository.findById(1L).orElseThrow();
    assertThat(found.getLastRunResult()).isEqualTo(RetentionScheduleRunResult.SKIPPED_NOT_DUE);
    assertThat(found.getLastRunBatchId()).isNull();
    assertThat(found.getLastRunAt()).isNotNull();
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.domain.repository.RetentionScheduleSettingRepositoryTest" --no-daemon
```
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 실행 결과 enum 생성**

`{BE}/enumeration/RetentionScheduleRunResult.java`:

```java
package com.shinyoung.recruit.enumeration;

/** 자동 파기 스케줄 1회 기동의 결과(Phase 10). 화면이 마지막 실행 상태를 보여 주는 데 쓴다. */
public enum RetentionScheduleRunResult {
    /** 자동 파기가 꺼져 있어 스캔하지 않음. */
    SKIPPED_DISABLED,
    /** 다음 파기 예정일 전이라 스캔하지 않음. */
    SKIPPED_NOT_DUE,
    /** 스캔했으나 적격 건이 0건. */
    NO_TARGET,
    /** 파기를 실행함. */
    EXECUTED,
    /** 실행 중 예외 발생. */
    ERROR
}
```

- [ ] **Step 4: 엔티티 생성**

`{BE}/domain/entity/RetentionScheduleSetting.java`:

```java
package com.shinyoung.recruit.domain.entity;

import ...;

/**
 * 자동 파기 스케줄 설정(Phase 10). <b>단일 행</b>(id 고정 1)이다.
 *
 * <p>{@code RetentionPolicy.enabled} 를 재사용하지 않는 이유 — 정책을 끄면 적격성 판정이
 * {@code POLICY_NOT_FOUND} 가 되어 강제 파기 화면의 판정 표시와 감사 사유까지 오염된다.
 * "스케줄을 돌릴 것인가"와 "보존 정책이 무엇인가"는 다른 스위치다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "retention_schedule_setting")
public class RetentionScheduleSetting {

    /** 단일 행 고정 id. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_run_result", length = 30)
    private RetentionScheduleRunResult lastRunResult;

    @Column(name = "last_run_batch_id")
    private Long lastRunBatchId;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 행이 없을 때 쓰는 기본값 — 꺼짐(안전 기본값). */
    public static RetentionScheduleSetting initial() {
        RetentionScheduleSetting setting = new RetentionScheduleSetting();
        setting.id = SINGLETON_ID;
        setting.enabled = false;
        return setting;
    }

    public void updateEnabled(boolean enabled, String updatedBy, LocalDateTime updatedAt) {
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void recordRun(RetentionScheduleRunResult result, Long batchId, LocalDateTime runAt) {
        this.lastRunResult = result;
        this.lastRunBatchId = batchId;
        this.lastRunAt = runAt;
    }
}
```

- [ ] **Step 5: 리포지토리 생성**

`{BE}/domain/repository/RetentionScheduleSettingRepository.java`:

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.RetentionScheduleSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** 단일 행 설정이라 id 조회·저장만 쓴다. */
public interface RetentionScheduleSettingRepository extends JpaRepository<RetentionScheduleSetting, Long> {
}
```

- [ ] **Step 6: 운영 DDL 작성**

`recruit_back/recruit_backend/docs/ops/phase-10-retention-schedule-ddl.sql`:

```sql
-- Phase 10: 자동 파기 스케줄 설정(단일 행)
-- 운영 DB는 ddl-auto=validate/none 이므로 수동 반영한다.
CREATE TABLE retention_schedule_setting (
    id                BIGINT       NOT NULL,
    enabled           TINYINT(1)   NOT NULL DEFAULT 0,
    last_run_at       DATETIME     NULL,
    last_run_result   VARCHAR(30)  NULL,
    last_run_batch_id BIGINT       NULL,
    updated_by        VARCHAR(100) NULL,
    updated_at        DATETIME     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 초기 행. 안전 기본값은 꺼짐이며, 운영에서는 보존 정책을 먼저 등록한 뒤 화면에서 켠다.
INSERT INTO retention_schedule_setting (id, enabled) VALUES (1, 0);
```

- [ ] **Step 7: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.domain.repository.RetentionScheduleSettingRepositoryTest" --no-daemon
```
Expected: PASS

---

## Task 8: 다음 파기 예정일 계산

**Files:**
- Modify: `{BE}/domain/repository/JobApplicationRepository.java`
- Create: `{BE}/service/RetentionScheduleService.java` (예정일 계산 부분만)
- Test: `{BT}/service/RetentionScheduleServiceTest.java` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test
@DisplayName("마감된 공고가 없으면 9999-12-31")
void noClosedPostingMeansNoSchedule() {
    assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(9999, 12, 31));
}

@Test
@DisplayName("전역 정책이 없으면 9999-12-31")
void noPolicyMeansNoSchedule() {
    마감공고와_지원서_생성(LocalDateTime.of(2020, 1, 1, 0, 0));

    assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(9999, 12, 31));
}

@Test
@DisplayName("가장 이른 마감일 + 보존기간이 예정일이다")
void earliestClosedAtPlusPeriod() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);
    마감공고와_지원서_생성(LocalDateTime.of(2024, 3, 10, 9, 0));
    마감공고와_지원서_생성(LocalDateTime.of(2025, 6, 1, 9, 0));

    assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(2029, 3, 9));
}

@Test
@DisplayName("이미 파기된 지원서만 있는 공고는 계산에서 제외한다")
void purgedApplicationsExcluded() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);
    JobApplication application = 마감공고와_지원서_생성(LocalDateTime.of(2024, 3, 10, 9, 0));
    application.markPurged(1L, LocalDateTime.now());
    jobApplicationRepository.save(application);

    assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(9999, 12, 31));
}
```

기대값 `2029-03-09`는 `2024-03-10T09:00 + 1825일 = 2029-03-09T09:00`의 날짜 부분이다. 실제 계산 결과로 검증하지 말고 이 값을 고정한다(윤년 포함 계산이 맞는지 확인하는 것이 목적).

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.RetentionScheduleServiceTest" --no-daemon
```
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 리포지토리 쿼리 추가**

`{BE}/domain/repository/JobApplicationRepository.java`:

```java
    /**
     * 아직 파기되지 않은 지원서를 가진 마감 공고 중 가장 이른 마감 시각(Phase 10 스케줄 게이트).
     * 결과가 없으면 파기 예정이 없다는 뜻이다.
     */
    @Query("""
            select min(application.jobPosting.closedAt)
              from JobApplication application
             where application.purgeResult is null
               and application.jobPosting.closedAt is not null""")
    Optional<LocalDateTime> findEarliestUnpurgedClosedAt();
```

JPQL `min()`은 행이 없으면 `null`을 돌려주므로 `Optional`이 비어 있지 않고 `Optional.of(null)`이 될 수 없다 — Spring Data가 null을 `Optional.empty()`로 감싼다. 서비스에서 한 번 더 null 체크할 필요는 없다.

- [ ] **Step 4: 예정일 계산 구현**

`{BE}/service/RetentionScheduleService.java`(이 Task에서는 계산 메서드만 만든다. 토글·실행 기록은 Task 9):

```java
package com.shinyoung.recruit.service;

import ...;

/**
 * 자동 파기 스케줄 설정과 다음 파기 예정일(Phase 10).
 *
 * <p>예정일은 <b>보존기간만</b> 계산한 하한이다 — 적격성 9단계(전형 종료 여부 등)를 반영하지 않으므로
 * 그날 실제 파기 건수가 0일 수 있다. 목적은 "이 날짜 전에는 파기 대상이 절대 없다"를 보장해
 * 불필요한 전량 스캔과 {@code purge_job_item} 누적을 막는 것이다.
 */
@Service
@RequiredArgsConstructor
public class RetentionScheduleService {

    /** 파기 예정이 없음을 뜻하는 약속된 값. */
    public static final LocalDate NO_SCHEDULE_DATE = LocalDate.of(9999, 12, 31);

    private final RetentionScheduleSettingRepository retentionScheduleSettingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LocalDate nextPurgeDate() {
        RetentionPolicySelection selection =
                retentionPolicyService.selectPolicy(null, LocalDateTime.now(clock));
        if (!selection.selected()) {
            return NO_SCHEDULE_DATE;
        }
        return jobApplicationRepository.findEarliestUnpurgedClosedAt()
                .map(closedAt -> closedAt.plusDays(selection.policy().getRetentionPeriodDays()).toLocalDate())
                .orElse(NO_SCHEDULE_DATE);
    }
}
```

`retentionPolicyService.selectPolicy(null, scanAt)`는 전역 정책을 고르는 기존 메서드다. 공고별 override는 스케줄 예정일 계산에서 고려하지 않는다 — 예정일은 하한이면 충분하고, override가 있으면 그 공고는 더 늦게 만료되므로 하한이 깨지지 않는다. 이 이유를 메서드 주석에 남긴다.

- [ ] **Step 5: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.RetentionScheduleServiceTest" --no-daemon
```
Expected: PASS (4건)

---

## Task 9: 설정 조회·토글·실행 기록

**Files:**
- Modify: `{BE}/service/RetentionScheduleService.java`
- Create: `{BE}/dto/response/RetentionScheduleResponse.java`
- Create: `{BE}/dto/request/RetentionScheduleRequest.java`
- Test: `{BT}/service/RetentionScheduleServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

```java
@Test
@DisplayName("설정 행이 없으면 꺼짐으로 본다")
void defaultsToDisabled() {
    RetentionScheduleResponse response = retentionScheduleService.getSchedule();

    assertThat(response.enabled()).isFalse();
    assertThat(response.lastRunAt()).isNull();
}

@Test
@DisplayName("토글하면 행을 만들고 감사를 남긴다")
void toggleCreatesRowAndAudit() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);

    RetentionScheduleResponse response = retentionScheduleService.updateEnabled(true, "tester");

    assertThat(response.enabled()).isTrue();
    assertThat(activityLogRepository.findAll()).anySatisfy(log -> {
        assertThat(log.getActionType()).isEqualTo(AuditActionType.RETENTION_POLICY_UPDATE);
        assertThat(log.getMetadataJson()).contains("SCHEDULE_ENABLE");
    });
}

@Test
@DisplayName("정책이 없으면 켤 수 없다")
void cannotEnableWithoutPolicy() {
    assertThatThrownBy(() -> retentionScheduleService.updateEnabled(true, "tester"))
            .isInstanceOf(InvalidRetentionRequestException.class);
}

@Test
@DisplayName("actor 가 비면 토글을 거부한다")
void toggleRequiresActor() {
    assertThatThrownBy(() -> retentionScheduleService.updateEnabled(true, " "))
            .isInstanceOf(InvalidRetentionRequestException.class);
}

@Test
@DisplayName("응답에 정책 유무와 보존기간이 함께 담긴다")
void responseCarriesPolicyInfo() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);

    RetentionScheduleResponse response = retentionScheduleService.getSchedule();

    assertThat(response.hasPolicy()).isTrue();
    assertThat(response.retentionPeriodDays()).isEqualTo(1825);
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.RetentionScheduleServiceTest" --no-daemon
```
Expected: FAIL — `getSchedule` 없음.

- [ ] **Step 3: DTO 생성**

`{BE}/dto/response/RetentionScheduleResponse.java`:

```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 자동 파기 설정 응답(Phase 10). {@code nextPurgeDate} 가 9999-12-31 이면 파기 예정이 없다는 뜻이다.
 */
public record RetentionScheduleResponse(
        boolean enabled,
        LocalDate nextPurgeDate,
        boolean hasPolicy,
        Integer retentionPeriodDays,
        LocalDateTime lastRunAt,
        RetentionScheduleRunResult lastRunResult,
        Long lastRunBatchId
) {
}
```

`{BE}/dto/request/RetentionScheduleRequest.java`:

```java
package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;

/** 자동 파기 on/off 요청(Phase 10). */
public record RetentionScheduleRequest(
        @NotNull(message = "enabled는 필수입니다.")
        Boolean enabled
) {
}
```

- [ ] **Step 4: 조회·토글·실행 기록 구현**

`{BE}/service/RetentionScheduleService.java`에 추가한다.

```java
    @Transactional(readOnly = true)
    public RetentionScheduleResponse getSchedule() {
        RetentionScheduleSetting setting = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID)
                .orElseGet(RetentionScheduleSetting::initial);
        RetentionPolicySelection selection =
                retentionPolicyService.selectPolicy(null, LocalDateTime.now(clock));

        return new RetentionScheduleResponse(
                setting.isEnabled(),
                nextPurgeDate(),
                selection.selected(),
                selection.selected() ? selection.policy().getRetentionPeriodDays() : null,
                setting.getLastRunAt(),
                setting.getLastRunResult(),
                setting.getLastRunBatchId());
    }

    /**
     * 자동 파기 on/off. 정책이 없으면 켜도 전건 스킵되므로 켜기 자체를 막는다.
     * 감사는 보존 설정 변경이라는 성격이 같아 {@code RETENTION_POLICY_UPDATE} 를 재사용한다.
     */
    @Transactional
    public RetentionScheduleResponse updateEnabled(boolean enabled, String actor) {
        String resolvedActor = requireActor(actor);
        RetentionPolicySelection selection =
                retentionPolicyService.selectPolicy(null, LocalDateTime.now(clock));
        if (enabled && !selection.selected()) {
            throw new InvalidRetentionRequestException("보존 정책을 먼저 등록해야 자동 파기를 켤 수 있습니다.");
        }

        RetentionScheduleSetting setting = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID)
                .orElseGet(RetentionScheduleSetting::initial);
        setting.updateEnabled(enabled, resolvedActor, LocalDateTime.now(clock));
        retentionScheduleSettingRepository.save(setting);

        AuditActorContext context = auditRequestContextResolver.resolve(resolvedActor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.RETENTION_POLICY_UPDATE)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.RETENTION_POLICY)
                .targetId(String.valueOf(RetentionScheduleSetting.SINGLETON_ID))
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(new RetentionPolicyChangeMetadata(
                        RetentionScheduleSetting.SINGLETON_ID,
                        enabled ? "SCHEDULE_ENABLE" : "SCHEDULE_DISABLE"))
                .build());

        return getSchedule();
    }

    /** 스케줄 1회 기동 결과 기록(화면 표시용). 감사는 dry-run·execute 가 각자 남긴다. */
    @Transactional
    public void recordRun(RetentionScheduleRunResult result, Long batchId) {
        RetentionScheduleSetting setting = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID)
                .orElseGet(RetentionScheduleSetting::initial);
        setting.recordRun(result, batchId, LocalDateTime.now(clock));
        retentionScheduleSettingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return retentionScheduleSettingRepository.findById(RetentionScheduleSetting.SINGLETON_ID)
                .map(RetentionScheduleSetting::isEnabled)
                .orElse(false);
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }
```

`RetentionPolicyChangeMetadata`의 실제 필드 구성은 `{BE}/service/RetentionPolicyChangeMetadata.java`를 열어 확인하고 맞춘다. `operation` 필드가 문자열이면 위 코드가 그대로 맞고, 필드 수가 다르면 그 시그니처에 맞춘다. `AuditTargetType.RETENTION_POLICY` 값 이름도 `{BE}/enumeration/AuditTargetType.java`에서 확인한다.

- [ ] **Step 5: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.RetentionScheduleServiceTest" --no-daemon
```
Expected: PASS (9건)

---

## Task 10: SYSTEM actor 감사

**Files:**
- Modify: `{BE}/service/AuditRequestContextResolver.java`
- Test: `{BT}/service/AuditRequestContextResolverTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

기존 테스트 파일이 있으면 거기에, 없으면 새로 만든다.

```java
@Test
@DisplayName("SYSTEM 예약 actorId 는 ActorType.SYSTEM 으로 기록한다")
void systemActor() {
    AuditActorContext context = auditRequestContextResolver.resolve(
            AuditRequestContextResolver.SYSTEM_ACTOR_ID);

    assertThat(context.actorType()).isEqualTo(ActorType.SYSTEM);
    assertThat(context.actorId()).isEqualTo("SYSTEM");
}

@Test
@DisplayName("일반 임직원 actorId 는 EMPLOYEE 로 기록한다")
void employeeActor() {
    AuditActorContext context = auditRequestContextResolver.resolve("emp001");

    assertThat(context.actorType()).isEqualTo(ActorType.EMPLOYEE);
}

@Test
@DisplayName("actorId 가 없으면 ANONYMOUS")
void anonymousActor() {
    assertThat(auditRequestContextResolver.resolve(null).actorType()).isEqualTo(ActorType.ANONYMOUS);
}
```

이 테스트는 SecurityContext가 비어 있어야 한다. 스프링 통합 테스트라면 `SecurityContextHolder.clearContext()`를 `@BeforeEach`에 넣는다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.AuditRequestContextResolverTest" --no-daemon
```
Expected: FAIL — `SYSTEM_ACTOR_ID` 없음.

- [ ] **Step 3: 구현**

`{BE}/service/AuditRequestContextResolver.java`:

```java
    /**
     * 스케줄러 등 사람이 아닌 실행의 예약 actorId(Phase 10). 임직원 loginId 는 LDAP 사번 체계라
     * 이 값과 충돌하지 않는다. 이 값이면 감사에 {@code ActorType.SYSTEM} 으로 남는다 —
     * 자동 실행이 임직원 행위로 기록되면 감사 로그가 거짓이 된다.
     */
    public static final String SYSTEM_ACTOR_ID = "SYSTEM";
```

`resolve` 안의 actorType 결정 부분을 바꾼다.

```java
        ActorType actorType;
        if (actorId == null || actorId.isBlank()) {
            actorType = ActorType.ANONYMOUS;
        } else if (SYSTEM_ACTOR_ID.equals(actorId)) {
            actorType = ActorType.SYSTEM;
        } else {
            actorType = ActorType.EMPLOYEE;
        }
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.Audit*" --no-daemon
```
Expected: PASS

---

## Task 11: 스케줄러

**Files:**
- Create: `{BE}/service/RetentionPurgeScheduler.java`
- Modify: `recruit_back/recruit_backend/src/main/resources/application.yaml`
- Test: `{BT}/service/RetentionPurgeSchedulerTest.java` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

이 테스트는 `@MockitoBean`(또는 프로젝트가 쓰는 mock 방식)으로 `RetentionDryRunService`·`PurgeExecutionService`를 대체해 호출 여부를 검증한다. 기존 테스트에서 쓰는 mock 애너테이션을 먼저 확인하고 맞춘다.

```java
@Test
@DisplayName("꺼져 있으면 dry-run 을 호출하지 않고 SKIPPED_DISABLED 를 기록한다")
void skipsWhenDisabled() {
    설정_저장(false);

    retentionPurgeScheduler.runScheduledPurge();

    verifyNoInteractions(retentionDryRunService);
    assertThat(설정_조회().getLastRunResult()).isEqualTo(RetentionScheduleRunResult.SKIPPED_DISABLED);
}

@Test
@DisplayName("예정일 전이면 dry-run 을 호출하지 않고 SKIPPED_NOT_DUE 를 기록한다")
void skipsWhenNotDue() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);
    마감공고와_지원서_생성(LocalDateTime.now(clock).minusDays(10));
    설정_저장(true);

    retentionPurgeScheduler.runScheduledPurge();

    verifyNoInteractions(retentionDryRunService);
    assertThat(설정_조회().getLastRunResult()).isEqualTo(RetentionScheduleRunResult.SKIPPED_NOT_DUE);
}

@Test
@DisplayName("적격 0건이면 execute 를 호출하지 않고 NO_TARGET 을 기록한다")
void noTarget() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);
    마감공고와_지원서_생성(LocalDateTime.now(clock).minusDays(2000));
    설정_저장(true);
    given(retentionDryRunService.dryRun(anyString())).willReturn(적격_0건_응답(batchId));

    retentionPurgeScheduler.runScheduledPurge();

    verifyNoInteractions(purgeExecutionService);
    assertThat(설정_조회().getLastRunResult()).isEqualTo(RetentionScheduleRunResult.NO_TARGET);
}

@Test
@DisplayName("적격 건이 있으면 근거 batch 로 execute 를 호출하고 EXECUTED 를 기록한다")
void executes() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);
    마감공고와_지원서_생성(LocalDateTime.now(clock).minusDays(2000));
    설정_저장(true);
    given(retentionDryRunService.dryRun(anyString())).willReturn(적격_2건_응답(11L));
    given(purgeExecutionService.execute(any(), anyString())).willReturn(execute_응답(12L));

    retentionPurgeScheduler.runScheduledPurge();

    ArgumentCaptor<PurgeExecuteRequest> captor = ArgumentCaptor.forClass(PurgeExecuteRequest.class);
    verify(purgeExecutionService).execute(captor.capture(), eq("SYSTEM"));
    assertThat(captor.getValue().confirm()).isTrue();
    assertThat(captor.getValue().sourceDryRunBatchId()).isEqualTo(11L);
    assertThat(captor.getValue().applicationId()).isNull();
    assertThat(설정_조회().getLastRunResult()).isEqualTo(RetentionScheduleRunResult.EXECUTED);
    assertThat(설정_조회().getLastRunBatchId()).isEqualTo(12L);
}

@Test
@DisplayName("예외가 나도 전파하지 않고 ERROR 를 기록한다")
void swallowsException() {
    전역정책_생성(1825, RetentionBaselineType.CLOSED_AT);
    마감공고와_지원서_생성(LocalDateTime.now(clock).minusDays(2000));
    설정_저장(true);
    given(retentionDryRunService.dryRun(anyString())).willThrow(new RuntimeException("boom"));

    assertThatCode(() -> retentionPurgeScheduler.runScheduledPurge()).doesNotThrowAnyException();
    assertThat(설정_조회().getLastRunResult()).isEqualTo(RetentionScheduleRunResult.ERROR);
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.RetentionPurgeSchedulerTest" --no-daemon
```
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 스케줄러 구현**

`{BE}/service/RetentionPurgeScheduler.java`:

```java
package com.shinyoung.recruit.service;

import ...;

/**
 * 보존기간 만료 지원서 자동 파기 스케줄러(Phase 10). 기본 매일 03:00, cron 은 외부 설정.
 *
 * <p><b>게이트 2단</b> — ① 자동 파기 off ② 다음 파기 예정일 전이면 스캔하지 않는다.
 * 게이트가 없으면 dry-run 이 매일 밤 전 지원서를 훑고 지원서 수만큼 {@code purge_job_item} 행을 쌓는다.
 * 대상이 0건인 기간에도 그렇다.
 *
 * <p>{@link ClientEventLogCleanupScheduler} 와 같은 모양으로 예외를 전파하지 않는다 —
 * 실패가 스케줄링 스레드와 다음 실행에 영향을 주지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionPurgeScheduler {

    private final RetentionScheduleService retentionScheduleService;
    private final RetentionDryRunService retentionDryRunService;
    private final PurgeExecutionService purgeExecutionService;
    private final Clock clock;

    @Scheduled(cron = "${retention.purge-cron:0 0 3 * * *}")
    public void runScheduledPurge() {
        try {
            if (!retentionScheduleService.isEnabled()) {
                retentionScheduleService.recordRun(RetentionScheduleRunResult.SKIPPED_DISABLED, null);
                log.info("Retention purge skipped: disabled");
                return;
            }
            LocalDate nextPurgeDate = retentionScheduleService.nextPurgeDate();
            if (nextPurgeDate.isAfter(LocalDate.now(clock))) {
                retentionScheduleService.recordRun(RetentionScheduleRunResult.SKIPPED_NOT_DUE, null);
                log.info("Retention purge skipped: not due until {}", nextPurgeDate);
                return;
            }

            PurgeBatchDetailResponse dryRun = retentionDryRunService.dryRun(
                    AuditRequestContextResolver.SYSTEM_ACTOR_ID);
            long eligibleCount = dryRun.batch().eligibleCount();
            if (eligibleCount == 0) {
                retentionScheduleService.recordRun(RetentionScheduleRunResult.NO_TARGET, null);
                log.info("Retention purge found no target. dryRunBatchId={}", dryRun.batch().id());
                return;
            }

            PurgeBatchDetailResponse executed = purgeExecutionService.execute(
                    new PurgeExecuteRequest(true, dryRun.batch().id(), null),
                    AuditRequestContextResolver.SYSTEM_ACTOR_ID);
            retentionScheduleService.recordRun(RetentionScheduleRunResult.EXECUTED, executed.batch().id());
            log.info("Retention purge executed. batchId={}, purged={}, skipped={}, failed={}",
                    executed.batch().id(), executed.batch().purgedCount(),
                    executed.batch().skippedCount(), executed.batch().failedCount());
        } catch (Exception e) {
            log.error("Retention purge failed", e);
            recordErrorSafely();
        }
    }

    /** 결과 기록까지 실패하면 더 할 수 있는 일이 없다 — 로그만 남기고 삼킨다. */
    private void recordErrorSafely() {
        try {
            retentionScheduleService.recordRun(RetentionScheduleRunResult.ERROR, null);
        } catch (RuntimeException e) {
            log.error("Retention purge result recording also failed", e);
        }
    }
}
```

`PurgeBatchDetailResponse`의 접근자 이름(`batch()`, `eligibleCount()`, `id()`)은 `{BE}/dto/response/PurgeBatchDetailResponse.java`와 `PurgeBatchResponse.java`를 열어 확인하고 맞춘다.

- [ ] **Step 4: cron 설정 추가**

`recruit_back/recruit_backend/src/main/resources/application.yaml`의 `client-event-log:` 블록 근처, 같은 최상위 레벨에 추가한다.

```yaml
retention:
  # 보존기간 만료 지원서 자동 파기. 게이트(자동 파기 on/off, 다음 파기 예정일)를 통과할 때만 실제 스캔한다.
  purge-cron: ${RETENTION_PURGE_CRON:0 0 3 * * *}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.RetentionPurgeSchedulerTest" --no-daemon
```
Expected: PASS (5건)

---

## Task 12: 스케줄 설정 엔드포인트와 권한

**Files:**
- Modify: `{BE}/controller/AdminRetentionController.java`
- Modify: `{BE}/config/SecurityConfig.java`
- Test: `{BT}/controller/AdminRetentionControllerTest.java`

- [ ] **Step 1: 실패하는 권한 테스트 추가**

```java
@Test
@WithMockUser(authorities = {"ROLE_RECRUIT_ADMIN"})
@DisplayName("GET schedule — RECRUIT_ADMIN 도 조회할 수 있다")
void scheduleReadableByRecruitAdmin() throws Exception {
    mockMvc.perform(get("/api/admin/retention/schedule"))
            .andExpect(status().isOk());
}

@Test
@WithMockUser(authorities = {"ROLE_ADMIN", "ROLE_RECRUIT_ADMIN"})
@DisplayName("POST schedule — PRIVACY 없으면 403")
void scheduleToggleForbidden() throws Exception {
    mockMvc.perform(post("/api/admin/retention/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true}")
                    .with(csrf()))
            .andExpect(status().isForbidden());
}

@Test
@DisplayName("POST schedule — 미인증 401")
void scheduleToggleUnauthenticated() throws Exception {
    mockMvc.perform(post("/api/admin/retention/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true}")
                    .with(csrf()))
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --no-daemon
```
Expected: FAIL — 404.

- [ ] **Step 3: 엔드포인트 추가**

`{BE}/controller/AdminRetentionController.java`:

```java
    private final RetentionScheduleService retentionScheduleService;

    // ---- 자동 파기 스케줄 설정 ----

    /** 자동 파기 on/off, 다음 파기 예정일, 마지막 실행 결과. */
    @GetMapping("/schedule")
    public ResponseEntity<ApiResponse<RetentionScheduleResponse>> getSchedule() {
        return ResponseEntity.ok(ApiResponse.success(retentionScheduleService.getSchedule()));
    }

    /** 자동 파기 켜기/끄기. 보존 정책이 없으면 켤 수 없다(켜도 전건 스킵되므로). */
    @PostMapping("/schedule")
    public ResponseEntity<ApiResponse<RetentionScheduleResponse>> updateSchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RetentionScheduleRequest request
    ) {
        String actor = currentEmployeeService.getCurrentEmployeeActor(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                retentionScheduleService.updateEnabled(request.enabled(), actor)));
    }
```

- [ ] **Step 4: SecurityConfig 매처 추가**

POST 매처를 `purge-batches/force` 줄 옆(둘 다 broad `/api/admin/**`보다 앞)에 넣는다.

```java
                // 자동 파기 on/off — 쓰기는 PRIVACY. GET 은 아래 broad GET retention/** (RECRUIT·PRIVACY)로 충분하다.
                .requestMatchers(HttpMethod.POST, "/api/admin/retention/schedule").hasAuthority(RoleNames.PRIVACY_ADMIN)
```

GET은 기존 `GET /api/admin/retention/**` 매처가 RECRUIT·PRIVACY로 이미 커버한다. 별도 매처를 만들지 않는다.

- [ ] **Step 5: 테스트 통과 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --no-daemon
```
Expected: PASS

- [ ] **Step 6: S2 전체 회귀 확인**

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.Retention*" --tests "com.shinyoung.recruit.service.Purge*" --tests "com.shinyoung.recruit.service.ForcedPurge*" --tests "com.shinyoung.recruit.service.Audit*" --tests "com.shinyoung.recruit.service.ActivityLogServiceTest" --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --tests "com.shinyoung.recruit.domain.repository.RetentionScheduleSettingRepositoryTest" --no-daemon
```
Expected: PASS

---

# S3 — 관리자 화면

## Task 13: 타입과 API 모듈

**Files:**
- Create: `{FE}/types/admin/retention.ts`
- Create: `{FE}/api/admin/retentionApi.ts`

- [ ] **Step 1: 타입 정의**

`{FE}/types/admin/retention.ts`:

```ts
/*
 * 개인정보 보존·파기 타입. 백엔드 RetentionPolicyResponse / DataSubject*Response /
 * RetentionScheduleResponse / PurgeBatch*Response 와 대응한다(docs/domains/privacy-audit.md "API 계약").
 */

export type RetentionBaselineType = 'HIRING_ENDED_AT' | 'CLOSED_AT'

export interface RetentionPolicy {
  id: number
  jobPostingId: number | null
  retentionPeriodDays: number
  baselineType: RetentionBaselineType
  enabled: boolean
  effectiveFrom: string | null
  effectiveTo: string | null
}

export interface RetentionPolicySaveRequest {
  jobPostingId: number | null
  retentionPeriodDays: number
  baselineType: RetentionBaselineType
  enabled: boolean
  effectiveFrom: string | null
  effectiveTo: string | null
}

export type RetentionScheduleRunResult =
  | 'SKIPPED_DISABLED'
  | 'SKIPPED_NOT_DUE'
  | 'NO_TARGET'
  | 'EXECUTED'
  | 'ERROR'

/** nextPurgeDate 가 '9999-12-31' 이면 파기 예정이 없다는 뜻이다. */
export interface RetentionSchedule {
  enabled: boolean
  nextPurgeDate: string
  hasPolicy: boolean
  retentionPeriodDays: number | null
  lastRunAt: string | null
  lastRunResult: RetentionScheduleRunResult | null
  lastRunBatchId: number | null
}

export interface DataSubjectQuery {
  name?: string
  phoneNumber?: string
  email?: string
}

export interface DataSubjectSummary {
  applicantId: number
  name: string | null
  email: string | null
  phoneNumber: string | null
  applicationCount: number
  purgedApplicationCount: number
  lastAppliedAt: string | null
  hasActiveHold: boolean
}

export interface DataSubjectApplication {
  applicationId: number
  jobPostingTitle: string
  status: string
  submittedAt: string | null
  purgeResult: string | null
  eligible: boolean
  reasonCode: string | null
}

export interface DataSubjectDetail {
  applicantId: number
  name: string | null
  email: string | null
  phoneNumber: string | null
  hasActiveHold: boolean
  applications: DataSubjectApplication[]
}

export type ForcedPurgeReason = 'DATA_SUBJECT_REQUEST' | 'DUPLICATE_ACCOUNT' | 'OTHER'

export interface ForcedPurgeRequest {
  applicantId: number
  reasonCode: ForcedPurgeReason
  confirm: boolean
}

export interface PurgeBatchSummary {
  id: number
  mode: string
  status: string
  triggerType: string
  scanAt: string
  startedAt: string
  completedAt: string | null
  requestedBy: string
  sourceDryRunBatchId: number | null
  totalCount: number
  eligibleCount: number
  skippedCount: number
  policyConflictCount: number
  purgedCount: number
  pendingCount: number
  failedCount: number
  binaryDeleteFailedCount: number
}

export interface PurgeBatchItem {
  id: number
  applicationId: number
  jobPostingId: number | null
  status: string
  reasonCode: string | null
}

export interface PurgeBatchDetail {
  batch: PurgeBatchSummary
  items: PurgeBatchItem[]
}
```

`PurgeBatchSummary`의 필드 이름은 백엔드 `{BE}/dto/response/PurgeBatchResponse.java`를 열어 1:1로 맞춘다. 추측하지 말 것.

- [ ] **Step 2: API 모듈 작성**

`{FE}/api/admin/retentionApi.ts`:

```ts
import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  DataSubjectDetail,
  DataSubjectQuery,
  DataSubjectSummary,
  ForcedPurgeRequest,
  PurgeBatchDetail,
  PurgeBatchSummary,
  RetentionPolicy,
  RetentionPolicySaveRequest,
  RetentionSchedule,
} from '@/types/admin/retention'

/*
 * 개인정보 보존·파기 API 모듈.
 * data-subjects 와 강제 파기, 스케줄 토글은 ROLE_PRIVACY_ADMIN 전용이다.
 * 권한 없이 호출하면 공통 인터셉터가 403을 받아 /403 으로 보내 버리므로,
 * 화면에서 권한을 먼저 확인하고 호출해야 한다.
 */
export const retentionApi = {
  getPolicies() {
    return apiClient.get<ApiResponse<RetentionPolicy[]>>('/admin/retention/policies')
  },

  createPolicy(request: RetentionPolicySaveRequest) {
    return apiClient.post<ApiResponse<RetentionPolicy>>('/admin/retention/policies', request)
  },

  updatePolicy(policyId: number, request: RetentionPolicySaveRequest) {
    return apiClient.post<ApiResponse<RetentionPolicy>>(`/admin/retention/policies/${policyId}`, request)
  },

  getSchedule() {
    return apiClient.get<ApiResponse<RetentionSchedule>>('/admin/retention/schedule')
  },

  updateSchedule(enabled: boolean) {
    return apiClient.post<ApiResponse<RetentionSchedule>>('/admin/retention/schedule', { enabled })
  },

  searchDataSubjects(query: DataSubjectQuery) {
    return apiClient.get<ApiResponse<DataSubjectSummary[]>>('/admin/retention/data-subjects', { params: query })
  },

  getDataSubject(applicantId: number) {
    return apiClient.get<ApiResponse<DataSubjectDetail>>(`/admin/retention/data-subjects/${applicantId}`)
  },

  /** 비가역 파기. 지원자 1명의 모든 지원서와 계정을 지운다. */
  forcePurge(request: ForcedPurgeRequest) {
    return apiClient.post<ApiResponse<PurgeBatchDetail>>('/admin/retention/purge-batches/force', request)
  },

  getPurgeBatches(page: number, size: number) {
    return apiClient.get<ApiResponse<PageResponse<PurgeBatchSummary>>>('/admin/retention/purge-batches', {
      params: { page, size },
    })
  },

  getPurgeBatch(batchId: number) {
    return apiClient.get<ApiResponse<PurgeBatchDetail>>(`/admin/retention/purge-batches/${batchId}`)
  },
}
```

- [ ] **Step 3: 타입 검사**

```bash
npm run type-check
```
Expected: 에러 0건

---

## Task 14: 라벨 매핑과 유닛 테스트

**Files:**
- Create: `{FE}/views/admin/retention/retentionLabel.ts`
- Test: `{FE}/views/admin/retention/__tests__/retentionLabel.spec.ts`

- [ ] **Step 1: 실패하는 테스트 작성**

```ts
import { describe, expect, it } from 'vitest'
import {
  NO_SCHEDULE_DATE,
  eligibilityTag,
  formatNextPurgeDate,
  forcedPurgeReasonLabel,
  runResultLabel,
} from '../retentionLabel'

describe('retentionLabel', () => {
  it('파기 예정이 없으면 날짜 대신 안내 문구를 준다', () => {
    expect(formatNextPurgeDate(NO_SCHEDULE_DATE)).toBe('예정 없음(파기 대상 없음)')
  })

  it('예정일이 있으면 날짜를 그대로 보여 준다', () => {
    expect(formatNextPurgeDate('2029-03-09')).toBe('2029-03-09')
  })

  it('진행 중 지원서는 경고 태그로 표시한다', () => {
    expect(eligibilityTag(false, 'APPLICATION_NOT_TERMINAL')).toEqual({ label: '진행 중', color: 'orange' })
  })

  it('보류는 빨간 태그로 표시한다', () => {
    expect(eligibilityTag(false, 'RETENTION_HOLD')).toEqual({ label: '보류', color: 'red' })
  })

  it('이미 파기된 건은 회색 태그로 표시한다', () => {
    expect(eligibilityTag(false, 'ALREADY_PURGED')).toEqual({ label: '파기됨', color: 'default' })
  })

  it('보존기간 미도래는 강제 파기 대상이므로 파기 가능으로 본다', () => {
    expect(eligibilityTag(false, 'RETENTION_NOT_DUE')).toEqual({ label: '파기 가능', color: 'green' })
  })

  it('적격 건은 파기 가능으로 표시한다', () => {
    expect(eligibilityTag(true, null)).toEqual({ label: '파기 가능', color: 'green' })
  })

  it('실행 결과 라벨을 한글로 준다', () => {
    expect(runResultLabel('SKIPPED_NOT_DUE')).toBe('실행 안 함(예정일 전)')
    expect(runResultLabel('ERROR')).toBe('실패')
    expect(runResultLabel(null)).toBe('실행 이력 없음')
  })

  it('사유 라벨을 한글로 준다', () => {
    expect(forcedPurgeReasonLabel('DATA_SUBJECT_REQUEST')).toBe('본인 삭제 요청')
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
npm run test:unit -- retentionLabel
```
Expected: FAIL — 모듈 없음.

- [ ] **Step 3: 구현**

`{FE}/views/admin/retention/retentionLabel.ts`:

```ts
/*
 * 파기 화면의 코드 → 한글 라벨 매핑. 화면 여러 곳이 같은 라벨을 써야 해서 한 곳에 모은다.
 */
import type { ForcedPurgeReason, RetentionScheduleRunResult } from '@/types/admin/retention'

/** 백엔드가 "파기 예정 없음"을 뜻할 때 쓰는 약속된 날짜. */
export const NO_SCHEDULE_DATE = '9999-12-31'

export interface RetentionTag {
  label: string
  color: string
}

export const formatNextPurgeDate = (date: string): string =>
  date === NO_SCHEDULE_DATE ? '예정 없음(파기 대상 없음)' : date

/*
 * 지원서별 판정 태그. 강제 파기는 보존기간·전형 상태를 보지 않으므로
 * RETENTION_NOT_DUE 등 기간 관련 사유는 "파기 가능"으로 묶는다.
 * 실제로 파기를 막는 것은 보류와 이미 파기된 건뿐이다.
 */
export const eligibilityTag = (eligible: boolean, reasonCode: string | null): RetentionTag => {
  if (reasonCode === 'RETENTION_HOLD') {
    return { label: '보류', color: 'red' }
  }
  if (reasonCode === 'ALREADY_PURGED') {
    return { label: '파기됨', color: 'default' }
  }
  if (reasonCode === 'APPLICATION_NOT_TERMINAL') {
    return { label: '진행 중', color: 'orange' }
  }
  if (eligible) {
    return { label: '파기 가능', color: 'green' }
  }
  return { label: '파기 가능', color: 'green' }
}

const RUN_RESULT_LABELS: Record<RetentionScheduleRunResult, string> = {
  SKIPPED_DISABLED: '실행 안 함(꺼짐)',
  SKIPPED_NOT_DUE: '실행 안 함(예정일 전)',
  NO_TARGET: '대상 없음',
  EXECUTED: '파기 실행',
  ERROR: '실패',
}

export const runResultLabel = (result: RetentionScheduleRunResult | null): string =>
  result === null ? '실행 이력 없음' : RUN_RESULT_LABELS[result]

const REASON_LABELS: Record<ForcedPurgeReason, string> = {
  DATA_SUBJECT_REQUEST: '본인 삭제 요청',
  DUPLICATE_ACCOUNT: '중복·오입력 계정 정리',
  OTHER: '기타',
}

export const forcedPurgeReasonLabel = (reason: ForcedPurgeReason): string => REASON_LABELS[reason]
```

`eligibilityTag`의 마지막 두 분기가 같은 값을 돌려주는 것은 의도다(기간 관련 사유는 강제 파기에서 의미가 없다). 나중에 읽는 사람이 실수로 합치지 않도록 위 주석을 남긴다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
npm run test:unit -- retentionLabel
```
Expected: PASS (9건)

---

## Task 15: 화면 뼈대 · 라우트 · 보존 정책 카드

**Files:**
- Create: `{FE}/views/admin/retention/AdminRetentionView.vue`
- Create: `{FE}/views/admin/retention/RetentionPolicyCard.vue`
- Modify: `{FE}/routes/adminRoutes.ts`

- [ ] **Step 1: 라우트 추가**

`{FE}/routes/adminRoutes.ts`의 `messages/templates` 다음에 추가한다.

```ts
      {
        path: 'retention',
        name: 'AdminRetention',
        component: () => import('@/views/admin/retention/AdminRetentionView.vue'),
      },
```

- [ ] **Step 2: 화면 뼈대 작성**

`{FE}/views/admin/retention/AdminRetentionView.vue`. 기존 관리자 화면(예: `{FE}/views/admin/message/AdminMessageHistoryView.vue`)의 헤더·여백·카드 스타일을 따른다.

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import RetentionPolicyCard from './RetentionPolicyCard.vue'
import RetentionScheduleCard from './RetentionScheduleCard.vue'
import DataSubjectSearchPanel from './DataSubjectSearchPanel.vue'
import PurgeBatchPanel from './PurgeBatchPanel.vue'

/*
 * 개인정보 파기 화면. 상단에 보존 정책·자동 파기 카드를 두고, 아래를 탭 2개로 나눈다.
 *
 * 삭제 요청 파기 탭은 ROLE_PRIVACY_ADMIN 전용 API 만 호출한다. 권한이 없는 사용자에게 탭을 보여 주면
 * 조회 한 번에 공통 인터셉터가 403 을 받아 /403 으로 보내 버리므로, 탭 자체를 렌더링하지 않는다.
 */
const authStore = useAuthStore()

const canPurge = computed<boolean>(() => authStore.roles.includes('ROLE_PRIVACY_ADMIN'))

const activeTab = ref<string>('batches')
const policyVersion = ref<number>(0)

onMounted(() => {
  activeTab.value = canPurge.value ? 'dataSubjects' : 'batches'
})

/* 정책이 바뀌면 자동 파기 카드의 예정일도 다시 읽어야 한다. */
const handlePolicyChanged = (): void => {
  policyVersion.value += 1
}
</script>

<template>
  <section class="retention-view">
    <header class="view-header">
      <div>
        <h2 class="view-title">개인정보 파기</h2>
        <p class="view-desc">보존기간이 지난 지원서를 자동으로 파기하고, 삭제 요청을 받은 지원자를 즉시 파기합니다.</p>
      </div>
    </header>

    <div class="card-row">
      <RetentionPolicyCard :can-edit="canPurge" @changed="handlePolicyChanged" />
      <RetentionScheduleCard :can-edit="canPurge" :policy-version="policyVersion" />
    </div>

    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane v-if="canPurge" key="dataSubjects" tab="삭제 요청 파기">
        <DataSubjectSearchPanel />
      </a-tab-pane>
      <a-tab-pane key="batches" tab="파기 이력">
        <PurgeBatchPanel />
      </a-tab-pane>
    </a-tabs>

    <a-alert
      v-if="!canPurge"
      type="info"
      show-icon
      message="조회 권한으로 보고 있습니다"
      description="삭제 요청 파기와 설정 변경은 개인정보 파기 권한(ROLE_PRIVACY_ADMIN)이 필요합니다."
    />
  </section>
</template>

<style scoped>
.retention-view {
  padding: 24px 28px 40px;
}

.view-header {
  margin-bottom: 18px;
}

.view-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.view-desc {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.card-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

@media (max-width: 1100px) {
  .card-row {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 3: 보존 정책 카드 작성**

`{FE}/views/admin/retention/RetentionPolicyCard.vue`. 전역 정책(`jobPostingId === null && enabled`) 1건만 다룬다.

요구 동작:
- 마운트 시 `retentionApi.getPolicies()` 호출 → 전역 enabled 정책을 찾는다.
- 없으면 빨간 `a-alert`("보존 정책이 없어 자동 파기가 동작하지 않습니다") + `정책 등록` 버튼(`canEdit`일 때만).
- 있으면 "공고 마감일 기준 1825일(5년) 보관 후 파기" 요약 + `수정` 버튼(`canEdit`일 때만).
- 전역 enabled 정책이 2건 이상이면 경고 `a-alert`와 함께 목록을 표시한다.
- 등록·수정은 `a-modal` + `a-form`(보존 일수 `a-input-number` min 1, 기산점 `a-select`[`CLOSED_AT`/`HIRING_ENDED_AT`], 사용 여부 `a-switch`).
- 저장 시 보존 일수가 기존보다 **줄어들면** `a-modal.confirm`으로 "다음 자동 파기 때 더 많은 지원서가 파기됩니다. 계속할까요?"를 먼저 묻는다.
- 성공하면 `message.success('보존 정책을 저장했습니다.')` 후 `emit('changed')`.
- 실패는 `getApiErrorMessage(error)`로 `message.error`.

`defineProps<{ canEdit: boolean }>()`, `defineEmits<{ changed: [] }>()`를 쓴다.

- [ ] **Step 4: 타입 검사**

```bash
npm run type-check
```
Expected: `RetentionScheduleCard`·`DataSubjectSearchPanel`·`PurgeBatchPanel`가 아직 없어 실패한다. Task 16~19에서 채운 뒤 다시 확인한다. 그때까지는 각 파일을 최소 껍데기(`<template><div /></template>`)로 만들어 두고 진행해도 된다.

---

## Task 16: 자동 파기 카드

**Files:**
- Create: `{FE}/views/admin/retention/RetentionScheduleCard.vue`

- [ ] **Step 1: 컴포넌트 작성**

`props`: `{ canEdit: boolean; policyVersion: number }`. `policyVersion`이 바뀌면 다시 조회한다(`watch`).

요구 동작:
- 마운트·`policyVersion` 변경 시 `retentionApi.getSchedule()`.
- 스위치(`a-switch`): `canEdit`가 false면 `disabled`. `hasPolicy`가 false면 `disabled` + 툴팁 "보존 정책을 먼저 등록하세요".
- **끄는 방향일 때만** `a-modal.confirm`으로 "보존기간이 지난 개인정보가 자동으로 파기되지 않습니다. 끄시겠습니까?"를 묻는다. 켜는 방향은 즉시 반영한다.
- 토글은 `retentionApi.updateSchedule(next)` 호출 후 응답으로 상태를 갱신한다. 실패하면 스위치를 원래 값으로 되돌리고 `message.error`.
- 다음 파기 예정일: `formatNextPurgeDate(schedule.nextPurgeDate)`. 예정 없음이 아닐 때만 보조 문구 "이 날짜부터 파기 대상이 생길 수 있습니다."를 함께 표시한다.
- 고정 보조 문구: "매일 03:00에 확인합니다. 예정일 전에는 스캔하지 않습니다."
- 마지막 실행: `lastRunAt`(없으면 '-') + `runResultLabel(lastRunResult)`. `EXECUTED`이면 `lastRunBatchId`를 여는 링크(부모에 `emit('open-batch', id)`)를, `ERROR`이면 빨간 텍스트와 "서버 로그를 확인하세요" 안내를 붙인다. **예외 내용을 화면에 표시하지 않는다.**

- [ ] **Step 2: 타입 검사**

```bash
npm run type-check
```
Expected: 남은 미구현 컴포넌트 외 에러 없음

---

## Task 17: 지원자 검색 패널과 상세 드로어

**Files:**
- Create: `{FE}/views/admin/retention/DataSubjectSearchPanel.vue`
- Create: `{FE}/views/admin/retention/DataSubjectDrawer.vue`

- [ ] **Step 1: 검색 패널 작성**

요구 동작:
- 검색바: 이름·휴대폰·이메일 `a-input` 3개 + `조회` 버튼. `@press-enter`로도 조회.
- 세 값이 모두 비면 API를 호출하지 않고 `message.warning('검색 조건을 1개 이상 입력하세요.')`.
- `retentionApi.searchDataSubjects({ name, phoneNumber, email })`. 빈 문자열은 `undefined`로 보내 쿼리에서 빠지게 한다.
- 결과 `a-table` 컬럼: 이름 · 이메일 · 휴대폰 · 지원 n건 · 최근 지원일 · 상태.
  - 상태 태그: `hasActiveHold`면 `보류 중`(red), `purgedApplicationCount === applicationCount && applicationCount > 0`이면 `파기 완료`(default), `purgedApplicationCount > 0`이면 `일부 파기`(orange), 그 외 없음.
  - **이메일·휴대폰을 마스킹하지 않는다.**
- 결과가 50건이면 표 아래에 "상위 50건만 표시합니다. 조건을 좁혀 주세요."를 회색 글씨로 붙인다.
- 행 클릭 → `DataSubjectDrawer`를 연다(`applicantId` 전달).
- 드로어에서 파기가 끝나면(`@purged`) 검색을 다시 실행한다.

- [ ] **Step 2: 상세 드로어 작성**

`props`: `{ open: boolean; applicantId: number | null }`, `emits`: `{ 'update:open': [boolean]; purged: [] }`.

요구 동작:
- `applicantId`가 바뀌고 `open`이 true면 `retentionApi.getDataSubject(id)` 호출. 응답이 늦게 와도 그 사이에 대상이 바뀌었으면 버린다(요청 시각·id 비교로 stale 가드).
- 상단: 이름 · 이메일 · 휴대폰.
- 지원서 표: 공고명 · 지원일 · 상태 · 판정 태그(`eligibilityTag(row.eligible, row.reasonCode)`).
- 하단 버튼 `삭제 요청 파기`:
  - `hasActiveHold`면 `disabled` + 아래에 빨간 안내 "파기 보류가 걸려 있어 파기할 수 없습니다. 보류를 먼저 해제해야 합니다."
  - 모든 지원서가 `ALREADY_PURGED`이고 지원서가 1건 이상이면 `disabled` + 회색 안내 "이미 파기된 지원자입니다."
  - 그 외에는 활성. 클릭하면 `ForcedPurgeConfirmModal`을 연다.
- 파기 성공 시 드로어를 닫고 `emit('purged')`.

- [ ] **Step 3: 타입 검사**

```bash
npm run type-check
```

---

## Task 18: 확인 모달과 파기 실행

**Files:**
- Create: `{FE}/views/admin/retention/ForcedPurgeConfirmModal.vue`

- [ ] **Step 1: 컴포넌트 작성**

`props`: `{ open: boolean; detail: DataSubjectDetail | null }`,
`emits`: `{ 'update:open': [boolean]; purged: [result: PurgeBatchDetail] }`.

요구 동작(순서대로 화면에 배치):
1. 대상 재표시: 이름 · 이메일 · 휴대폰을 굵게. 잘못된 사람을 지우는 것을 막는 유일한 방어선이므로 눈에 띄게 둔다.
2. 진행 중 건 경고: `detail.applications` 중 `reasonCode === 'APPLICATION_NOT_TERMINAL'`인 건수가 1 이상이면 빨간 `a-alert` — "진행 중인 전형 N건이 함께 파기됩니다. 해당 지원자는 더 이상 전형에 참여할 수 없습니다."
3. 고정 안내 `a-alert`(warning) — "계정이 익명화되어 이 지원자는 다시 로그인할 수 없습니다. 재지원은 신규 가입이 필요합니다. 이 작업은 되돌릴 수 없습니다."
4. 사유 `a-radio-group`(세로): `DATA_SUBJECT_REQUEST` / `DUPLICATE_ACCOUNT` / `OTHER`. 라벨은 `forcedPurgeReasonLabel`. 기본 선택은 `DATA_SUBJECT_REQUEST`. 그 아래 회색 글씨로 "상세 경위는 접수 대장에 기록하세요. 파기 기록에는 사유 구분만 남습니다."
5. 확인 체크박스 — "위 내용을 확인했으며 파기에 동의합니다."
6. 실행 버튼(`danger`): 체크 전에는 `disabled`. 클릭하면 `loading` 상태로 `retentionApi.forcePurge({ applicantId, reasonCode, confirm: true })`.

결과 처리:
- 성공: `message.success` + 결과 요약. 예) "지원서 2건 파기 완료(스킵 0건, 실패 0건)". `emit('purged', data)` 후 닫는다.
- `failedCount > 0 || pendingCount > 0`이면 `message.warning`으로 "일부 항목이 완료되지 않았습니다. 파기 이력에서 확인하세요."를 함께 띄운다.
- 실패: `getApiErrorMessage(error)`를 `message.error`로. 모달은 닫지 않는다.
- 닫힐 때 체크박스와 사유를 초기값으로 되돌린다(다음에 열 때 체크가 남아 있으면 안 된다).

- [ ] **Step 2: 타입 검사**

```bash
npm run type-check
```

---

## Task 19: 파기 이력 탭

**Files:**
- Create: `{FE}/views/admin/retention/PurgeBatchPanel.vue`

- [ ] **Step 1: 컴포넌트 작성**

요구 동작:
- `retentionApi.getPurgeBatches(page, 20)`로 목록 조회. `a-table` + `a-pagination`.
- 컬럼: 실행일시(`startedAt`) · 모드 · 트리거 · 요청자 · 상태 · 대상/파기/스킵/실패.
  - 트리거 라벨: `RETENTION` → "보존기간 만료", `DATA_SUBJECT_REQUEST` → "삭제 요청", `FORCED_PURGE` → "강제 파기".
  - 상태 태그: `COMPLETED` green, `PARTIAL_FAILED` orange, `FAILED` red, `RUNNING` blue.
  - 요청자가 `SYSTEM`이면 "자동 실행"으로 표시한다.
- 행 클릭 → `a-drawer`에서 `retentionApi.getPurgeBatch(id)` 호출.
- 드로어 구성 순서:
  1. batch 집계(대상·파기·보류중·스킵·실패)
  2. **스킵 사유별 집계** — `items`를 `reasonCode`로 묶어 건수 표로. 사유 한글 라벨은 `eligibilityTag`가 아니라 별도 매핑을 쓴다(사유 그대로: `RETENTION_NOT_DUE` → "보존기간 미도래" 등).
  3. 지원서 id 목록은 `a-collapse` 안에 둔다. item이 수천 건일 수 있어 기본은 접어 둔다.
- 부모(`AdminRetentionView`)가 자동 파기 카드의 링크로 특정 batch를 열 수 있도록 `defineExpose({ openBatch(id: number) })`를 제공한다.

- [ ] **Step 2: 검증**

```bash
npm run type-check
npm run test:unit
npm run lint
```
Expected: 전부 통과

---

## Task 20: 문서 갱신과 최종 검증

**Files:**
- Modify: `docs/domains/privacy-audit.md`
- Modify: `docs/domains/_index.md`
- Modify: `todo.md`

- [ ] **Step 1: 도메인 카드 갱신**

`docs/domains/privacy-audit.md`에서:
- `## 요약`: "관리자 API만 있다. **프론트 화면·스케줄러 없음**" 문장을 현재 상태로 고친다.
- `## API 계약` 표에 5행 추가(🟢). `GET data-subjects`·`GET data-subjects/{applicantId}`·`POST purge-batches/force`·`GET schedule`·`POST schedule`.
- `### 엔드포인트 상세`에 검색 조건 필수·상한 50·익명화 계정 제외, 강제 파기 거부 조건(confirm·hold·404), 스케줄 토글 조건(정책 없으면 400)을 적는다.
- `## 파일 지도` 백엔드 표에 신규 파일을 추가하고, `### 프론트` 절의 "없음"을 화면 파일 목록으로 교체한다.
- `## 규칙·불변식`에 절을 추가한다: 강제 파기 판정 축약(2단계), 계정 익명화 조건, 스케줄 게이트 2단, 다음 파기 예정일 정의와 `9999-12-31` 약속, `SYSTEM` 예약 actorId.
- `## 감사 호출 규약` 표에 `PURGE_FORCED` 행을 추가한다.
- `## 함정·결정` 마지막 `미사용 슬롯` 항목에서 `ActorType.SYSTEM`·`PurgeTriggerType.DATA_SUBJECT_REQUEST`를 뺀다. `미구현` 용어 행에서 forced purge와 스케줄 실행을 뺀다.
- `## 함정·결정`의 수동 DDL 목록에 `phase-10-retention-schedule-ddl.sql`을 추가한다.

> **카드 크기 주의:** 현재 30.3KB이고 상한은 40KB다. 위 내용을 다 넣으면 상한에 가까워진다. 35KB를 넘으면 감사(`ActivityLog`) 부분을 `privacy-audit-audit.md`로 분리하고 `_index.md`를 고친다.

- [ ] **Step 2: 색인 갱신**

`docs/domains/_index.md`의 privacy-audit 행 키워드에 `개인정보 파기 화면`·`/admin/retention`·`강제 파기`·`자동 파기`를 추가한다.

- [ ] **Step 3: todo.md 갱신**

운영 반영 전 항목을 추가한다.

```markdown
- [ ] **파기 운영 준비**: `phase-10-retention-schedule-ddl.sql` 적용, 전역 보존 정책 1건 등록
      (1825일·CLOSED_AT·enabled), `/admin/menus`에 개인정보 파기(`/admin/retention`) 등록,
      파기 담당자에게 `ROLE_PRIVACY_ADMIN` 매핑. 첫 가동 전에 수동 dry-run으로 건수를 확인한 뒤 자동 파기를 켠다.
```

- [ ] **Step 4: 문서 점검**

```bash
node tools/check-docs.mjs
```
Expected: 오류 0건

- [ ] **Step 5: 최종 검증**

백엔드(`recruit_back/recruit_backend/`에서):

```bash
AES_SECRET_KEY='22791194512954214612461221261067' ./gradlew test --tests "com.shinyoung.recruit.service.Retention*" --tests "com.shinyoung.recruit.service.Purge*" --tests "com.shinyoung.recruit.service.ForcedPurge*" --tests "com.shinyoung.recruit.service.DataSubject*" --tests "com.shinyoung.recruit.service.Audit*" --tests "com.shinyoung.recruit.service.ActivityLogServiceTest" --tests "com.shinyoung.recruit.service.ApplicationPiiPurgeServiceTest" --tests "com.shinyoung.recruit.controller.AdminRetentionControllerTest" --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --tests "com.shinyoung.recruit.domain.repository.RetentionScheduleSettingRepositoryTest" --no-daemon
```

프론트(`recruit_front/`에서):

```bash
npm run type-check
npm run test:unit
npm run build
```

Expected: 전부 통과

- [ ] **Step 6: 줄바꿈 확인**

새로 만든 파일이 전부 LF인지 확인한다.

```bash
git status --porcelain | awk '{print $2}' | while read f; do
  [ -f "$f" ] && cr=$(tr -cd '\r' < "$f" | wc -c) && [ "$cr" != "0" ] && echo "CRLF: $f"
done
```
Expected: 출력 없음

---

## 완료 조건

- 백엔드 테스트 전부 통과(위 최종 검증 명령)
- `npm run type-check`·`npm run test:unit`·`npm run build` 통과
- `node tools/check-docs.mjs` 오류 0건
- `docs/domains/privacy-audit.md` API 계약 5행이 🟢
- 커밋하지 않음(사용자 요청 시에만)
