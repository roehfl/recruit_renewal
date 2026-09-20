# 개인정보 파기 화면·강제 파기·자동 파기 설계

작성일 2026-09-20. 대상 카드: [privacy-audit](../../domains/privacy-audit.md).

## 1. 배경

백엔드 보존·파기·감사 API 13종은 구현·검증이 끝났고(카드 `## API 계약` 전부 🟢) 프론트 화면은 0개다.
지금 상태로는 파기가 한 번도 실행되지 않는다. 다음 두 가지가 비어 있다.

- **자동 파기**: 스케줄 실행이 없다. 보존기간이 지나도 아무도 파기하지 않는다.
- **삭제 요청 대응**: [ApplicantPrivacy.vue:53](../../../recruit_front/src/views/applicant/ApplicantPrivacy.vue) 개인정보 처리방침은
  "인력풀에 5년 보관, 삭제를 원하는 경우 지체없이 삭제"를 약속한다. 현재 단건 execute는 적격성을 재검증하므로
  보존기간이 남아 있으면 `RETENTION_NOT_DUE`로 스킵된다. 즉 삭제 요청을 처리할 경로가 없다.
  카드도 forced purge를 `미구현`으로 적어 두었다.

## 2. 목표와 범위

| 덩어리 | 내용 |
|---|---|
| A | 강제 파기(삭제 요청) — 지원자 조회 API 2개, 강제 파기 API 1개, `PurgeItemProcessor` forced 경로 |
| B | 자동 파기 스케줄러 — 매일 03:00 dry-run → execute, SYSTEM actor, 실행 on/off와 예정일 게이트 |
| C | 관리자 화면 1개 — `/admin/retention`, 탭 2개(삭제 요청 파기 / 자동 파기 이력) + 보존 정책·자동 파기 설정 |

범위 밖: 감사 로그 조회 화면(`/admin/audit/activities` API는 있으나 화면은 만들지 않는다), 공고별 override 정책 UI,
`hiringEndedAt`(anchor) 확정 UI, reconcile 실행 버튼.

### 성공 기준

1. 관리자가 이름·휴대폰·이메일로 지원자를 찾아 그 사람의 모든 개인정보를 한 번에 파기할 수 있다.
2. 보존기간(5년)이 지난 지원서가 사람 개입 없이 매일 파기된다.
3. 파기 대상이 하나도 없는 동안에는 스캔이 돌지 않는다. 관리자는 자동 파기를 끄고 켤 수 있고,
   다음 파기 예정일을 화면에서 본다.
4. 파기 보류(hold)가 걸린 사람은 강제 파기로도 지워지지 않는다.
5. 파기 실행 이력과 사유가 감사 로그에 남고, 그 로그에 지원자 PII가 들어가지 않는다.

## 3. 보존 정책 전제

전역 정책 1건을 쓴다.

| 항목 | 값 | 근거 |
|---|---|---|
| `retentionPeriodDays` | 1825 (5년) | 개인정보 처리방침 "5년 동안 보관" |
| `baselineType` | `CLOSED_AT` | 공고 마감 시 [JobPostingService.close](../../../recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/JobPostingService.java)가 자동 기록. 사람이 공고마다 채용 종료일을 확정할 필요가 없다 |
| `jobPostingId` | null (전역) | 공고별 차등 보존 요구 없음 |
| `enabled` | true | |

`HIRING_ENDED_AT`을 쓰지 않는 이유: 수동 확정이 빠지면 그 공고 지원서는 영원히 파기되지 않는다
(`ANCHOR_NOT_FIXED`). 5년 단위에서 "공고 마감"과 "채용 실질 종료"의 차이는 무시할 수 있다.

정책이 DB에 없으면 스케줄러는 전건 `POLICY_NOT_FOUND`로 스킵한다. 화면 상단 정책 카드가 이 상태를 경고로 드러낸다.

## 4. A — 강제 파기

### 4.1 단위와 규칙

- 단위는 **지원자 1명 전체**다. 그 사람의 모든 지원서 + `Applicant` 계정을 한 `PurgeBatch`로 처리한다.
  일부만 지우면 계정에 이름·연락처가 남아 "지체없이 삭제" 약속을 못 지킨다.
- 계정이 익명화되면 `loginId`가 null이 되어 로그인이 불가능해진다. 재지원은 신규 가입이다.
- 지원 이력이 0건인 계정도 대상이다. item 없이 `Applicant.purgePersonalData`만 수행한다.
- **보류(hold)가 하나라도 걸려 있으면 거부**한다. 법적 보존 의무가 삭제 요구권보다 우선한다.
- **진행 중 전형이 있어도 파기한다.** 확인 모달이 "진행 중 지원서 N건이 함께 사라진다"고 경고한다.

### 4.2 적격성 판정

forced 경로는 [RetentionEligibilityService](../../../recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/RetentionEligibilityService.java)
9단계 중 **2개만** 적용한다.

| 판정 | forced | 이유 |
|---|---|---|
| `ALREADY_PURGED` | 적용 | 두 번 지울 것이 없다 |
| `RETENTION_HOLD` | 적용 | 보류가 우선 |
| `POLICY_NOT_FOUND` · `POLICY_CONFLICT` | 무시 | 정책은 기간 계산용이다. 강제 파기는 기간을 안 본다 |
| `ANCHOR_NOT_FIXED` · `RETENTION_NOT_DUE` | 무시 | 기간 무관이 강제 파기의 정의다 |
| `APPLICATION_NOT_TERMINAL` · `INVALID_STAGE_CONFIGURATION` | 무시 | 진행 중이어도 지운다 |

판정 이후의 처리(관계형 PII tombstone → 첨부 metadata → 바이너리 saga → ref-count 익명화)는 기존 경로와
완전히 같다. `PurgeItemProcessor.process`에 판정 모드를 넘기는 것 외에 파기 로직은 건드리지 않는다.

### 4.3 신규 API

전부 `ROLE_PRIVACY_ADMIN` 전용. `SecurityConfig`에 broad `/api/admin/**`보다 **앞선** 좁은 매처를 추가한다.

| 상태 | 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|---|
| 🟡 | GET | `/admin/retention/data-subjects` | query `name`·`phoneNumber`·`email` | `DataSubjectSummaryResponse[]` |
| 🟡 | GET | `/admin/retention/data-subjects/{applicantId}` | 없음 | `DataSubjectDetailResponse` |
| 🟡 | POST | `/admin/retention/purge-batches/force` | `{ applicantId, reasonCode, confirm }` | `PurgeBatchDetailResponse` |

**검색**(`GET data-subjects`)
- 세 조건 중 **최소 1개 필수**. 전부 비면 400(전체 나열 금지).
- `name`은 부분 일치, `phoneNumber`는 하이픈·공백 제거 후 부분 일치(기존
  [JobApplicationRepository](../../../recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/repository/JobApplicationRepository.java)
  규칙과 동일), `email`은 부분 일치. 셋 다 AND.
- 결과 상한 50건. 초과분은 잘라서 준다. 별도 플래그를 두지 않고, 화면이 "결과가 정확히 50건"이면
  "조건을 좁히라"고 안내한다.
- 이미 익명화된 계정(`ciHash`가 `PURGED:` 접두)은 결과에서 제외한다.
- 행: `{ applicantId, name, email, phoneNumber, applicationCount, purgedApplicationCount, lastAppliedAt, hasActiveHold }`.

**상세**(`GET data-subjects/{applicantId}`)
- `{ applicant: {...}, applications: [...], hasActiveHold, holdReasonPresent }`.
- 지원서 행: `{ applicationId, jobPostingTitle, status, submittedAt, purgeResult, eligibility: { eligible, reasonCode } }`.
- `eligibility`는 기존 `RetentionEligibilityService.evaluate`를 그대로 호출한 결과다. 화면은 이 값 하나로
  "진행 중"(`APPLICATION_NOT_TERMINAL`)·"보류"(`RETENTION_HOLD`)·"이미 파기"(`ALREADY_PURGED`)를 모두 표시한다.
- `holdReasonPresent`만 주고 **사유 원문은 주지 않는다**(카드 규칙: hold 사유는 민감 자유 텍스트).
- 없는 applicantId는 404.

**강제 파기**(`POST purge-batches/force`)
- `confirm != true` → 400. 없는 applicantId → 404. active hold 보유 → 400(`RETENTION_HOLD`).
- `reasonCode`는 신규 enum `ForcedPurgeReason`: `DATA_SUBJECT_REQUEST`(본인 삭제 요청) ·
  `DUPLICATE_ACCOUNT`(중복·오입력 계정 정리) · `OTHER`(기타). **자유 텍스트는 받지 않는다** —
  감사 로그와 파기 대장은 파기 대상이 아니므로 거기에 들어간 PII는 영구히 남는다.
- `PurgeBatch`: `mode=EXECUTE`, `triggerType=DATA_SUBJECT_REQUEST`, `sourceDryRunBatchId=null`.
- 응답은 기존 `PurgeBatchDetailResponse` 그대로(집계 + item 목록).

### 4.4 감사

- `AuditActionType`에 `PURGE_FORCED`를 추가한다. `PURGE_EXECUTE` 재사용이 아니라 별도 값이어야
  "강제 파기만 뽑아 보기"가 가능하다. VARCHAR 컬럼이라 DDL 불필요(50자 이내).
- metadata record 신규: `ForcedPurgeMetadata { applicantRefHash, reasonCode, applicationCount, purgedCount, skippedCount, failedCount }`.
  `AuditMetadata` permits와 `AuditMetadataContractTest` `EXPECTED_COMPONENTS`에 등록한다.
  **이름·이메일·휴대폰·loginId는 넣지 않는다.** 사람 식별은 `applicantRefHash`(기존 HMAC)로 한다.
- 기록 위치는 `PurgeBatchLifecycleService`(기존 execute와 동일하게 완료·실패용 REQUIRES_NEW tx 안 in-tx).

### 4.5 백엔드 변경 목록

| 파일 | 변경 |
|---|---|
| `enumeration/ForcedPurgeReason.java` | 신규 3값 |
| `enumeration/AuditActionType.java` | `PURGE_FORCED` 추가 |
| `service/PurgeItemProcessor.java` | `process(batchId, applicationId, scanAt, forced)` — forced면 hold·already-purged만 검사 |
| `service/ForcedPurgeService.java` | 신규. 검증 → batch 시작 → 지원서별 처리 → 계정 익명화 → 집계·감사 |
| `service/DataSubjectLookupService.java` | 신규. 검색·상세 조회(읽기 전용) |
| `service/PurgeBatchLifecycleService.java` | forced 완료·실패 기록 메서드 추가 |
| `domain/repository/ApplicantRepository.java` | 이름·연락처·이메일 검색 JPQL |
| `dto/request/ForcedPurgeRequest.java` | 신규 |
| `dto/response/DataSubject*Response.java` | 신규 2종 |
| `controller/AdminRetentionController.java` | 엔드포인트 3개 |
| `config/SecurityConfig.java` | 좁은 매처 3개(broad 앞) |

`Applicant` 계정 익명화는 기존 ref-count 로직을 쓰되, 지원서가 0건인 경우를 위해 `ForcedPurgeService`가
직접 `purgePersonalData`를 호출하는 경로를 둔다(이미 익명화된 계정은 건너뛴다).

## 5. B — 자동 파기 스케줄러

### 5.1 실행 게이트

```java
@Scheduled(cron = "${retention.purge-cron:0 0 3 * * *}")
```

cron은 매일 울리되, **실제 스캔은 두 관문을 통과해야** 돌아간다.

```
매일 03:00
  ├─ 자동 파기 OFF?            → 로그 1줄 남기고 종료
  ├─ 오늘 < 다음 파기 예정일?   → 로그 1줄 남기고 종료
  └─ 그 외 → dryRun("SYSTEM") → ELIGIBLE 0건이면 종료
                              → PurgeExecutionService.execute({confirm, sourceDryRunBatchId}, "SYSTEM")
```

게이트가 없으면 dry-run이 매일 밤 `findAll`로 전 지원서를 훑고 **지원서 수만큼 `purge_job_item` 행을
쌓는다**. 대상이 0건인 5년 동안에도 그렇다. 지원자 1만 명이면 연 360만 행이다. 게이트는 편의가 아니라
이 누적을 막는 장치다.

- [ClientEventLogCleanupScheduler](../../../recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/ClientEventLogCleanupScheduler.java)
  와 같은 모양: `@Component` + try/catch로 예외를 삼키고 로그만 남긴다. 실패해도 다음날 다시 돈다.
- 기존 2단계(dry-run → execute)를 그대로 호출하므로 근거 batch가 남아 무엇을 왜 지웠는지 추적된다.
- **건수 상한 없음**. 만료된 건은 그날 전부 처리한다. 첫 가동 때 누적분이 한꺼번에 처리될 수 있다
  (§9 위험 참조).
- 중복 실행 방지: 1서버 전제 + Spring 기본 `taskScheduler`는 단일 스레드라 이전 실행이 안 끝나면 다음 실행이
  대기한다. 별도 락을 두지 않는다.
- 게이트 통과 여부와 무관하게 마지막 실행 시각·결과를 설정 행에 기록한다(화면 표시용).

### 5.2 다음 파기 예정일

정의: **아직 파기되지 않은 지원서를 가진 마감된 공고 중, 가장 이른 `closedAt` + 보존기간**.

```sql
-- 개념 쿼리(실제는 JPQL)
select min(p.closedAt)
  from JobApplication a join a.jobPosting p
 where a.purgeResult is null
   and p.closedAt is not null
```

- 결과가 있으면 `min(closedAt) + retentionPeriodDays`(정책값)를 날짜로 반환한다.
- 결과가 없으면(마감된 공고가 없거나, 마감 공고의 지원서가 모두 파기됨) **9999-12-31**을 반환한다.
  "예정 없음"을 화면에서 한눈에 구분하기 위한 약속된 값이다.
- 전역 정책이 없으면 기간 계산이 불가능하므로 역시 9999-12-31이며, 화면은 정책 경고를 함께 띄운다.
- 이 값은 보존기간 도래일만 본다. 적격성 9단계(전형 종료·보류 등)까지 반영하지 않는다 —
  **"이 날짜 전에는 파기 대상이 절대 없다"는 하한**이며, 그 이상의 정밀도는 게이트 용도로 불필요하다.
- 반환값이 과거이거나 오늘이면 게이트를 통과한다.
- 쿼리 1건이고 인덱스(`purge_result`, `closed_at`)로 커버되므로 매일 호출해도 부담이 없다.

### 5.3 자동 파기 설정 저장

단일 행 테이블 `retention_schedule_setting`(id 고정 1)을 둔다.

| 컬럼 | 타입 | 내용 |
|---|---|---|
| `id` | BIGINT PK | 항상 1 |
| `enabled` | BOOLEAN NOT NULL | 자동 파기 on/off |
| `last_run_at` | DATETIME | 마지막 스케줄 기동 시각 |
| `last_run_result` | VARCHAR(30) | `SKIPPED_DISABLED`·`SKIPPED_NOT_DUE`·`NO_TARGET`·`EXECUTED`·`ERROR` |
| `last_run_batch_id` | BIGINT | 실행했다면 그 execute batch id |
| `updated_by` · `updated_at` | | 토글한 사람과 시각 |

- 행이 없으면 `enabled=false`로 간주한다(**안전 기본값** — 모르고 켜져 있는 것보다 꺼져 있는 편이 낫다).
  최초 토글 시 행을 만든다.
- `RetentionPolicy.enabled`를 재사용하지 않는 이유: 정책을 끄면 스케줄만 멈추는 게 아니라 적격성 판정이
  `POLICY_NOT_FOUND`가 되어 강제 파기 화면의 판정 표시와 감사 사유까지 오염된다. 두 스위치는 의미가 다르다.
- 운영 DDL은 `recruit_back/recruit_backend/docs/ops/`에 새 파일로 추가한다.

### 5.4 신규 API

| 상태 | 메서드 | 경로 | 요청 | 응답 | 권한 |
|---|---|---|---|---|---|
| 🟡 | GET | `/admin/retention/schedule` | 없음 | `RetentionScheduleResponse` | R·P |
| 🟡 | POST | `/admin/retention/schedule` | `{ enabled }` | `RetentionScheduleResponse` | PRIVACY |

`RetentionScheduleResponse`:
`{ enabled, nextPurgeDate, hasPolicy, retentionPeriodDays, lastRunAt, lastRunResult, lastRunBatchId }`

- `nextPurgeDate`는 `LocalDate`. 대상이 없으면 `9999-12-31`.
- 토글은 `RETENTION_POLICY_UPDATE` 감사에 metadata `operation=SCHEDULE_ENABLE`/`SCHEDULE_DISABLE`로 남긴다
  (새 `AuditActionType`을 만들지 않는다 — 보존 설정 변경이라는 성격이 같다).

### 5.5 SYSTEM actor

현재 `AuditRequestContextResolver.resolve(actor)`는 actorId가 있으면 무조건 `ActorType.EMPLOYEE`로 찍는다.
스케줄러 실행이 임직원 행위로 기록되면 감사 로그가 거짓이 된다.

- 예약 상수 `AuditActorContext.SYSTEM_ACTOR_ID = "SYSTEM"`을 두고, `resolve`가 이 값이면 `ActorType.SYSTEM`으로
  기록한다(`ActorType.SYSTEM`은 이미 enum에 있는 미사용 슬롯).
- 각 서비스의 `requireActor`는 빈 문자열만 거부하므로 `"SYSTEM"`은 그대로 통과한다. 수정 불필요.
- 사람이 `SYSTEM`이라는 loginId를 가질 수 없어야 한다. 임직원 loginId는 LDAP 사번 체계라 충돌하지 않는다.

### 5.6 백엔드 변경 목록

| 파일 | 변경 |
|---|---|
| `domain/entity/RetentionScheduleSetting.java` | 신규 단일 행 엔티티 |
| `domain/repository/RetentionScheduleSettingRepository.java` | 신규 |
| `domain/repository/JobApplicationRepository.java` | `findEarliestUnpurgedClosedAt()` JPQL 추가 |
| `service/RetentionScheduleService.java` | 신규. 설정 조회·토글, `nextPurgeDate()` 계산, 실행 결과 기록 |
| `service/RetentionPurgeScheduler.java` | 신규 `@Scheduled` — 게이트 2개 → dry-run → execute |
| `service/AuditRequestContextResolver.java` | `SYSTEM` 예약 actorId → `ActorType.SYSTEM` |
| `dto/request/RetentionScheduleRequest.java` | 신규 `{ enabled }` |
| `dto/response/RetentionScheduleResponse.java` | 신규 |
| `controller/AdminRetentionController.java` | 엔드포인트 2개 |
| `config/SecurityConfig.java` | GET/POST `schedule` 매처(broad 앞, POST는 PRIVACY) |
| `docs/ops/*.sql` | `retention_schedule_setting` DDL |

## 6. C — 관리자 화면

경로 `/admin/retention`, 메뉴명 **개인정보 파기**. `AdminLayout` 아래 단일 화면.

### 6.1 권한 표현

라우트 가드(`ADMIN_ROLES`)는 건드리지 않는다. 파기 담당자는 `ROLE_RECRUIT_ADMIN` + `ROLE_PRIVACY_ADMIN`을
함께 가진다는 전제다.

| 영역 | 필요 역할 | PRIVACY 없는 사용자 |
|---|---|---|
| 화면 진입 | `ADMIN_ROLES` | 진입 가능 |
| 정책 카드 조회 · 자동 파기 이력(탭 2) | RECRUIT 또는 PRIVACY | 그대로 보인다 |
| 지원자 검색·상세(탭 1) | PRIVACY 전용 API | **탭 자체를 감춘다** |
| 강제 파기 실행 · 정책 저장 | PRIVACY | 버튼 비노출 |

`ROLE_PRIVACY_ADMIN`이 없는 사용자에게 탭 1을 감추는 것은 편의가 아니라 필수다.
[client.ts](../../../recruit_front/src/api/client.ts)의 응답 인터셉터가 403을 받으면 `/403`으로 리다이렉트하므로,
권한 없는 호출이 한 번이라도 나가면 사용자가 화면 밖으로 튕긴다.
실제 차단은 백엔드가 하고, 프론트는 호출 자체를 만들지 않는다.

### 6.2 상단 — 보존 정책 카드

- `GET /admin/retention/policies`에서 `jobPostingId == null && enabled` 정책을 찾아 요약 표시
  ("공고 마감일 기준 5년(1825일) 보관 후 파기").
- 없으면 빨간 경고 + "정책 등록" 버튼 → 모달(보존 일수·기산점·사용 여부) → `POST /admin/retention/policies`.
- 있으면 "수정" → `POST /admin/retention/policies/{id}`. 보존 일수를 줄이는 저장은 확인 모달에서
  "다음 자동 파기 때 더 많은 지원서가 파기됩니다"를 경고한다.
- 전역 enabled 정책이 2건 이상이면(겹침 검증을 빠져나간 과거 데이터) 목록을 그대로 보여 주고 경고한다.

### 6.3 상단 — 자동 파기 카드

정책 카드 옆에 나란히 둔다. `GET /admin/retention/schedule` 한 번으로 전부 채운다.

| 표시 | 내용 |
|---|---|
| 스위치 | 자동 파기 on/off. `a-switch` + 확인 모달(끌 때만: "보존기간이 지난 개인정보가 자동으로 파기되지 않습니다") |
| 다음 파기 예정일 | `nextPurgeDate` + 보조 문구 "이 날짜부터 파기 대상이 생길 수 있습니다". `9999-12-31`이면 날짜 대신 **"예정 없음(파기 대상 없음)"** |
| 보조 문구 | "매일 03:00에 확인합니다. 예정일 전에는 스캔하지 않습니다." |
| 마지막 실행 | `lastRunAt` + `lastRunResult` 한글 라벨. `EXECUTED`면 해당 batch 상세로 가는 링크 |

- `lastRunResult` 라벨: `SKIPPED_DISABLED` "실행 안 함(꺼짐)" · `SKIPPED_NOT_DUE` "실행 안 함(예정일 전)" ·
  `NO_TARGET` "대상 없음" · `EXECUTED` "파기 실행" · `ERROR` "실패"(빨강).
- `ERROR`이면 "서버 로그를 확인하세요" 안내를 함께 띄운다. 화면에 예외 내용을 노출하지 않는다.
- 스위치는 `ROLE_PRIVACY_ADMIN`이 없으면 `disabled`. 상태 표시는 그대로 보인다.
- 정책이 없으면 스위치를 켤 수 없다(켜도 전건 스킵되므로). "먼저 보존 정책을 등록하세요" 안내.

### 6.4 탭 1 — 삭제 요청 파기

1. 검색바: 이름 / 휴대폰 / 이메일. 최소 1개 입력 없이 조회하면 "검색 조건을 1개 이상 입력하세요".
2. 결과 표: 이름 · 이메일 · 휴대폰 · 지원 n건 · 최근 지원일 · 상태 뱃지(보류 중 / 일부 파기됨 / 파기 완료).
   연락처는 **마스킹하지 않는다**(관리자 화면 규칙).
3. 행 클릭 → 우측 드로어:
   - 지원자 기본 정보
   - 지원서 목록(공고명 · 지원일 · 상태 · 판정 뱃지)
   - 판정 뱃지: `APPLICATION_NOT_TERMINAL` → "진행 중", `ALREADY_PURGED` → "파기됨",
     `RETENTION_HOLD` → "보류", 그 외 적격/기간 미도래는 "파기 가능"
   - 하단 `삭제 요청 파기` 버튼. 보류가 있으면 비활성 + "보류가 걸려 있어 파기할 수 없습니다"
4. 확인 모달(`MessageSendConfirmModal` 패턴 재사용):
   - 대상 이름·이메일·휴대폰 재표시
   - 지원서 n건 중 진행 중 m건이 함께 파기된다는 경고(m > 0일 때만, 빨간 배너)
   - "계정이 익명화되어 이 사람은 다시 로그인할 수 없습니다" 고정 문구
   - 사유 선택(라디오 3개)
   - `confirm` 체크박스 — 체크 전에는 실행 버튼 비활성
5. 실행 후 결과 알림: "지원서 n건 파기, m건 스킵(사유), 계정 익명화 완료". 드로어를 닫고 검색 결과를 새로 고친다.

### 6.5 탭 2 — 자동 파기 이력

- `GET /admin/retention/purge-batches` 페이지네이션 표: 실행일시 · 모드 · 트리거 · 요청자 · 상태 ·
  대상/파기/스킵/실패 건수.
- 행 클릭 → 드로어: batch 집계 + item 목록. item은 최대 수천 건일 수 있으므로 **스킵 사유별 집계를 먼저**
  보여 주고, 지원서 id 목록은 그 아래 접이식으로 둔다.
- `PARTIAL_FAILED`·`FAILED` batch는 붉은 뱃지로 구분한다.

### 6.6 프론트 파일

| 파일 | 역할 |
|---|---|
| `src/api/admin/retentionApi.ts` | 정책·검색·상세·강제 파기·batch 목록 호출 |
| `src/types/admin/retention.ts` | 요청·응답 타입 |
| `src/views/admin/retention/AdminRetentionView.vue` | 화면 뼈대(정책 카드 + 탭) |
| `src/views/admin/retention/RetentionPolicyCard.vue` | 정책 요약·편집 모달 |
| `src/views/admin/retention/RetentionScheduleCard.vue` | 자동 파기 on/off·다음 예정일·마지막 실행 |
| `src/views/admin/retention/DataSubjectSearchPanel.vue` | 검색 + 결과 표 |
| `src/views/admin/retention/DataSubjectDrawer.vue` | 지원자 상세 + 파기 버튼 |
| `src/views/admin/retention/ForcedPurgeConfirmModal.vue` | 확인 모달 |
| `src/views/admin/retention/PurgeBatchPanel.vue` | 자동 파기 이력 표 + 드로어 |
| `src/views/admin/retention/retentionLabel.ts` | 사유코드·상태·판정 라벨 매핑(+단위 테스트) |
| `src/routes/adminRoutes.ts` | `/admin/retention` 라우트 추가 |

## 7. 데이터 흐름

```
[삭제 요청]
관리자 → GET data-subjects(이름/연락처) → 목록
       → GET data-subjects/{id} → 지원서별 eligibility
       → POST purge-batches/force { applicantId, reasonCode, confirm }
          → ForcedPurgeService
             → PurgeBatch(EXECUTE, DATA_SUBJECT_REQUEST) 시작
             → 지원서마다 PurgeItemProcessor.process(forced=true)  [REQUIRES_NEW]
                → hold/already-purged면 SKIPPED
                → 아니면 PII tombstone → 첨부 saga → PURGED 또는 PURGE_PENDING
             → Applicant.purgePersonalData (남은 지원서 없음 확인 후)
             → 집계 + PURGE_FORCED 감사
          → PurgeBatchDetailResponse

[자동 파기] 매일 03:00
스케줄러 → RetentionScheduleService.findSetting()
        → enabled=false면 SKIPPED_DISABLED 기록 후 종료
        → nextPurgeDate() > today면 SKIPPED_NOT_DUE 기록 후 종료
        → RetentionDryRunService.dryRun("SYSTEM")
        → ELIGIBLE 0건이면 NO_TARGET 기록 후 종료
        → PurgeExecutionService.execute({confirm, sourceDryRunBatchId}, "SYSTEM")
        → EXECUTED + batchId 기록. 예외는 ERROR 기록 후 삼킴
```

## 8. 오류 처리

| 상황 | 처리 |
|---|---|
| 검색 조건 미입력 | 400, 화면에서 사전 차단 |
| 없는 applicantId | 404 "지원자를 찾을 수 없습니다." |
| active hold 보유 | 400 + 화면 버튼 비활성 |
| `confirm != true` | 400 |
| 이미 전부 파기된 지원자 | item 전부 `SKIPPED(ALREADY_PURGED)`, batch는 `COMPLETED`. 화면은 "이미 파기된 지원자입니다" |
| item 1건 실패 | 그 건만 `FAILED`, 나머지 계속. batch `PARTIAL_FAILED` |
| 첨부 바이너리 삭제 실패 | `PURGE_PENDING` 유지. reconcile이 수습(이번 범위에 화면 없음, 로그로 확인) |
| 스케줄러 실행 중 예외 | 로그 + `lastRunResult=ERROR`. 다음날 재시도 |
| 정책 없음 | `nextPurgeDate`가 9999-12-31이라 게이트에서 멈춘다 + 화면 상단 빨간 경고 + 스위치 잠금 |
| 자동 파기 꺼짐 | 매일 `SKIPPED_DISABLED`만 기록. 만료 건은 쌓이기만 한다 |
| 설정 행 없음 | `enabled=false`로 간주(안전 기본값) |

## 9. 알려진 위험

- **비가역**: 강제 파기는 되돌릴 수 없다. 확인 모달의 이름·연락처 재표시와 `confirm` 체크가 유일한 방어선이다.
- **첫 가동 부하**: 상한이 없어 누적 만료분이 한 번에 처리된다. 운영 투입 첫날은 수동으로 dry-run을 돌려
  건수를 확인한 뒤 스케줄을 켜는 절차를 권한다(운영 문서에 기록).
- **직무 분리 구멍**: `/api/admin/role-mappings/**`에 전용 매처가 없어 `ROLE_RECRUIT_ADMIN`이 자기에게
  `ROLE_PRIVACY_ADMIN`을 붙일 수 있다(카드 `## 함정·결정` 기록, 이번 범위 밖).
- **감사 로그는 파기되지 않는다**: `ActivityLog`에 남는 것은 `applicantRefHash`·행위자·ip·ua뿐이어야 한다.
  metadata에 PII를 넣지 않는 것이 계약이고 `AuditMetadataContractTest`가 이를 고정한다.
- **정책 변경의 파급**: 보존 일수를 줄이면 다음 새벽에 더 많은 지원서가 비가역 파기된다. 화면 경고로만 막는다.
- **꺼 두고 잊기**: 자동 파기가 꺼진 채 방치되면 보존기간이 지난 개인정보가 계속 남는다. 화면 카드가
  꺼짐 상태를 상시 노출하지만, 그걸 보는 사람이 없으면 소용없다. 운영 점검 항목에 넣어야 한다.
- **예정일은 하한**: `nextPurgeDate`는 보존기간만 계산한 값이라, 그날 실제로 파기되는 건수는 0일 수 있다
  (전형이 종료되지 않은 지원서 등). 화면 문구를 "이 날짜부터 파기 대상이 생길 수 있습니다"로 쓴다.

## 10. 테스트

**백엔드**
- `ForcedPurgeServiceTest`: hold 거부 / already-purged 스킵 / 기간 미도래여도 파기 / 진행 중 전형도 파기 /
  지원서 0건 계정 익명화 / 계정 익명화 후 재요청 시 404 또는 스킵
- `PurgeItemProcessorTest`: forced 플래그가 hold·already-purged만 검사하는지
- `DataSubjectLookupServiceTest`: 조건 미입력 400 / 하이픈 제거 매칭 / 상한 50 / 익명화 계정 제외
- `AdminRetentionControllerTest`: 신규 3개 엔드포인트의 401/403/200 권한 행렬
- `RetentionPurgeSchedulerTest`: enabled=false면 dry-run 미호출 / 예정일 전이면 dry-run 미호출 /
  예정일 당일·경과면 실행 / ELIGIBLE 0건이면 execute 미호출 / 예외를 삼키고 `ERROR` 기록 /
  각 경로의 `lastRunResult` 값
- `RetentionScheduleServiceTest`: 설정 행 없으면 `enabled=false` / 토글 시 행 생성·감사 기록 /
  `nextPurgeDate` — 마감 공고 없음 → 9999-12-31, 정책 없음 → 9999-12-31,
  미파기 지원서를 가진 가장 이른 마감일 + 1825일, 전부 파기된 공고는 제외
- `AuditMetadataContractTest`: `ForcedPurgeMetadata` allowlist
- 실행: 카드 `## 검증`의 명령 + 신규 테스트

**프론트**
- `retentionLabel.spec.ts`: 판정 사유코드 → 뱃지 라벨·색 매핑
- `npm run type-check`, 필요 시 `npm run build`

## 11. 문서 갱신

- `docs/domains/privacy-audit.md`: API 계약 5행 추가(🟡→🟢), 파일 지도(백엔드 신규 + 프론트 절 교체),
  자동 파기 스케줄러·설정 테이블·수동 DDL 목록,
  규칙·불변식(forced 판정 축약 규칙·스케줄러·SYSTEM actor), 감사 호출 규약 표에 `PURGE_FORCED`,
  `미구현` 항목에서 forced purge·스케줄 실행 제거
- `docs/domains/_index.md`: privacy-audit 행의 키워드에 화면 경로 추가
- `node tools/check-docs.mjs` 0건
