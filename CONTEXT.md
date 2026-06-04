# Recruit Backend — Context

신영증권 채용 Renewal 백엔드의 도메인 용어 glossary. 구현 세부는 `docs/codex` 문서를 따르고, 이 파일은 용어 정의만 유지한다.

## Language

### Export / Reporting (Phase 07)

**Export**:
관리자가 조회 화면의 데이터를 Excel(xlsx) 또는 PDF 파일로 내려받는 행위. read-only 이며, 도메인 상태를 변경하지 않는다.
_Avoid_: 다운로드(파일 첨부 download와 혼동), 출력

**Excel download**:
admin 조회 결과(applications/stage results/interviews/evaluations)를 xlsx로 export 하는 기능. 기존 list 필터를 재사용하고 page는 무시해 필터된 전체 행을 내보낸다.

**Excel upload**:
관리자가 작성한 xlsx를 올려 `StageResult`를 bulk로 **변경**하는 기능. Phase 07에서 유일하게 쓰기가 발생하는 지점이며, preview(검증·미적용)와 commit(적용)을 분리한다. `InterviewEvaluation`은 Phase 06 경계(평가는 배정 면접관 본인만 작성, 평가 독립성)상 upload 대상에서 **제외**한다 — admin은 엑셀로 평가 등급을 입력/수정하지 않는다.
_Avoid_: Excel import (이 프로젝트에서는 upload로 통일)

**Statistics**:
지원 데이터를 집계한 read-only 수치. 개별 개인정보가 아니라 집계값만 노출한다. `jobPosting` 단위의 **전형 funnel** 형태로 제공한다.

**전형 funnel**:
한 공고에서 "접수 → 그 공고의 stageOrder 순서대로 각 stage PASSED" 로 이어지는 단계별 인원 추이. 동적 stage 구조에 자동 적응한다.

**모집단 P (접수)**:
funnel의 분모가 되는 고정 지원서 코호트. `submittedAt != null`(한 번이라도 제출) 인 지원서를 포함하며 현재 status 와 무관하다(DRAFT·미제출 제외). 각 단계는 동일 P 위에서 계산한다. P 중 `status == SUBMITTED` 는 `currentlySubmittedCount`, `status == WITHDRAWN`(제출 후 철회) 는 `withdrawnCount` 로 별도 표기한다. 코호트를 제출이력으로 고정하므로 조회 시점이 달라도 funnel 이 재현 가능하다.
_Avoid_: "현재 SUBMITTED 집합"(철회 시 줄어들어 재현 불가)

**Dimension (집계 축)**:
funnel을 쪼개는 기준. 전체 / 분야별(`jobPosition`) / 학교별(`schoolName`) / 자격별(`certificateName`). 모든 dimension은 **지원자(application) 단위 distinct** 로 센다. 학교별은 **최종학력(가장 높은 `EducationLevel`) 1교만**, 자격별은 **자격명별 보유 지원자 distinct**. free-text 축(학교·자격)은 topN + '기타' 버킷으로 cardinality를 제한한다.

**funnel 단계 분포**:
각 stage 에서 P 멤버를 `StageResultStatus` 6종(PASSED/FAILED/ABSENT/HOLD/PENDING/WITHDRAWN) + `NO_RESULT` 의 7개 버킷으로 분류한 값. 합은 항상 `|P|`. PASSED 기준으로 누적 비율(P 대비)과 직전 단계 대비 전환율 두 가지를 함께 제공한다.

**NO_RESULT**:
funnel 응답 전용 synthetic 버킷. 해당 stage 에 `StageResult` row 자체가 없는(미초기화·미도달) P 멤버를 가리킨다. DB `StageResultStatus` enum 값이 **아니며**, Excel upload 의 허용 입력값도 **아니다**.
_Avoid_: PENDING(= StageResult row 존재, 결정 전 — NO_RESULT 와 구분)

**Application PDF**:
지원자 한 명의 지원서를 PDF로 렌더링한 출력물. 본질적으로 개인정보를 포함한다.

**Audit log (export)**:
누가/언제/어떤 dataset을/어떤 필터로/몇 행을 export 했는지 기록. 현재는 SLF4J 구조적 로그로만 남기고, 영속 `ActivityLog` 도메인 생성 시 그쪽으로 이관한다.

### Master data (Phase 08)

**CommonCode**:
관리자가 런타임에 관리(추가/수정/비활성)하는 **코드성 lookup master**. `groupCode + code` 로 식별하고 `displayName`(표시명)·정렬·활성 여부를 갖는다. "고정적이고 비즈니스 분기에 쓰이는 값"은 enum으로 두고, CommonCode 는 그렇지 않은(관리자가 목록을 늘릴 수 있는) 값에 쓴다. 기존 enum 을 CommonCode 로 바꾸는 것은 Phase 08 범위가 **아니다**(추가형 도입, 전환 0). 전환은 "관리자가 런타임에 값을 추가해야 한다"는 구체 요구가 생긴 group 에 한해 별도로 진행한다.
_Avoid_: enum 과 CommonCode 를 같은 값에 동시에 두는 것(중복 진실원)

**School (학교 master)**:
지원자 학력 입력의 **자동완성/검색** 기준이자 **학교별 통계 grouping** 의 기준이 되는 master data. 외부 `schoolCode`(있으면) 또는 `(schoolName, schoolType, region)` 로 식별한다. `ApplicationEducation` 은 자유입력 `schoolName`(snapshot)을 그대로 유지하고, 지원자가 자동완성에서 고른 경우에만 optional `schoolId` 로 master 를 참조한다(직접입력=null=미매칭). master 는 강한 FK 가 아니라 application-level 참조다.
_Avoid_: `schoolName`(지원서의 자유입력 표시값) 과 `School.schoolName`(master 정규명) 을 같은 것으로 취급

### Privacy / Audit (Phase 09)

**ActivityLog**:
누가/언제/무엇에/어떤 행위를 했고 결과가 무엇이었는지를 남기는 **append-only 감사 증적**. **지원자 원문 PII 미저장(applicant raw PII-free)** — 단 행위자/접속/대상 식별정보(`actorId`, `ipAddress`, `userAgent`, `applicationId`, `applicantRefHash`)는 포함하므로 '완전 PII-free 테이블'이 **아니다**. 영속 DB row 가 source of truth 이고, 기존 SLF4J `recruit.audit.*` 라인은 운영 로그 보조 용도다. 정정이 필요하면 row 수정이 아니라 correction event 를 추가 기록한다.
_Avoid_: '완전 PII-free 테이블'이라는 표현; 일반 애플리케이션 로그(SLF4J)와 혼동; 전역 접근/페이지 추적 로그.

**감사 이벤트 (Audit event)**:
ActivityLog 에 남기는 단위 행위. 대상은 **정보 반출(export/PDF/admin download)**, **핵심 관리자 상태 변경**(StageResult 변경/발표/확정, evaluation reopen, application admin 처리, retention 정책 변경, purge), **파기 lifecycle**(대상 산정/요청/성공/실패/스킵/보존 예외)에 한정한다. 전역 `VIEW_PAGE`/`ACCESS_API` blanket 추적은 감사 이벤트가 **아니다**(Phase 09 범위 밖, 후속 후보).

**Egress (정보 반출)**:
개인정보·평가자료·첨부가 시스템 밖으로 나가는 행위(Excel/PDF/다운로드). **fail-close** — 감사 기록(commit)이 성공해야 산출물을 반환한다. 기록 후 스트리밍이 깨져도 over-record(누락보다 안전)로 허용한다.

**actionResult**:
감사 행위의 결과 분류 — `SUCCESS`/`FAILURE`/`DENIED`/`SKIPPED`/`CONFLICT`. `CONFLICT`(낙관적 락/버전 충돌)는 검색·장애분석 가치를 위해 `FAILURE` 와 **분리**한다. 상태성(`STARTED`/`REQUESTED`/`COMPLETED`)은 결과가 아니라 `actionType` 으로 표현한다.

**actorRoleSnapshot**:
감사 row 의 행위자 권한을 **행위 시점에 고정 기록**한 스냅샷. 라이브 권한 조회/join 이 아니라 그때의 권한이어야 감사 무결성이 유지된다. `ADMIN`/`INTERVIEWER` 구분도 `actorType` 이 아니라 이 값으로 한다.

**ActivityLog lifecycle policy (후속)**:
ActivityLog **자체**의 보존기간·접근통제·`ipAddress`/`userAgent` 마스킹·N년 후 삭제/회전/아카이빙 정책. **Phase 09 지원자 개인정보 파기 job 범위 밖**의 후속 설계 대상이다. 지원자 파기 job 은 `applicationId`/`applicantRefHash` 를 참조하는 ActivityLog row 를 수정·삭제·마스킹하지 **않는다**(감사 증적 보존 우선).

**파기 (Purge / 개인정보 파기)**:
지원자 **원문 PII 를 비가역 소거**하고, 통계/감사 연결에 필요한 **비식별 tombstone** 만 남기는 행위. 기본 방식 = **tombstone anonymization + 첨부 바이너리 물리삭제**(ADR-0005). crypto-shred·전면 hard delete 가 아니다.
_Avoid_: '삭제(delete)'(첨부 바이너리 외에는 row 를 지우지 않음), soft delete(첨부 lifecycle 의 `markDeleted` 와 혼동).

**파기 tombstone**:
파기 후 남는 **비식별 골격 row**. 보존 후보 = `applicationId`/`jobPostingId`/`jobPositionId`/stage·result status code/submitted date bucket/`purgedAt`/`purgeBatchId`/`purgeResult`. 원문 PII(name/email/phone/ci/address/answers/섹션 원문)는 없다.

**ref-count 익명화 (Applicant)**:
Applicant 공통 PII(`email`/`name`/`phone`/`ci`)는 그 Applicant 의 **모든** JobApplication 이 파기 대상이 됐을 때만 익명화한다. 일부 지원서만 파기됐으면 다른 살아있는 지원서가 연락처를 필요로 하므로 보존한다. **`ciHash` 는 보존하지 않는다** — `HashUtil.sha256(ci)`(plain SHA-256, HMAC 아님)이고 회원가입이 `existsByCiHash` 로 중복가입을 막으므로, 그대로 두면 CI 연결자가 잔존해 비가역 파기가 깨진다. ref0 시 `ci=null`, `ciHash="PURGED:"+UUID` 로 overwrite(중복가입 차단은 파기 후 미보장 — 파기 우선).
_Avoid_: `ciHash` 를 "HMAC·가명이라 보존해도 안전"하다고 보는 것(plain SHA-256 임); ActivityLog 의 `applicantRefHash`(HMAC+pepper, 감사용)와 혼동.

**retentionAnchorAt**:
파기 보존기간 계산의 기준 시점. **"지원 접수 마감"이 아니라 "해당 채용 프로세스가 실질적으로 종료된 시점"**이다. 공고 단위 anchor 로 기본 소스는 `JobPosting.hiringEndedAt`(신규 필드). **암묵적 `closedAt` fallback 은 하지 않는다** — `hiringEndedAt` 이 null 이면 `ANCHOR_NOT_FIXED` 로 SKIP. `closedAt` 을 기준으로 쓰려면 `RetentionPolicy.baselineType = CLOSED_AT` 을 **명시 선택**해야 한다(암묵 fallback 은 오파기 위험).
_Avoid_: `closedAt`(공고 close 시각)을 암묵 fallback 으로 retention 기준에 끌어쓰는 것; `finalizedAt`(의미가 넓고 모호) 네이밍.

**RetentionPolicy**:
보존기간 정책. **전역 기본값 + 공고별 override** 구조. `retentionPeriod`/`baselineType`/`enabled`/`effectiveFrom`/`effectiveTo`(+ override 시 `jobPostingId`) 를 가지며, 변경은 ActivityLog 에 committed change(in-tx)로 기록한다. `baselineType` = `HIRING_ENDED_AT`(기본) / `CLOSED_AT`(명시 선택). 법정 일수는 코드에 하드코딩하지 않고 설정/정책으로 주입한다. **선택 규칙**(dry-run 결정성): override 우선 → 없으면 global default, `effective` 는 `scanAt` 기준 평가, 같은 jobPostingId 기간 overlap 금지, global enabled 동시 1개, 없으면 `POLICY_NOT_FOUND` SKIP.

**hiringEndedAt 수동 확정**:
`retentionAnchorAt` 소스인 `JobPosting.hiringEndedAt` 은 **자동 세팅하지 않는다**(현 `close()` 는 `status`/`closedAt` 만). "공고 마감" ≠ "채용 프로세스 종료" 이므로 관리자가 `POST /api/admin/retention/job-postings/{id}/anchor`(ROLE_PRIVACY_ADMIN, 감사 `RETENTION_ANCHOR_SET`)로 수동 확정한다. 미확정 = `ANCHOR_NOT_FIXED` SKIP.

**RetentionHold (보존 예외)**:
파기 자동 대상에서 제외하는 보존 의무/예외. **Phase 9 는 관리자 수동 hold 만(manual only)** — `StageResultStatus` 에 `HIRED`/`ONBOARDED`/`HR_TRANSFERRED` 가 없어 "onboarded 자동 제외" 의 도메인 근거가 없다(자동 onboarded-hold 는 `ApplicationHireStatus` 신규 도메인 필요 → 후속). **중간 전형 PASSED 는 제외 기준이 아니다** — 불합격·전형포기·미응시·최종합격 후 입사포기·채용 미확정 종료는 retention 경과 시 모두 파기 대상이 될 수 있다. hold 건은 파기 시 `SKIPPED` + `RETENTION_HOLD` 로 감사.
_Avoid_: 합격/onboarded 를 코드로 자동 hold(현 도메인에 상태 없음).

**terminal application status (파기 적격 전제)**:
파기는 지원서가 **종결 상태**일 때만 적격하다. 구체 판정(9c 계약, 실제 enum 검증 완료) = `JobApplication.status == WITHDRAWN` **OR** (`Stage.finalStage==true` row 정확히 1개 + `Stage.status ∈ {RESULT_ANNOUNCED, CLOSED}` + 해당 application+finalStage `StageResult` 존재 + `resultStatus != PENDING` + `decidedAt != null`). finalStage 부재/2개 이상 = `INVALID_STAGE_CONFIGURATION`, 그 외 미충족 = `APPLICATION_NOT_TERMINAL` 로 SKIP. 적격성 = `anchor 종료 + retentionPeriod 경과 + not purged + not hold + terminal`.
_Avoid_: "확정"을 구현자가 임의 해석하는 것(이 query 가 계약).

**PurgeBatch / PurgeJobItem**:
파기 실행의 상세 원장. **`PurgeBatch`** = dry-run 또는 execute **1회 실행 단위**(mode/criteria/counts/status). **`PurgeJobItem`** = application 별 판정·실행 결과. 둘 다 PII-free. **append-only 가 아니라 "delete 금지 mutable ledger/control table"** — batch 는 `RUNNING→COMPLETED/PARTIAL_FAILED/FAILED`, item 은 pending→failed→retry→PURGED 로 상태 전이(update 허용, delete 금지). append-only 는 `ActivityLog` 에만 해당. `ActivityLog` 는 이 원장의 **coarse index** 로만 쓰고(batch 시작/완료/부분실패/실패 + 집계 metadataJson), item 결과를 중복 기록하지 않는다.
_Avoid_: PurgeBatch/PurgeJobItem 을 append-only 라고 부르는 것(상태 전이 있음).

**dry-run vs execute batch**:
dry-run batch 는 `wouldPurge`/`wouldSkip`+reasonCode **예측만** 남기고 도메인을 바꾸지 않는다. execute batch 는 실제 파기 결과를 남기며 `sourceDryRunBatchId`(nullable)로 어떤 dry-run 을 보고 실행했는지 연결한다. **execute 는 dry-run item 을 그대로 믿지 않고 실행 시점에 eligibility 를 재검증한다.**

**item-level atomicity (파기 트랜잭션)**:
application 1건의 tombstone/anonymize/ref-count 판단은 **하나의 item 트랜잭션** 안에서 처리한다(all-or-nothing per application). batch 는 **비원자적 집계 컨테이너** — 한 item 실패는 그 item 만 `FAILED` 로 격리하고 batch 는 계속한다(최종 `COMPLETED`/`PARTIAL_FAILED`). batch `FAILED` 는 시작 자체 실패 또는 criteria 생성 실패에 한정한다. `ALREADY_PURGED` 는 오류가 아니라 idempotent skip.
_Avoid_: 엑셀 upload 식 "batch 전체 all-or-nothing"(대량·비가역 sweep 에는 부적합).

**PURGED (상태의 의미)**:
**관계형 PII 제거 + 첨부 바이너리 소멸 확인까지 완료된 최종 상태**. 관계형 PII 만 지우고 파일 바이트가 남아 있으면 `PURGED` 가 **아니다**(→ `BINARY_DELETE_PENDING`/`BINARY_DELETE_FAILED`/`PARTIAL_FAILED`). **"DB 는 PURGED 인데 디스크에 파일 바이트 잔존"은 절대 불허**한다.

**파기 saga (stateful saga + reconciliation)**:
첨부 바이너리 삭제는 DB 트랜잭션과 원자화할 수 없으므로 트랜잭션이 아니라 saga 로 설계한다. ① DB tx(PII·`originalFilename` 제거, attachment/item = `BINARY_DELETE_PENDING`, `JobApplication.purgeResult = PURGE_PENDING`, commit) → ② 파일 물리 삭제(`deleteIfExists` 멱등 + 존재 재확인, 이미 없음 = `MISSING_AS_SUCCESS`) → ③ DB tx(소멸 확인 → `PURGED`/`purgedAt`, 실패 → `BINARY_DELETE_FAILED`/`PARTIAL_FAILED`, 재시도 대상). reconciliation sweep 이 pending/failed 를 재처리하고, `storage-health-scan` 은 "DB PURGED 인데 파일 존재"를 치명적 불일치로 탐지한다. "파일 소멸 + DB pending" 은 프라이버시상 안전(나중에 PURGED 승격), 역방향은 불허. `PhysicalFileStatus` 는 기존 `DELETED` 를 **`SOFT_DELETED`** 로 개명(soft-delete 와 purge 물리삭제 의미 분리)하고 `BINARY_DELETE_PENDING`/`BINARY_DELETED`/`BINARY_DELETE_FAILED` 를 추가한다 — 기존 `markDeleted()` soft-delete 와 혼동 금지.

**AuditMetadata (typed)**:
ActivityLog `metadataJson` 은 자유 `Map`/raw JSON 이 아니라 actionType 별 **sealed `AuditMetadata` typed record**(ExportMetadata/PdfMetadata/UploadMetadata/StageResultChangeMetadata/PurgeBatchMetadata…)로 고정한다. 직렬화는 `ActivityLogService` 내부에서만 수행하고 호출부는 typed record 만 넘긴다(PII-free 보장). actor/ip/ua/correlationId/occurredAt 는 metadata 가 아니라 ActivityLog 컬럼. **업로드 원본 파일명은 PII 가능**(예: "홍길동_…xlsx")이라 원문 저장 금지 — `sourceFileNameHash`(SHA-256)+`sourceFileExtension` 만(ActivityLog·SLF4J 공통).
_Avoid_: 호출부에서 `Map<String,Object>`/raw JSON 문자열 전달; 업로드 `sourceFileName` 원문 저장.

**ROLE_PRIVACY_ADMIN vs ROLE_RECRUIT_ADMIN (파기/감사 권한 분리)**:
비가역 파기·민감 작업은 채용 운영 권한과 **분리**한다. **ROLE_PRIVACY_ADMIN 전용** = purge execute, RetentionPolicy/RetentionHold 변경, ActivityLog 민감필드(`ipAddress`/`userAgent`) 원문 조회, purge batch 상세/실행결과 원문. **ROLE_RECRUIT_ADMIN 까지 허용** = retention dry-run/scan, retention 결과 조회, ActivityLog **마스킹** 목록, RetentionPolicy read-only. 두 권한 모두 `DeptRoleMapping` 파생(하드코딩 금지). narrow requestMatcher 를 broad `/api/admin/**` 보다 **먼저** 배치해야 한다.

**forced purge (후속)**:
정보주체 삭제요청 기반 **retention 미도래 우회 파기**. retention-based purge 와 다른 트리거다. **Phase 09 제외** — `triggerType` enum 슬롯(`DATA_SUBJECT_REQUEST`/`FORCED_PURGE`)만 남기고 endpoint/실행 로직은 만들지 않는다. Phase 09 파기는 **eligibility 충족 건만** 대상.
_Avoid_: "retention 무시 즉시 purge" 로 단순 구현(법적 보존/분쟁/진행중 거부·hold 필요).

## Flagged ambiguities

- **CI (`ci`/`ciHash`)**: NICE 본인확인의 연계정보. 민감 식별자이므로 **어떤 export(Excel/PDF)에도 절대 포함하지 않는다**. `password`·암호화키도 동일하게 절대 노출하지 않는다. `name`/`phoneNumber`/`email`은 admin 운영(연락·발송) 목적상 평문으로 export 하되 audit 로그를 남긴다.
- **"학교별 통계"의 학교**: Phase 08 의 `School` master + `ApplicationEducation.schoolId`(optional, 자동완성 선택 시에만) 로 **매칭된 학력**은 정확히 grouping 한다. 직접입력(미매칭)은 `schoolId == null` 이라 통계에서 **'기타'(미매칭)** 버킷으로 묶고, 과거 데이터의 free-text 는 소급 매칭하지 않는다(통계 설계 시 이 한계를 전제).
