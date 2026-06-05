# Phase 05y — Applicant Account Hardening (결정-독립 슬라이스) 설계

- Date: 2026-06-05
- Work type: design (**2026-06-05 구현 완료** — `docs/codex/implementation/phase-05y-applicant-account-hardening.md` 참조)
- 선행 관계: Phase 05x(지원자 회원가입) 후속, **Phase 09b 착수 전 선행 슬라이스**
- 관련 문서: `docs/codex/implementation/phase-05x-applicant-sign-up.md`, `docs/codex/implementation/fix-auth-status-codes-401-403.md`

> 2026-06-05 리뷰 1차 반영(instruction.md, Major 3 + Medium 3): ① loginId 정규화 정책 명시(05y는 trim only, 대소문자 semantics는 collation 의존 제거 후속 결정), ② LDAP JIT 동시 생성 race **복구** 채택(선택 B — unique 차단에서 끝내지 않고 재조회 후 정상 로그인 복구), ③ 전화번호 변경에 currentPassword 재확인 채택(권장안), ④ check-email은 "email 입력값이 있을 때만 호출하는 advisory API"로 명확화, ⑤ 운영 DDL 사전 점검 3종 보강, ⑥ JIT race/복구 단위 테스트 추가.
>
> 2026-06-05 리뷰 2차 반영(instruction.md, Major 1 + Medium 2 + Low 1): ① JIT race 복구 시 `processLdap()` 재호출 금지 — LDAP 재인증 없이 기존 `ldapUser`로 `buildEmployeeAuthentication()` helper 토큰 생성(테스트에 authenticate 1회 호출 검증 추가), ② collation 점검을 `SHOW INDEX`에서 `INFORMATION_SCHEMA.COLUMNS`(또는 `SHOW FULL COLUMNS`)로 교체(SHOW INDEX의 Collation은 인덱스 정렬 방향), ③ 복구 범위 한정 — `Employee.deptName` unique 등 loginId race가 아닌 제약 위반은 복구하지 않고 예외 전파("한 요청도 실패 없이" 표현 정정), ④ 07-history 상단 요약의 전화번호 변경 항목에 currentPassword 재확인 반영.

## 1. 목적

지원자 loginId 정책(이메일을 loginId로 쓸지, 별도 ID를 입력받을지)이 **미결정**인 상태에서, 어느 안이 채택되어도 그대로 유효한 계정 기능만 골라 먼저 구현한다. 동시에 현재 코드에 실재하는 loginId 무결성 결함을 수정한다.

## 2. 배경 — 결정-독립성 분석

### 2.1 미결정 사항

지원자 회원가입 시 loginId 입력 방식이 결정되지 않았다.

- 안 1: 이메일 = loginId (가입 시 `loginId = 정규화된 email`)
- 안 2: 별도 ID 입력 (현재 구조 그대로)

두 안 모두 내부 인증 계약은 동일하다 — 로그인 API(`loginId + password`), `CustomUserDetailsService`/`RoutingAuthenticationProvider`의 `findUserByLoginId` 조회, 세션 principal 전부 `loginId` 기반이며 결정과 무관하다.

### 2.2 결정-독립 vs 결정-의존 작업 분류

| 작업 | 분류 | 근거 |
| --- | --- | --- |
| `User.loginId` DB 유니크 제약 | **독립 → 본 슬라이스** | 두 안 모두 loginId는 users 테이블 전체에서 유일해야 함 |
| signUp loginId 중복체크를 User 레벨로 교정 | **독립 → 본 슬라이스** | 현재 Applicant 레벨만 체크하는 실제 결함(§3.1) |
| email 중복체크 API | **독립 → 본 슬라이스** | email은 두 안 모두 유니크(`Applicant.email unique` + signUp 체크 기존재) |
| 비밀번호 변경 API | **독립 → 본 슬라이스** | 자격증명 변경, loginId 정책과 무관 |
| 전화번호 변경 API | **독립 → 본 슬라이스** | 연락처 변경, loginId 정책과 무관 |
| check-login-id API | 의존 → 보류 | 안 2에서만 존재(안 1에서는 check-email이 동일 역할) |
| 가입 request email `@NotBlank` 전환 | 의존 → 보류 | 안 1에서 email 필수, 안 2에서 optional — 실제 분기점 |
| 이메일 변경 API | 의존 → 보류 | 안 1에서는 이메일 변경 = 로그인 자격증명 변경(세션/재인증 설계 필요) |
| 아이디 찾기 플로우 | 의존 → 보류 | 안 2에서만 필요(CI 본인인증 기반) |

결정이 내려진 후의 추가 비용: 안 1 채택 시 `signUp()`에서 loginId=email 대입 + request 검증 변경, 안 2 채택 시 check-login-id API 1개 추가. 어느 쪽이든 본 슬라이스 산출물은 변경 없이 유효하다.

## 3. 현재 코드의 결함 (본 슬라이스에서 수정)

### 3.1 loginId 중복체크 범위 결함 — Applicant 레벨 vs User 레벨

- 로그인 해석은 `UserRepository.findUserByLoginId()`로 **users 테이블 전체**에서 일어난다(`CustomUserDetailsService:25`, `RoutingAuthenticationProvider:38`).
- 그러나 `ApplicantSignUpService:32`의 중복체크는 `ApplicantRepository.existsByLoginId()` — **Applicant 서브타입만** 본다.
- 따라서 지원자가 기존 임직원(LDAP JIT 생성)의 loginId와 동일한 값으로 가입하면 체크를 통과하고, 이후 `findUserByLoginId`가 2건을 매칭해 `IncorrectResultSizeDataAccessException`(원인: `jakarta.persistence.NonUniqueResultException`) — **해당 loginId의 지원자/임직원 모두 로그인 영구 장애**.

### 3.2 `User.loginId` DB 유니크 제약 부재

- `Applicant.email`(unique), `Applicant.ciHash`(unique)와 달리 `User.loginId`에는 아무 제약이 없다.
- 서비스 레벨 체크만으로는 동시 요청 race를 막지 못한다.
- 부수 위험: 동일 임직원의 최초 로그인이 동시에 2건 들어오면 JIT 경로(`RoutingAuthenticationProvider.processLdapAndJit`)가 중복 Employee row를 만들 수 있고, 이 역시 3.1과 같은 영구 로그인 장애로 이어진다. 유니크 제약은 이 경로의 backstop도 겸한다.

### 3.3 DB 제약 위반 시 응답 포맷 결함

- `GlobalExceptionHandler`에 `DataIntegrityViolationException` 핸들러가 없어, race에서 DB 제약에 걸리면 500 + Spring 기본 에러 포맷(ApiResponse 아님)으로 응답한다.
- 예외: `SchoolService`/`CommonCodeService`는 `DataIntegrityViolationException`을 서비스 내부에서 로컬 catch하여 도메인 예외로 변환하고 있어 이 경로는 해당 없음. 본 슬라이스가 다루는 unique 경로(loginId/email/ciHash)에는 로컬 catch가 없어 500으로 샌다.

## 4. 구현 범위 (Scope)

### Scope A — loginId 무결성 보강

1. `User.loginId`에 `@Column(unique = true)` 추가.
   - `nullable = false`는 **이번에 추가하지 않는다** — 기존 테스트 픽스처 영향 범위가 넓어질 수 있고, H2/MariaDB 모두 NULL은 유니크 제약에 충돌하지 않으므로 무결성 목표(중복 차단)는 unique만으로 달성된다. nullable 강화는 후속.
2. `UserRepository`에 `boolean existsByLoginId(String loginId)` 추가.
3. `ApplicantSignUpService.signUp()`의 loginId 중복체크를 `userRepository.existsByLoginId()`로 교체(User 전체 범위). 실패 메시지 불변: `"이미 사용 중인 아이디입니다."`
4. `ApplicantRepository.existsByLoginId` 제거(유일 사용처가 3번으로 이동 — 구현 시 사용처 재확인 후 제거).
5. **loginId 정규화 정책(리뷰 Major 1)**:
   - 05y에서 loginId는 **trim only**로 유지한다(가입 경로 — 현행과 동일). 로그인/JIT 경로는 입력값 그대로 사용(현행 유지).
   - **대소문자 구분 여부는 DB collation에 의존하지 않도록 후속 phase에서 명시 결정한다.** MariaDB collation이 case-insensitive(예: `utf8mb4_general_ci`)면 `user01`/`USER01`이 유니크 충돌로 묶이고, H2 테스트(case-sensitive)와 운영 동작이 달라질 수 있다.
   - 테스트는 H2 unique 동작만 검증하고, 운영 MariaDB collation 차이는 **DDL 적용 전 점검 항목**으로 남긴다(아래 6번 점검 쿼리).
   - `normalizeLoginId()`를 만들어 가입/로그인/JIT 저장 경로에 동일 적용하는 안은 LDAP `sAMAccountName` 대소문자 정책에 영향을 줄 수 있어 **05y에서는 보류**한다(후속 결정과 함께).
6. 운영(MariaDB) 수동 DDL — 적용 전 사전 점검 포함:

```sql
-- 1) 중복 loginId 점검 (0건이어야 적용 가능)
SELECT login_id, COUNT(*)
FROM users
WHERE login_id IS NOT NULL
GROUP BY login_id
HAVING COUNT(*) > 1;

-- 2) null / blank loginId 현황 파악
SELECT COUNT(*)
FROM users
WHERE login_id IS NULL OR TRIM(login_id) = '';

-- 3) 기존 인덱스/제약명 충돌 확인
SHOW INDEX FROM users;

-- 4) login_id 컬럼 collation(case-sensitivity) 확인
--    주의: SHOW INDEX의 Collation 컬럼은 문자열 collation이 아니라
--    인덱스 정렬 방향(A/D)이므로 collation 확인에 쓰지 않는다(2차 리뷰 Medium 1).
SELECT COLUMN_NAME, COLLATION_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'login_id';
-- 또는: SHOW FULL COLUMNS FROM users LIKE 'login_id';

-- 5) 적용
ALTER TABLE users ADD CONSTRAINT uk_users_login_id UNIQUE (login_id);
```

> migration framework가 없으므로 H2(ddl-auto)는 엔티티 선언으로 반영되고, 운영 DB는 위 DDL을 별도 적용한다. 점검 4)에서 컬럼 collation이 case-insensitive(예: `*_ci`)로 확인되면 5번 정책(후속 명시 결정) 전까지 대소문자만 다른 loginId가 유니크 충돌로 묶이는 동작 차이를 인지하고 적용한다.

7. **LDAP JIT 동시 생성 race 복구(리뷰 Major 2 — 선택 B 채택, 2차 리뷰 Major 1 반영)**:
   - 현재 JIT 경로는 `findUserByLoginId()` 부재 확인 → LDAP 인증 → 즉시 `employeeRepository.save()`라서, 동일 임직원의 동시 최초 로그인 2건이면 unique 제약 도입 후 한쪽이 `DataIntegrityViolationException`으로 실패한다. unique 제약은 "영구 중복 데이터 방지"(차단)일 뿐 "정상 로그인 보장"(복구)이 아니다.
   - 채택: `RoutingAuthenticationProvider.processLdapAndJit()`에서 Employee save 중 `DataIntegrityViolationException` 발생 시 `userRepository.findUserByLoginId(loginId)`를 **재조회**하고:
     - 결과가 `Employee`면 → 이미 인증 성공한 `ldapUser`로 Authentication을 직접 구성해 정상 로그인 복구. **`processLdap()`를 재호출하지 않는다** — `processLdap()`는 내부에서 `ldapProvider.authenticate()`를 다시 수행하므로, 그대로 부르면 LDAP 인증이 2회 일어난다(2차 리뷰 Major 1). DB 저장만 실패한 상태이므로 재인증 없이 아래 helper로 토큰만 만든다:

       ```java
       private Authentication buildEmployeeAuthentication(User user, CustomUserDetails ldapUser) {
           CustomUserDetails finalUser = CustomUserDetails.fromUser(user, ldapUser.getAuthorities());
           return new UsernamePasswordAuthenticationToken(finalUser, null, finalUser.getAuthorities());
       }
       ```

       `processLdapAndJit()`의 catch 블록에서 재조회 결과가 `Employee`면 이 helper를 호출한다. (기존 `processLdap()`의 토큰 생성부도 동일 helper로 추출해 재사용 가능 — 구현 시 선택.)
     - 결과가 `Employee`가 아니거나(이론상 race에서 동일 loginId 지원자 가입이 선점한 경우) 부재면 → 예외를 전파해 인증 실패 처리.
   - **복구 범위의 한정(2차 리뷰 Medium 2)**: `Employee.deptName`에도 `@Column(unique = true)`가 걸려 있어, JIT save 중의 `DataIntegrityViolationException`이 loginId race가 아니라 **deptName unique 충돌**일 수도 있다. 동시 JIT **loginId race인 경우에는** 재조회가 Employee를 찾으므로 양쪽 요청 모두 로그인 성공으로 복구된다. 단, loginId race가 아닌 다른 DB 제약 위반(deptName unique 등)은 재조회가 부재(또는 비-Employee)로 떨어져 복구하지 않고 예외를 전파한다 — 이것이 정상 동작이다.

### Scope B — 이메일 중복체크 API (가입 화면 지원)

| Method | Path | Auth |
| --- | --- | --- |
| GET | `/auth/applicants/check-email?email=...` | permitAll |

- 위치: 기존 `ApplicantSignUpController`(가입 전 단계 API 계열이므로 동거).
- 파라미터 검증: 컨트롤러 클래스에 `@Validated` + `@RequestParam @NotBlank @Email @Size(max = 255)`. 위반 시 기존 `ConstraintViolationException` 핸들러가 400으로 응답(컨트롤러 파라미터 검증에 `@Validated`를 쓰는 첫 사례 — config properties 외 최초).
- 정규화: `signUp()`의 `normalizeEmail`(trim)과 **동일한 정규화**를 거친 뒤 `existsByEmail` 판정. 정규화 불일치는 가용 판정 오류를 만들므로 같은 private 메서드를 재사용한다.
- 응답: `ApiResponse<ApplicantEmailAvailabilityResponse>` — `{ "available": true | false }`. 가입 여부 외 어떤 정보도 노출하지 않는다.
- 의미: **email 입력값이 있을 때만 호출하는 advisory(UX 사전 안내) 전용 API**(리뷰 Medium 반영). 현재 가입 정책에서 email은 optional(`@NotBlank` 없음)이므로, 프론트가 이 API를 "가입 전 필수 검증"처럼 사용하면 가입 정책과 충돌한다 — 지원자가 email을 입력한 경우에만 호출한다. email 필수 전환은 결정-의존 보류 항목(§2.2). 최종 권위는 기존대로 signUp 시점 재검증 + `Applicant.email` DB unique. race로 사전 체크와 결과가 달라질 수 있음은 정상 동작.
- SecurityConfig: `"/api/auth/applicants/check-email"`을 permitAll 목록에 **명시 추가**. 현재 `anyRequest().permitAll()` fall-through로도 접근 가능하지만, `fix-auth-status-codes-401-403.md`에 기록된 "permitAll → authenticated + allowlist 전환" 미해결 과제가 실행될 때 깨지지 않도록 지금 명시한다(05x의 sign-up 명시 등록과 동일 관례).

### Scope C — 비밀번호 변경 API

| Method | Path | Auth |
| --- | --- | --- |
| POST | `/applicant/account/password` | ROLE_APPLICANT |

- Request: `ApplicantPasswordChangeRequest(currentPassword, newPassword)` record.
  - `currentPassword`: `@NotBlank`
  - `newPassword`: `@NotBlank @Size(min = 8, max = 100)` — 05x 가입 정책과 동일.
- 규칙:
  1. `passwordEncoder.matches(currentPassword, 저장값)` 불일치 → 400 `"현재 비밀번호가 일치하지 않습니다."`
  2. `passwordEncoder.matches(newPassword, 저장값)` 일치(현재와 동일한 새 비밀번호) → 400 `"새 비밀번호가 현재 비밀번호와 달라야 합니다."`
  3. 통과 시 BCrypt 인코딩 후 저장.
- 응답: `ApiResponse<Void>` 성공.
- 세션: 본인 세션은 유지(재로그인 불요). 다른 기기 세션 무효화는 세션 레지스트리 부재로 범위 외(§7 한계).

### Scope D — 전화번호 변경 API

| Method | Path | Auth |
| --- | --- | --- |
| POST | `/applicant/account/phone-number` | ROLE_APPLICANT |

- Request: `ApplicantPhoneNumberChangeRequest(currentPassword, phoneNumber)` record (리뷰 Major 3 — 권장안 채택).
  - `currentPassword`: `@NotBlank`
  - `phoneNumber`: `@NotBlank @Size(max = 30)` (05x 가입 정책과 동일)
- 규칙:
  1. `passwordEncoder.matches(currentPassword, 저장값)` 불일치 → 400 `"현재 비밀번호가 일치하지 않습니다."` (Scope C와 동일 메시지 — 검증 로직 공유)
  2. 통과 시 trim 후 저장.
- 채택 근거: 전화번호는 향후 메시지 발송(MessageBatch)/본인확인/알림 채널과 연결될 가능성이 높은 개인정보이고, 금융권·채용 시스템 성격상 세션 탈취만으로 통지 채널을 변조할 수 없도록 재확인을 둔다. SMS 재인증/변경 알림은 여전히 후속(§7).
- 전화번호 유니크 제약은 가입과 동일하게 두지 않는다(기존 정책 유지).
- 응답: `ApiResponse<Void>` 성공.

### Scope E — 횡단 보강

1. `GlobalExceptionHandler`에 `DataIntegrityViolationException` 핸들러 추가 → **409 CONFLICT** + generic 메시지 `"이미 처리되었거나 중복된 데이터입니다."`
   - 제약명/컬럼명/SQL 등 내부 정보를 응답 메시지에 노출하지 않는다(`e.getMessage()` 미사용).
   - **관측성 보존**: 응답은 generic으로 고정하되, 핸들러 내부에서 원인 예외를 **warn 레벨 서버 로그**로 기록한다 — 일괄 409 매핑으로 실제 서버측 결함(FK/NOT NULL 위반 등)이 모니터링에서 가려지는 것을 방지.
   - 전역 영향(주의): `@RestControllerAdvice`라 신규 엔드포인트만이 아니라 **기존 전 엔드포인트**의 DB 제약 위반 응답이 500 → 409로 바뀐다. FK/NOT NULL 위반 같은 서버측 결함도 409로 분류되는 trade-off가 있으나 비표준 500 노출보다 낫다고 판단(설계 결정). 구현 시 **회귀 영향 점검 항목**에 포함하고, 기존 409 매핑인 `ObjectOptimisticLockingFailureException`과 메시지 일관성을 확인한다.
   - 검증 확인: 500을 기대하는 기존 제약 위반 테스트는 없음(유일한 `DataIntegrityViolationException` 단언은 `InterviewEvaluationRepositoryTest`의 리포지토리 계층 단언으로 HTTP 미경유). `SchoolService`/`CommonCodeService`의 로컬 catch 경로는 전파 전에 처리되므로 영향 없음.
2. `Applicant` 엔티티에 의미 있는 상태 변경 메서드 추가(CLAUDE.md §5.2 — setter 남발 대신):
   - `changePassword(String encodedPassword)`
   - `changePhoneNumber(String phoneNumber)`
   - 기존 `@Setter`는 제거하지 않는다(05x signUp 경로가 사용 중 — 점진 개선 원칙). 신규 코드는 의미 메서드만 사용.

## 5. Out of Scope (보류 사유 포함)

| 항목 | 사유 |
| --- | --- |
| `check-login-id` API | loginId 정책 결정-의존(§2.2) |
| 이메일 변경 API | 결정-의존 — 안 1에서 자격증명 변경이 됨 |
| 가입 email `@NotBlank` 전환 | 결정-의존 — 실제 분기점 |
| 아이디 찾기 / 비로그인 비밀번호 재설정 | 결정-의존 + NICE 본인인증 의존 |
| email lowercase 정규화 | 기존 저장 데이터와 비교 semantics 변경 — 별도 검토(§7) |
| loginId 대소문자 정규화(`normalizeLoginId()` 가입/로그인/JIT 공통 적용) | LDAP `sAMAccountName` 대소문자 정책 영향 — collation 의존 제거 결정과 함께 후속 phase(§4 Scope A-5) |
| NICE 본인인증 연동, rate limiting, CAPTCHA | 05x Known Limitations 승계 |
| name / ci 변경 API | 본인인증 산출물 — 일반 수정 API로 열지 않음 |
| ActivityLog 계측(비밀번호/연락처 변경 감사) | 9a `AuditActionType` taxonomy가 관리자/파기 행위 중심 + typed `AuditMetadata`가 9b 작업 — 지원자 self-service 행위 유형 추가는 9b 이후 계측 스윕에서 일괄(§7에 hook 명시) |
| `anyRequest().permitAll()` → allowlist 전환 | `fix-auth-status-codes-401-403.md` 미해결 항목 — 별도 슬라이스(9b matcher 작업과 병합 검토) |
| `User.loginId` `nullable = false` | 픽스처 영향 범위 — 후속 |
| 임직원(Employee) 정보 변경 | LDAP이 원천 — 본 시스템에서 수정하지 않음 |

## 6. 클래스 설계

### 6.1 신규 클래스

| Package | Class | Type | 책임 |
| --- | --- | --- | --- |
| `controller` | `ApplicantAccountController` | Controller | `/applicant/account` 하위 비밀번호/전화번호 변경 엔드포인트. `@AuthenticationPrincipal CustomUserDetails` → `CurrentApplicantService.getCurrentApplicantId()`로 본인 식별(401/403 일관 — fix-auth 슬라이스 정합) |
| `service` | `ApplicantAccountService` | Service | `changePassword(applicantId, request)`, `changePhoneNumber(applicantId, request)`. `@Transactional` 변경 메서드. Applicant 조회 실패 시 `InvalidApplicantAccountException` |
| `dto.request` | `ApplicantPasswordChangeRequest` | Request DTO (record) | currentPassword/newPassword + Bean Validation |
| `dto.request` | `ApplicantPhoneNumberChangeRequest` | Request DTO (record) | currentPassword/phoneNumber + Bean Validation |
| `dto.response` | `ApplicantEmailAvailabilityResponse` | Response DTO (record) | `available` boolean 단일 필드 |
| `exception` | `InvalidApplicantAccountException` | Exception | 계정 변경 검증 실패(현재 비밀번호 불일치 등) → 400 |

### 6.2 변경 클래스

| Class | 변경 |
| --- | --- |
| `domain.entity.User` | `loginId`에 `@Column(unique = true)` |
| `domain.entity.Applicant` | `changePassword`/`changePhoneNumber` 의미 메서드 추가 |
| `domain.repository.UserRepository` | `existsByLoginId` 추가 |
| `domain.repository.ApplicantRepository` | `existsByLoginId` 제거 |
| `service.ApplicantSignUpService` | loginId 중복체크를 `UserRepository`로 교체, `checkEmailAvailability(String email)` 추가(normalizeEmail 재사용) |
| `controller.ApplicantSignUpController` | `@Validated` + `GET /check-email` 추가 |
| `exception.GlobalExceptionHandler` | `InvalidApplicantAccountException` → 400, `DataIntegrityViolationException` → 409 핸들러 추가 |
| `config.SecurityConfig` | permitAll 목록에 `"/api/auth/applicants/check-email"` 명시 추가 |
| `security.auth.RoutingAuthenticationProvider` | `processLdapAndJit()` save 시 `DataIntegrityViolationException` catch → `findUserByLoginId` 재조회 → Employee면 `buildEmployeeAuthentication(user, ldapUser)` helper로 복구(**LDAP 재인증 없음**), 아니면 예외 전파(§4 Scope A-7) |

### 6.3 인가 경로 분석 (SecurityConfig 변경 최소화 근거)

- `/applicant/account/**`는 기존 matcher `requestMatchers("/api/applicant/**").hasAuthority("ROLE_APPLICANT")`(SecurityConfig:86)에 **이미 포함**된다 — `/auth/applicants/me/**` 같은 신규 네임스페이스를 만들면 `anyRequest().permitAll()` fall-through 때문에 matcher 누락 시 **무인증 공개**되는 위험이 있으나, 기존 보호 네임스페이스를 쓰면 이 위험이 구조적으로 제거된다.
- 심층 방어: matcher 외에 `CurrentApplicantService`가 미인증 401(`AuthenticationRequiredException`) / 비지원자 403(`AccessForbiddenException`)을 한 번 더 보장한다.

## 7. 한계 / 후속 과제

1. check-email은 계정 존재 여부를 노출하는 **enumeration 벡터** — rate limiting/CAPTCHA 미적용(05x 한계 승계). 운영 전 보호장치 검토 필요. signUp 본검증의 실패 메시지("이미 사용 중인 이메일입니다." 등)도 동일한 enumeration 표면을 이미 갖고 있으므로 check-email이 새로 여는 표면은 아니다.
2. email 비교는 대소문자 구분(trim만 수행) — lowercase 정규화 도입 시 기존 데이터 마이그레이션과 함께 별도 검토.
3. 비밀번호 변경 시 타 세션 무효화 없음(세션 레지스트리 부재).
4. 비밀번호 변경의 `currentPassword` 검증에 **시도 횟수 제한 없음** — 세션 탈취 상태에서의 비밀번호 brute-force가 이론상 가능. rate limiting 도입 시(1번과 함께) 포함 검토.
5. CSRF disabled는 단일 filter chain 전 엔드포인트 공통의 기존 결정이며 본 슬라이스가 새 위험을 추가하지 않으나, 비밀번호 변경은 영향도가 높은 엔드포인트이므로 CSRF 정책 재검토 시 우선 대상.
6. 전화번호 변경은 currentPassword 재확인을 채택(리뷰 반영)했으나 **SMS 재인증/변경 알림은 없음** — SMS 인증/계정 복구 수단으로 phoneNumber를 사용하기 전에는 반드시 재인증 또는 변경 알림을 도입한다(MessageBatch 실발송 단계 전 후속 과제).
7. loginId 대소문자 비교 semantics가 **DB collation 의존** 상태 — 05y는 trim only 유지, collation 의존 제거(명시 정규화 정책)는 후속 phase에서 결정(§4 Scope A-5).
8. 비밀번호/전화번호 변경의 ActivityLog 계측 보류 — 계측 시 `AuditActionType`에 지원자 self-service 유형(예: `APPLICANT_PASSWORD_CHANGE`) 추가 + `ApplicantAccountService` 변경 메서드가 hook 지점.
9. `User.loginId`는 여전히 nullable — 후속 강화 대상.
10. 비밀번호 강도 정책은 길이(8~100)만 — 05x 한계 승계.

## 8. 테스트 계획

### 8.1 보강 — `ApplicantSignUpServiceTest`

- 기존 loginId 모킹을 `UserRepository.existsByLoginId`로 전환.
- 추가: 임직원이 점유한 loginId로 가입 시 실패(User 레벨 체크 검증).
- 추가: `checkEmailAvailability` — 가용 true / 점유 false / 공백 trim 정규화 후 판정.

### 8.2 보강 — `ApplicantSignUpControllerTest`

- `GET /auth/applicants/check-email` — 200 available true / 200 available false / 400 이메일 형식 오류 / 400 blank.

### 8.3 신규 — `ApplicantAccountServiceTest`

- 비밀번호 변경 성공(인코딩 저장 검증) / 현재 비밀번호 불일치 400 / 새 비밀번호가 현재와 동일 400.
- 전화번호 변경 성공 / trim 검증 / **currentPassword 불일치 400**(리뷰 반영).

### 8.4 신규 — `ApplicantAccountControllerTest`

- 미인증 → 401 (`/api/applicant/**` matcher).
- 지원자 인증 → 비밀번호 변경 200 / 전화번호 변경 200.
- validation 위반 → 400.
- (가능 시) 임직원 인증 → 403.

### 8.5 신규 — `RoutingAuthenticationProvider` JIT race 복구 단위 테스트 (리뷰 Medium 반영)

- `employeeRepository.save()`가 `DataIntegrityViolationException`을 던질 때:
  - 재조회 결과가 Employee → `buildEmployeeAuthentication` 경로로 정상 Authentication 반환(복구). 이때 **`ldapProvider.authenticate()`가 정확히 1회만 호출됐는지 검증**한다(`verify(ldapProvider, times(1)).authenticate(...)`) — 복구 경로에서 LDAP 재인증이 일어나지 않음을 보장(2차 리뷰 Major 1).
  - 재조회 결과 부재 또는 Employee 아님 → 예외 전파(인증 실패).
- LDAP provider는 mock — 실제 LDAP 연결 금지(CLAUDE.md §6).

### 8.6 선택 — `UserRepository` `@DataJpaTest`

- 동일 loginId 2건 insert 시 `DataIntegrityViolationException`(H2 유니크 제약 동작 확인 — H2는 case-sensitive이므로 대소문자 차이 케이스는 검증 범위 아님, §4 Scope A-5).
- null loginId 2건 insert 허용 확인(NULL은 제약 비충돌).

### 8.7 실행 명령 (scoped)

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicantSignUp*" --tests "*ApplicantAccount*" --tests "*RoutingAuthenticationProvider*" --no-daemon
```

전체 회귀는 사용자 명시 요청 시에만 실행한다. `User` 엔티티 변경(unique)이 있으므로 구현 시 기존 픽스처 중 **중복 loginId를 명시 사용하는 테스트**가 있는지 검색(`setLoginId` 사용처) 후 영향 보고에 포함한다.

> 설계 검증 시점(2026-06-05) 사전 실증 결과: src/test의 `setLoginId` 약 80곳 전수 조사 — 리터럴 중복 2건(`"22791"`, `"interviewer1"`)은 각각 별도 `@DataJpaTest`(메서드 롤백)·순수 POJO 단위 테스트라 영속 충돌 없음. 롤백 없는 유일한 영속 `@SpringBootTest`(`ApplicationAttachmentDeleteServiceTest`)도 loginId 전부 distinct. `@Column(unique = true)` 임시 적용 후 위험 테스트 27건 전부 통과(BUILD SUCCESSFUL) + 생성 DDL에 `login_id` unique 제약 생성을 실측 확인(JOINED 상속 부모 테이블에서도 정상 생성 — `Applicant.email`/`ciHash`와 동일 패턴). 구현 시점에 동일 검색으로 재확인만 하면 된다.

## 9. 테스트 결과

- 설계 단계 — 코드 변경 없음, 테스트 미실행.

## 10. 구현 순서 권장

1. Scope A(무결성) + E-1(409 핸들러) — 결함 수정 묶음.
2. Scope B(check-email).
3. Scope C/D(계정 변경) + E-2(도메인 메서드).
4. 문서/리포트 갱신(implementation md + html).

단일 슬라이스로 묶어 구현해도 되는 크기이며, 구현 문서는 `docs/codex/implementation/phase-05y-applicant-account-hardening.md`로 산출한다.

## 11. 다음 단계

- 본 슬라이스 구현 → 이후 **Phase 09b** 진행(영향 없음 — 본 슬라이스는 audit 파이프라인을 건드리지 않는다).
- loginId 정책(이메일 vs 별도 ID) 의사결정은 **가입 화면 프론트 작업 시작 전**까지 timebox 권장. 결정 후 후속 비용: 안 1 = signUp의 loginId=email 대입 + email 필수화, 안 2 = check-login-id API 추가.
