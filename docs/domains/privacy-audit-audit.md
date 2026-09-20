# 감사 로그 (`privacy-audit-audit`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [privacy-audit](privacy-audit.md)(보존·파기 — `ActivityLogService`로 `PURGE_SCAN`·`PURGE_EXECUTE`·`PURGE_RECONCILE`·`PURGE_FORCED`·`RETENTION_*` 이벤트를 기록한다) · [admin-application](admin-application.md)(반출 감사) · [stage-result](stage-result.md) · [interview](interview.md) · [attachment](attachment.md) · [auth-account](auth-account.md)(`SecurityConfig`) · [role-menu](role-menu.md)(역할 매핑)

## 요약

- **감사 로그**: `ActivityLog`(append-only). 여러 도메인이 `ActivityLogService` 2경로로 기록, 관리자는 `/admin/audit/**`로 조회.
- 감사 이벤트 = 정보 반출(엑셀·PDF·첨부 다운로드), 핵심 관리자 변경, 보존·파기(발행처는 [privacy-audit](privacy-audit.md)). 반출은 fail-close(감사 커밋 후에만 반환).
- 조회 화면은 **로그 조회**(`/admin/logs`)의 `감사 로그` 탭이다. 탭 본문·상세만 이 카드 소유고 페이지 껍데기는 [client-event-log](client-event-log.md) 소유다. 기록용 API는 없다.
- 권한(ADR 0007): 조회 = `ROLE_RECRUIT_ADMIN`·`ROLE_PRIVACY_ADMIN`(ip·ua 원문은 PRIVACY만). 기록 자체는 별도 쓰기 API 없이 각 도메인 서비스 내부에서 호출한다.

## 용어

| 용어 | 뜻 |
|---|---|
| `ActivityLog` | 감사 행. 수정·삭제 없음(정정은 새 이벤트). 지원자 원문 PII는 없지만 `actorId`·ip·ua·`applicationId`가 있어 완전 PII-free는 아니다 |
| 감사 이벤트 | 정보 반출(엑셀·PDF·첨부 다운로드), 핵심 관리자 변경, 보존·파기만. 반출은 fail-close(감사 커밋 후에만 반환) |
| `actorRoleSnapshot` | 행위 시점 authority 목록(쉼표 연결) |
| `applicantRefHash` | `HMAC_SHA256(AUDIT_HMAC_SECRET, "APPLICANT:"+applicantId)` — 파기 후에도 같은 지원자를 가명으로 묶는다 |
| 미구현 | 감사 로그 자체 수명 정책 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AdminAuditController.java` | `/admin/audit/activities` 검색·단건, ip/ua 마스킹 |
| service | `{BE}/service/ActivityLogService.java` | 감사 기록 2경로·문자열 정제·HMAC·metadata 직렬화 |
| service | `{BE}/service/Audit*` | `AuditEvent`(기록 요청)·`AuditMetadata`(sealed, permits 11)·`AuditActorContext`·`AuditRequestContextResolver`(행위자 해석)·`AuditActivityReadService`(조회 가드) |
| entity | `{BE}/domain/entity/ActivityLog.java` | 감사 행(BaseEntity 미상속) |
| repository | `{BE}/domain/repository/ActivityLogRepository.java` | save·조회·검색만 |
| dto | `{BE}/dto/response/AuditActivityResponse.java` | 감사 행 + 마스킹 |
| enum | `{BE}/enumeration/Audit*` | `AuditActionType`(21)·`AuditActionResult`(5)·`AuditTargetType`(11)·`AuditReasonCode`(13) |
| enum | `{BE}/enumeration/ActorType.java` | `EMPLOYEE`·`SYSTEM`·`APPLICANT`·`ANONYMOUS`. `ActivityLog.actorType` 필드값 |
| exception | `{BE}/exception/InvalidAuditQueryException.java` | 400 |
| exception | `{BE}/exception/ActivityLogNotFoundException.java` | 404 |
| exception | `{BE}/exception/InvalidActivityLogException.java` | 감사 행 필수값 위반 — **핸들러 없음 → 500** |
| config | `{BE}/config/AuditConfig.java` | HMAC 키 주입·누락 시 기동 실패 |
| config | `{BE}/common/hash/AuditHmac.java` | HMAC-SHA256 hex |
| test | `{BT}/controller/AdminAuditControllerTest.java` | 권한·마스킹·가드 |
| test | `{BT}/service/ActivityLogServiceTest.java` | 2경로 롤백·정제·HMAC |
| test | `{BT}/service/Audit*` | 조회 가드, metadata allowlist |
| test | `{BT}/service/AuditRequestContextResolverTest.java` | 행위자 해석(`resolve`) — retention·purge 서비스도 이 로직을 공용으로 쓴다 |
| test | `{BT}/domain/repository/ActivityLogRepositoryTest.java` | 매핑·필수값 |
| test | `{BT}/common/hash/AuditHmacTest.java` | HMAC |
| test | `{BT}/config/AuditConfigTest.java` | 키 누락·fallback·`prod` |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| view | `{FE}/views/admin/log/AuditLogPanel.vue` | 로그 조회 `감사 로그` 탭 본문(필터·목록·페이저·크로스 점프) |
| view | `{FE}/views/admin/log/AuditLogDrawer.vue` | 감사 로그 상세 drawer |
| api | `{FE}/api/admin/adminAuditApi.ts` | `getActivities`·`getActivity` |
| types | `{FE}/types/admin/auditLog.ts` | 감사 조회 쿼리·응답 타입, enum 유니언 |

페이지 껍데기(`AdminLogView.vue`)와 두 탭 공용 유틸(`logLabel.ts`·`logQuery.ts`, 감사 enum 한글 라벨 포함)은 [client-event-log](client-event-log.md) 소유다. 라우트 `AdminLogInquiry`(`/admin/logs`)도 그 카드에 등록되어 있다.

## API 계약

응답은 `ApiResponse<T>`. `R·P` = `hasAnyAuthority(ROLE_RECRUIT_ADMIN, ROLE_PRIVACY_ADMIN)`. 비인증 401, 권한 없음 403.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/audit/activities | query 필터·`from`·`to`·`page`(0)·`size`(20) | `PageResponse<AuditActivityResponse>` | R·P |
| 🟢 | GET | /admin/audit/activities/{id} | 없음 | `AuditActivityResponse` | R·P |

### 엔드포인트 상세

- 매핑: FE `adminAuditApi.getActivities()` ↔ 목록, `adminAuditApi.getActivity()` ↔ 단건(`{FE}/api/admin/adminAuditApi.ts`). 코드 기준 🟢.
- enum query 값 오류는 400 `"Invalid request."`. 목록은 공통 `PageResponse`(`content`·`page`·`totalElements` 등).
- **감사 조회**: query `actorId`(완전일치·trim), `actionType`·`actionResult`·`targetType`(enum 이름), `jobPostingId`, `applicationId`, `from`·`to`(ISO `yyyy-MM-ddTHH:mm:ss`). 응답 = `ActivityLog` 컬럼(`traceId` 제외, `metadataJson`은 문자열). `ipAddress`·`userAgent`는 PRIVACY만 원문, 그 외 `"***"`(null은 null). 단건 없음 404.

## 규칙·불변식

**감사 기록 (`ActivityLogService`)**
- 2경로(ADR 0006): `recordInCurrentTx`(REQUIRED — 커밋된 변경의 성공 증적, 감사 실패 시 비즈니스도 롤백) / `recordRequiresNew`(실패·충돌 증적과 반출 fail-close, 비즈니스 롤백과 무관하게 남음). afterCommit·AOP 금지, 같은 빈 안 호출 금지(self-invocation이면 REQUIRES_NEW 무효). ({BE}/service/ActivityLogService.java — recordInCurrentTx, recordRequiresNew)
- 필수 `occurredAt`(Clock)·`actorType`·`actionType`·`actionResult`·`targetType`, `EMPLOYEE`·`APPLICANT`는 `actorId`도. 위반 = `InvalidActivityLogException`(500). ({BE}/domain/entity/ActivityLog.java — validateRequired, {BE}/service/ActivityLogService.java — validateActorIdPresence)
- 요청 유래 문자열은 CR/LF/TAB→공백, trim, 컬럼 길이 절단, 공백뿐이면 null(긴 입력으로 insert 실패 방지). `correlationId`가 비면 MDC 값, `applicantId`는 HMAC 입력으로만, `traceId`는 항상 null. ({BE}/service/ActivityLogService.java — toEntity, safe)
- 리포지토리는 `save`·`findById`·`count`·`search`만 노출. `AuditMetadata` permits 11개와 record 필드는 테스트가 allowlist로 고정(email·phone·ci·sourcefilename 등 이름 금지). ({BE}/domain/repository/ActivityLogRepository.java, {BT}/service/AuditMetadataContractTest.java)
- 서비스 계층 행위자 = `AuditRequestContextResolver.resolve(actor)`: principal username·authority → 없으면 넘긴 actor → 없으면 `ANONYMOUS`. ip = `getRemoteAddr()`(프록시 뒤면 프록시 주소). ({BE}/service/AuditRequestContextResolver.java — resolve)

**감사 호출 규약** — 지원자 자가 행위는 기록하지 않는다. 보존·파기 이벤트(`RETENTION_*`·`PURGE_*`)는 [privacy-audit](privacy-audit.md) 서비스가 기록한다.

| actionType | 기록 위치 | 경로 |
|---|---|---|
| `EXPORT_APPLICATIONS`·`_STAGE_RESULTS`·`_INTERVIEWS`·`_EVALUATIONS`·`_STAGE_RESULT_TEMPLATE`, `APPLICATION_PDF` | `ExportAuditLogger`·`PdfAuditLogger`([admin-application](admin-application.md)) | REQUIRES_NEW, fail-close |
| `STAGE_RESULT_UPLOAD` | `UploadAuditLogger`([stage-result](stage-result.md)) | 적용 in-tx / 거부 FAILURE·충돌 CONFLICT는 REQUIRES_NEW |
| `STAGE_RESULT_CORRECT`·`_ANNOUNCE`·`_CONFIRM` | `StageResultService`·`StageService`([stage-result](stage-result.md)) | in-tx |
| `EVALUATION_REOPEN` | `InterviewEvaluationAdminService`([interview](interview.md)) | in-tx |
| `ATTACHMENT_ADMIN_DOWNLOAD` / `_DELETE` | `AdminApplicationAttachmentController` / `ApplicationAttachmentDeleteService`([attachment](attachment.md)) | REQUIRES_NEW fail-close / in-tx |
| `RETENTION_POLICY_UPDATE`·`RETENTION_HOLD_SET`·`_RELEASE`·`RETENTION_ANCHOR_SET`·`PURGE_SCAN` | [privacy-audit](privacy-audit.md) 서비스 | in-tx |
| `PURGE_EXECUTE` | `PurgeBatchLifecycleService`([privacy-audit](privacy-audit.md)) | 완료·실패용 REQUIRES_NEW tx 안 in-tx |
| `PURGE_RECONCILE` | `PurgeReconciliationService`([privacy-audit](privacy-audit.md)) | REQUIRES_NEW 2건(시작·요약) |
| `PURGE_FORCED` | `PurgeBatchLifecycleService`([privacy-audit](privacy-audit.md)) | 완료·실패용 REQUIRES_NEW tx 안 in-tx |
| `RETENTION_POLICY_UPDATE`(스케줄 토글) | `RetentionScheduleService`([privacy-audit](privacy-audit.md)) | in-tx, `targetType=RETENTION_SCHEDULE`, metadata `operation`=`SCHEDULE_ENABLE`/`SCHEDULE_DISABLE` |

**감사 조회**
- size 1..100, page ≥0, `to` 기본 now, `from` 기본 `to`−30일, `from>to`·90일 초과 400(정확히 90일 허용). 정렬 `occurredAt desc, id desc`. ({BE}/service/AuditActivityReadService.java — search, validateRange)
- ip·ua 원문은 `ROLE_PRIVACY_ADMIN`만(principal 없으면 마스킹). ({BE}/controller/AdminAuditController.java — includeSensitive)

**감사 HMAC 키** ({BE}/config/AuditConfig.java — auditHmac)
- `audit.hmac-secret` = env `AUDIT_HMAC_SECRET`. 비면 기동 실패. 단 `audit.allow-fallback-secret=true`(env `AUDIT_ALLOW_FALLBACK_SECRET`)이고 active profile에 `prod`가 없으면 비운영 대체 값 + 경고, `prod`면 flag가 true여도 거부. 테스트 값은 `recruit_back/recruit_backend/src/test/resources/application.yaml`. 실제 키 값은 문서·코드에 쓰지 않는다.
- `AuditHmac` 입력 접두 `APPLICANT:`·`CERT_NO:`·`FILE_NAME:`. ci·email·phone 원문 입력 금지. ({BE}/common/hash/AuditHmac.java)

## 변경 레시피

### 다른 도메인에 새 감사 이벤트 추가
1. `{BE}/enumeration/AuditActionType.java`(필요하면 `AuditTargetType`·`AuditReasonCode`)에 값 추가. VARCHAR라 DDL 불필요(50·40자 이내).
2. 커밋된 변경 = 그 서비스 `@Transactional` 안 `recordInCurrentTx`, 실패·충돌·반출 = `recordRequiresNew`(반출은 성공 후 산출물 반환). 서비스 계층이면 `AuditRequestContextResolver.resolve(actor)`.
3. metadata record → `{BE}/service/AuditMetadata.java` permits → `{BT}/service/AuditMetadataContractTest.java` `EXPECTED_COMPONENTS`. 지원자 PII·원본 파일명 금지.
4. 그 도메인 테스트 + 아래 검증, "감사 호출 규약" 표 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서, 테스트 설정에 감사 HMAC 값이 있어 `AUDIT_HMAC_SECRET` 불필요):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --tests "com.shinyoung.recruit.service.ActivityLogServiceTest" --tests "com.shinyoung.recruit.service.Audit*" --tests "com.shinyoung.recruit.domain.repository.ActivityLogRepositoryTest" --tests "com.shinyoung.recruit.common.hash.AuditHmacTest" --tests "com.shinyoung.recruit.config.AuditConfigTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.AdminAuditControllerTest" --tests "com.shinyoung.recruit.service.ActivityLogServiceTest" --tests "com.shinyoung.recruit.service.Audit*" --tests "com.shinyoung.recruit.domain.repository.ActivityLogRepositoryTest" --tests "com.shinyoung.recruit.common.hash.AuditHmacTest" --tests "com.shinyoung.recruit.config.AuditConfigTest" --no-daemon
```

`ActivityLogService`·`AuditMetadata` 변경 시 호출부 테스트 `StageAuditInstrumentationTest`([stage-result](stage-result.md)), `ExportAuditLoggerTest`([admin-application](admin-application.md))도 돌린다. [privacy-audit](privacy-audit.md)의 `PURGE_SCAN`·`PURGE_EXECUTE`·`PURGE_RECONCILE`·`PURGE_FORCED`·`RETENTION_*` 감사 호출도 영향을 받으므로 그 카드의 검증도 함께 돌린다.

프론트(`recruit_front/`에서): `npm run type-check`.

## 함정·결정

- 소유 컨트롤러 fix 커밋 없음(`c3d93ab` 통합 이전은 아카이브).
- **`ROLE_PRIVACY_ADMIN`만 가진 계정은 감사 로그 화면에 도달하지 못한다.** `/admin` 라우트 가드 `ADMIN_ROLES`(`{FE}/routes/adminRoutes.ts` — `['ROLE_ADMIN','ROLE_RECRUIT_ADMIN']`)에 막힌다. 정작 ip·ua 원문을 볼 수 있는 권한이 화면을 못 본다. 미해결 — `ADMIN_ROLES`는 로그인 직후 이동 분기([auth-account](auth-account.md))와 공유하는 값이라 바꾸면 영향 범위가 넓다.
- 반대로 `ROLE_ADMIN`만 가진 계정은 라우트는 통과하지만 두 조회 API가 403이다. 조회를 쏘면 공통 인터셉터가 `/403`으로 튕기므로, 화면은 탭 본문을 렌더링하지 않고 안내만 띄운다.
- `recruit_back/recruit_backend/docs/adr/0006-audit-transaction-policy.md` — 커밋변경 in-tx / 실패계열 REQUIRES_NEW / 반출 fail-close. 감사 장애가 업무를 막는 것은 의도된 것, afterCommit 전환은 ADR 재검토 필요.
- `InvalidActivityLogException`은 핸들러가 없어 500(ApiResponse 형식 아님).
- `AUDIT_HMAC_SECRET`를 바꾸면 이후 해시가 이전 값과 연결되지 않는다. `ActivityLog`는 파기되지 않는다.
- 미사용 슬롯: `ActorType.APPLICANT`, `AuditActionResult.DENIED`·`SKIPPED`, `AuditReasonCode.AUTH_DENIED`·`BINARY_DELETE_FAILED`, `AuditTargetType.JOB_APPLICATION`. `ActorType.SYSTEM`(자동 파기 스케줄 actor, [privacy-audit](privacy-audit.md))은 이제 쓰인다.
- 수동 DDL(ddl-auto validate/none 운영 DB): `recruit_back/recruit_backend/docs/ops/phase-09a-activity-log-ddl.sql`(`activity_log`). `activity_log`는 앱 계정에 INSERT·SELECT만, DELETE 권한 금지. **엔티티와 이 DDL의 불일치는 테스트로 검증되지 않는다**(테스트는 `ddl-auto: create-drop`으로 스키마를 매번 재생성해 이 파일을 실행하지 않는다). 운영 반영 전 사람이 대조해야 한다.
