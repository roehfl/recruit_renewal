# Phase 09a-RF — ActivityLog Foundation 리뷰 보완 (Review Fix)

> Phase 09a(`phase-09a-activity-log-foundation.md`) 구현 결과에 대한 코드 리뷰 5개 지적(Major 1, Medium 2, Low 2)을 반영한 보완 슬라이스. 9b(로그 흡수 + egress fail-close + read API) 착수 전 선행 조건.
> 리뷰 원문: 루트 `instruction.md`.

## 1. Phase 요약

- **Major-1** request-derived 문자열(헤더/UA/IP/사유 등)에 길이 제한·sanitize 가 없어, 외부 입력이 길다는 이유만으로 audit insert 가 실패 → 비즈니스 트랜잭션 rollback(`recordInCurrentTx`)·egress fail-close 차단(9b) 위험 → **filter 엄격화 + 서비스 저장 직전 normalize** 로 해소.
- **Medium-2** 운영(MariaDB) 수동 DDL 스크립트 부재 → `docs/codex/ops/phase-09a-activity-log-ddl.sql` 추가.
- **Medium-3** 9b read API 의 `actionResult` 검색 조건 대비 index 부재 → `(action_result, occurred_at)` 복합 인덱스 추가.
- **Low-4** ADR-0006/0007 status 가 proposed 로 잔존 → 구현 히스토리 기준("9a 착수 시 accepted")대로 둘 다 **accepted** 전환.
- **Low-5** fallback HMAC secret 허용이 prod **profile 이름** 의존(prd/real 등이면 조용히 fallback) → **property 기반 게이트** `audit.allow-fallback-secret`(기본 false)로 전환, prod profile 가드는 이중 안전장치로 유지.

## 2. 구현 범위 (Implemented scope)

- `CorrelationIdFilter`: `X-Request-Id` 가 100자 초과 또는 CR/LF 포함이면 재사용하지 않고 UUID 로 대체.
- `ActivityLogService`: 저장 직전 `safe(value, max)` normalize — CR/LF/TAB → 공백, trim, 컬럼 길이 truncate, blank → null. 대상: `actorId`(100), `actorRoleSnapshot`(255), `reasonMessage`(1000), `correlationId`(100), `ipAddress`(64), `userAgent`(512).
- `ActivityLog`: `idx_activity_log_action_result_occurred (action_result, occurred_at)` 복합 인덱스 추가(총 7종).
- `AuditConfig`: secret 누락 시 기본 기동 실패. fallback 은 `audit.allow-fallback-secret=true`(env `AUDIT_ALLOW_FALLBACK_SECRET`) 명시 시에만 허용 + prod profile 이면 flag 무관 거부.
- 운영 DDL: `docs/codex/ops/phase-09a-activity-log-ddl.sql`(테이블 + 인덱스 7종).
- ADR-0006/0007 → accepted.

**범위 밖**: 9b 본 작업(기존 로거 흡수, egress fail-close, 관리자 변경 계측, audit read API), `targetId` normalize(내부 파생 값 — 호출부가 entity id 문자열만 넣는다), `traceId`(항상 null, deferred).

## 3. 변경 파일 (Changed files)

수정(main):
- `config/CorrelationIdFilter.java` — 엄격 resolve(길이/CRLF 검증), `MAX_CORRELATION_ID` 상수
- `service/ActivityLogService.java` — 길이 상수 6종 + `safe()` normalize 적용
- `domain/entity/ActivityLog.java` — `(action_result, occurred_at)` 복합 인덱스
- `config/AuditConfig.java` — property 기반 fallback 게이트 + prod 이중 가드
- `src/main/resources/application.yaml` — `audit.allow-fallback-secret: ${AUDIT_ALLOW_FALLBACK_SECRET:false}`

수정(test):
- `config/CorrelationIdFilterTest.java` — +3 테스트(길이 초과/경계 100자/CRLF)
- `config/AuditConfigTest.java` — 3 → 5 테스트(allow-fallback true/기본값 실패/prod 실패/prod+flag 실패)
- `service/ActivityLogServiceTest.java` — +4 테스트(UA truncate/reasonMessage sanitize/전 필드 truncate/blank→null)

신규(docs):
- `docs/codex/ops/phase-09a-activity-log-ddl.sql`
- `docs/codex/implementation/phase-09a-review-fix.md` (본 문서)
- `docs/codex/reports/phase-09a-review-fix.html`

수정(docs):
- `docs/adr/0006-audit-transaction-policy.md` — Status: accepted
- `docs/adr/0007-privacy-admin-role-separation.md` — Status: accepted
- `docs/codex/implementation/phase-09a-activity-log-foundation.md` — stale 기술(인덱스 6종, AuditConfig fallback 정책) 보정
- `docs/codex/07-implementation-history.md` — 본 보완 이력 추가

## 4. 신규 클래스 (New classes)

- 없음(기존 클래스 보완만).

## 5. 수정 클래스 (Modified classes)

`CorrelationIdFilter`, `ActivityLogService`, `ActivityLog`, `AuditConfig` + 대응 테스트 3종.

## 6. 클래스별 설명 (Class-by-class)

### `config.CorrelationIdFilter` — Filter (수정)
- 책임: 요청 단위 correlationId 전파. **외부 헤더 불신** 추가.
- 주요 변경: `resolve()` — null/blank → UUID(기존), `trim` 후 **100자 초과 또는 `\r`/`\n` 포함 → UUID 대체**(신규). `MAX_CORRELATION_ID = 100`(= `activity_log.correlation_id` 컬럼 길이, package-private — 테스트 참조).
- 관련 클래스: `ActivityLogService`(MDC 소비), `ActivityLog`.
- 노트: 응답 echo 하는 값이므로 CRLF 거부는 response header injection 방어도 겸한다.

### `service.ActivityLogService` — Service (수정)
- 책임: 감사 기록 2경로(불변). **저장 직전 request-derived 문자열 normalize** 추가.
- 주요 변경: 길이 상수 6종(`MAX_ACTOR_ID`=100, `MAX_ROLE_SNAPSHOT`=255, `MAX_REASON_MESSAGE`=1000, `MAX_CORRELATION_ID`=100, `MAX_IP_ADDRESS`=64, `MAX_USER_AGENT`=512 — 엔티티 `@Column(length)` 와 동일 유지 필수) + `safe(String, int)`: `[\r\n\t]`→공백, trim, 초과 truncate, blank→null. `toEntity()` 에서 actorId/actorRoleSnapshot/reasonMessage/correlationId(MDC resolve 후)/ipAddress/userAgent 에 적용.
- 관련 클래스: `ActivityLog`, `AuditEvent`, `CorrelationIdFilter`.
- 노트: 의미 보존 위해 reject 가 아니라 truncate — 감사 기록은 잘려도 남는 것이 우선(fail-open 기록, fail-close 는 egress 쪽 9b 정책).

### `domain.entity.ActivityLog` — Entity (수정)
- 주요 변경: `@Index(name = "idx_activity_log_action_result_occurred", columnList = "action_result,occurred_at")` 추가(총 7종). 9b read API 가 `actionResult=FAILURE/DENIED/CONFLICT` + 기간 조건을 함께 검색하는 패턴 대비. 단독 `action_result` 인덱스 대신 복합 채택(리뷰 권고).

### `config.AuditConfig` — Config (수정)
- 책임: `AuditHmac` 빈 생성. fail-safe 정책을 profile 이름 의존에서 property 기반으로 전환.
- 주요 변경: `@Value("${audit.allow-fallback-secret:false}") boolean allowFallbackSecret` 추가. secret blank 시: `prod profile ∨ ¬allowFallbackSecret → IllegalStateException`(기동 실패). fallback 은 flag 명시 + 비prod 에서만(경고 로그).
- 노트: 운영 profile 이름이 `prd`/`real`/`production` 이어도 secret 누락이 조용히 fallback 으로 넘어가지 않는다(기본 fail-closed). prod profile 가드는 flag 오설정 대비 이중 안전장치.

### Test — `CorrelationIdFilterTest` (수정, 3→6)
- 추가: 길이 100 초과 헤더 → UUID 대체(+응답 echo 일치), 길이 100 경계 헤더 → 재사용, CRLF 포함 헤더 → UUID 대체.

### Test — `AuditConfigTest` (수정, 3→5)
- 변경: 기존 "비운영 fallback 빈 생성" → "allow-fallback=true 일 때만 fallback".
- 추가: 기본값(미설정)이면 기동 실패, prod + allow-fallback=true 도 기동 실패.

### Test — `ActivityLogServiceTest` (수정, 6→10)
- 추가: 2000자 UA → 512 truncate, CRLF/TAB reasonMessage → sanitize, actorId/roleSnapshot/correlationId/ip/reasonMessage 동시 초과 → 전부 컬럼 길이로 truncate(insert 성공), whitespace-only → null 저장.

## 7. API 목록

- **없음**(9a 와 동일 — foundation 보완).

## 8. Entity 관계 요약

- 변동 없음. `ActivityLog` 독립 테이블 유지, 인덱스만 6→7종.

## 9. 비즈니스 규칙 (추가/변경)

1. **외부 헤더 불신**: `X-Request-Id` 는 100자 초과·CRLF 포함 시 재사용하지 않는다(UUID 대체).
2. **truncate-not-fail**: request-derived 문자열은 컬럼 길이로 truncate 해 기록한다 — 외부 입력 길이가 audit insert 실패 → 비즈니스 rollback/egress 차단으로 전이되지 않게 한다.
3. **normalize 규칙**: CR/LF/TAB → 공백, trim, blank → null. 컬럼 길이 변경 시 `ActivityLogService` 상수를 함께 변경한다.
4. **fallback secret 게이트**: `audit.allow-fallback-secret=true` 명시 시에만 fallback 허용(기본 false = 기동 실패). prod profile 은 flag 무관 거부.
5. ADR-0006(감사 트랜잭션 3-way)/ADR-0007(ROLE_PRIVACY_ADMIN 분리) 는 **accepted** — 이후 변경은 ADR 재검토 선행.

## 10. 테스트 커버리지 (Test coverage)

- scoped: `$env:AES_SECRET_KEY='...'; .\gradlew.bat test --tests "*ActivityLog*" --tests "*AuditHmacTest" --tests "*AuditConfigTest" --tests "*CorrelationIdFilterTest" --no-daemon`
  - 결과: **BUILD SUCCESSFUL** (28 tests: AuditHmac 5, CorrelationIdFilter 6, AuditConfig 5, ActivityLogRepository 2, ActivityLogService 10).
- 전체 회귀: **미실행** — 사용자 지시로 scoped 테스트만 실행(전체 스위트 실행 제외). 변경 영역(audit/correlation/config) 관련 테스트 28건은 전부 통과. 적대적 검증 워크플로(6 agents)가 변경/추가된 모든 테스트 클래스의 통과를 별도 재확인함.

## 11. 알려진 한계 (Known limitations)

- truncate 는 정보 손실이다(의도된 트레이드오프). 원문 보존이 필요한 사례가 생기면 metadataJson allowlist 로 옮기는 것을 9b 에서 검토.
- `safe()` 는 CR/LF/TAB 외 제어문자(예: `\0`, `\b`)는 치환하지 않는다 — DB 저장에는 문제없고, 표시 계층(9b read API) 이슈로 이연.
- local 개발에서 `AUDIT_HMAC_SECRET` 미설정 시 이제 **기동이 실패**한다 — `AUDIT_ALLOW_FALLBACK_SECRET=true` 를 명시해야 한다(기존 AES_SECRET_KEY 필수와 동일한 운영 방식). 테스트 yaml 은 secret 이 이미 설정돼 있어 영향 없음.

## 12. 다음 슬라이스 고려사항 (Next)

- 9b 진행 가능. egress fail-close 가 이 normalize 를 전제로 한다(긴 UA/헤더로 export 가 막히지 않음).
- 9b read API 의 `actionResult`+기간 검색은 `idx_activity_log_action_result_occurred` 를 타도록 쿼리 작성.
- 운영 배포 체크리스트: `AUDIT_HMAC_SECRET` 주입 + `phase-09a-activity-log-ddl.sql` 반영(ddl-auto validate/none 환경).
