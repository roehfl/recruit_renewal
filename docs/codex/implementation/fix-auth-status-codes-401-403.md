# Fix — 수동 인증/인가 체크 상태코드 정정 (400 → 401/403)

> Phase 외 cross-cutting 수정. 서비스 레이어 수동 인증 체크가 도메인 검증 예외(400)를 재사용해
> "Authentication is required" 류 오류가 400 으로 응답되던 문제를 401(미인증)/403(타입 불일치)으로 정정.

## 1. 요약 (Phase summary)

- 미인증 요청이 `anyRequest().permitAll()`(SecurityConfig) 경로로 컨트롤러까지 도달하면,
  `CurrentEmployeeService`/`CurrentApplicantService` 의 null 체크가 `InvalidStageResultException` 등
  **400 매핑 도메인 예외**를 던져 클라이언트가 인증 만료를 구분할 수 없었다.
- 미인증 → **`AuthenticationRequiredException` → 401**, 인증됐지만 사용자 타입 불일치 → **`AccessForbiddenException` → 403** 으로 분리.
- 응답 포맷은 기존 Security 필터 레벨 처리(`CustomAuthenticationEntryPoint` 401 / `CustomAccessDeniedHandler` 403)와 동일한 `ApiResponse.fail` JSON — 프론트엔드(Vue)는 401 수신 시 로그인 라우트로 이동하는 interceptor 분기가 가능해진다.
- **백엔드는 redirect 하지 않는다** — 분리형 SPA + 세션 구조에서 302 는 XHR/fetch/CORS 문제를 일으키므로, 로그인 이동은 프론트 라우팅 책임.

## 2. 구현 범위 (Implemented scope)

- 신규 예외 2종 + `GlobalExceptionHandler` 매핑 2건(401/403).
- 수동 체크 교체 3곳: `CurrentEmployeeService.getCurrentEmployeeActor`, `CurrentEmployeeService.getCurrentEmployeeActorForInterview`(→ `getCurrentEmployeeId` 경유), `CurrentApplicantService.getCurrentApplicantId`.
- 테스트: `CurrentEmployeeServiceTest` 갱신(+2), `CurrentApplicantServiceTest` 신규(4), `ApplicationControllerTest` 1건 400→403 정정.

**범위 밖**:
- `anyRequest().permitAll()` 자체의 폐기(authenticated 전환) — 공개 API 전수 확인이 필요한 별도 슬라이스(ADR-0007 의 URL 1차 방어선 원칙과 함께 9b+ 에서 검토).
- blank username(`"Employee actor is required."`)·미존재 사용자(`"... user was not found."`) 체크 — 인증/인가가 아닌 무결성 사례로 기존 예외 유지.
- Security 필터 레벨 401/403 처리(EntryPoint/DeniedHandler) — 이미 올바르며 변경 없음.

## 3. 변경 파일 (Changed files)

신규(main):
- `exception/AuthenticationRequiredException.java`
- `exception/AccessForbiddenException.java`

수정(main):
- `exception/GlobalExceptionHandler.java` — 401/403 핸들러 추가
- `service/CurrentEmployeeService.java` — null→401 예외, 타입 불일치→403 예외(2개 메서드)
- `service/CurrentApplicantService.java` — 동일(1개 메서드)

신규(test):
- `service/CurrentApplicantServiceTest.java`

수정(test):
- `service/CurrentEmployeeServiceTest.java` — 인증 시나리오 예외 교체 + interviewer 경로 401/403 테스트 추가
- `controller/ApplicationControllerTest.java` — `employee_user_cannot_create_application` 400→403 정정

## 4. 신규 클래스 (New classes)

### `exception.AuthenticationRequiredException` — Exception
- 책임: 미인증 요청이 인증 필요 로직에 도달. `GlobalExceptionHandler` 가 **401** 매핑.
- 관련: `CurrentEmployeeService`, `CurrentApplicantService`, `CustomAuthenticationEntryPoint`(필터 레벨 동치).

### `exception.AccessForbiddenException` — Exception
- 책임: 인증됐지만 사용자 타입/권한 불일치. **403** 매핑.
- 관련: `CustomAccessDeniedHandler`(필터 레벨 동치).

## 5. 수정 클래스 (Modified classes)

### `exception.GlobalExceptionHandler` — Handler
- `handleAuthenticationRequired` → 401 UNAUTHORIZED, `handleAccessForbidden` → 403 FORBIDDEN. 둘 다 `ApiResponse.fail(message)`.

### `service.CurrentEmployeeService` / `service.CurrentApplicantService` — Service
- null userDetails → `AuthenticationRequiredException`(메시지 기존 유지: "Employee/Applicant authentication is required.").
- 타입 불일치 → `AccessForbiddenException`(메시지 기존 유지: "Only employee/applicant users can access ...").
- blank actor·미존재 사용자 체크는 기존 도메인 예외 유지.

## 6. API 목록

- 신규 endpoint 없음. **기존 API 의 오류 상태코드 계약 변경**:
  - 수동 인증 체크 경유 모든 API — 미인증: 400 → **401**, 타입 불일치: 400 → **403**.
  - 메시지·`ApiResponse` 포맷 불변.

## 7. Entity 관계 요약

- 변동 없음(엔티티 미변경).

## 8. 비즈니스 규칙

1. 미인증 = 401, 인가 실패(타입/권한) = 403, 도메인 검증 = 400 — 의미별 상태코드 분리.
2. 서비스 레이어 수동 체크와 Security 필터 레벨 처리(EntryPoint/DeniedHandler)는 동일 상태코드·동일 `ApiResponse.fail` 포맷.
3. 로그인 이동은 백엔드 redirect 가 아니라 프론트엔드가 401 응답으로 분기.

## 9. 테스트 커버리지 (Test coverage)

- 명령: `.\gradlew.bat test --tests "*CurrentEmployeeServiceTest" --tests "*CurrentApplicantServiceTest" --tests "*StageResultControllerTest" --tests "*StageResultUploadControllerTest" --tests "*InterviewerInterviewControllerTest" --tests "*ApplicationStageResultControllerTest" --tests "*ApplicationControllerTest"`
- 결과: **97 tests, 0 failures, 0 errors** (8개 클래스 — `AdminApplicationControllerTest` 패턴 포함).
  - `CurrentEmployeeServiceTest`(8): null→`AuthenticationRequiredException`, applicant 타입→`AccessForbiddenException`(admin/interviewer 두 경로), blank actor 기존 예외 유지.
  - `CurrentApplicantServiceTest`(4, 신규): 정상 id 반환, null→401 예외, employee 타입→403 예외, 미존재→기존 예외.
  - `ApplicationControllerTest`(35): `employee_user_cannot_create_application` 이 403 단정으로 정정되어 통과.
- 전체 회귀: 미실행(사용자 지시 — scoped 만 실행).

## 10. 알려진 한계 (Known limitations)

- 근본 원인인 `anyRequest().permitAll()` 은 그대로다 — 보호 대상 경로가 Security matcher 에 없으면 여전히 컨트롤러까지 도달한다(수동 체크가 401/403 을 보장하지만, URL 레벨 1차 방어선이 아니라는 구조적 한계).
- 기존 컨트롤러 테스트 중 fall-through 경로를 미인증으로 호출하는 케이스가 적어, 401 경로의 E2E 커버리지는 서비스 단위 테스트 중심이다.

## 11. 다음 고려사항 (Next phase considerations)

- SecurityConfig `anyRequest()` 정책 재검토(permitAll → authenticated + 공개 경로 명시 allowlist)를 별도 슬라이스로 — 9b 의 `/api/admin/audit/**` matcher 추가(ADR-0007)와 함께 다루면 자연스럽다.
- 프론트엔드: axios interceptor 에서 401 → 로그인 라우트, 403 → 권한 안내 분기 구현 필요(프론트 저장소 작업).
