Phase 03e-4 Security Exception JSON Response 구현을 진행한다.

목표:
- Spring Security 인증/인가 실패 응답을 JSON `ApiResponse.fail(...)` 형식으로 통일한다.
- 401/403은 Controller 진입 전 발생하므로 `GlobalExceptionHandler`가 아니라 Security handler에서 처리한다.
- 기존 URL authorization 정책과 StageResult actor propagation은 변경하지 않는다.

먼저 확인할 문서:
- AGENTS.md
- docs/codex/design/phase-03e-admin-auth-hardening-design.md
- docs/codex/implementation/phase-03e-3-url-authorization-hardening.md
- docs/codex/design/phase-03-application-design.md
- docs/codex/07-implementation-history.md

구현 범위:
1. Security handler 추가
   - `CustomAuthenticationEntryPoint` 추가
     - 인증되지 않은 요청 처리
     - HTTP 401
     - `Content-Type: application/json;charset=UTF-8`
     - body: `ApiResponse.fail("Authentication is required.")` 또는 프로젝트 메시지 정책에 맞는 문구
   - `CustomAccessDeniedHandler` 추가
     - 인증은 되었지만 권한 없는 요청 처리
     - HTTP 403
     - `Content-Type: application/json;charset=UTF-8`
     - body: `ApiResponse.fail("Access is denied.")` 또는 프로젝트 메시지 정책에 맞는 문구

2. JSON 직렬화 방식
   - `ObjectMapper`를 주입받아 `ApiResponse.fail(...)`를 writeValue로 응답한다.
   - 문자열 직접 조립 금지.
   - 응답 body 구조는 기존 `ApiResponse`와 동일해야 한다.
   - `success=false`, `message` 존재, `data` 정책은 기존 `ApiResponse.fail` 구현을 따른다.

3. SecurityConfig 연결
   - `http.exceptionHandling(...)`에 entryPoint/accessDeniedHandler 등록
   - 기존 03e-3 URL authorization rule 유지
     - `/admin/**`: `ROLE_ADMIN`, `ROLE_RECRUIT_ADMIN`
     - `/applications/**`: `ROLE_APPLICANT`
     - `GET /job-postings/{jobPostingId}/application`: `ROLE_APPLICANT`
     - public allowlist 유지
     - `anyRequest().permitAll()` 유지
   - CSRF/session/CORS/httpBasic 정책 변경 금지

4. 테스트
   - `ApplicationStageResultControllerTest`
     - anonymous가 `/applications/{applicationId}/stage-results` 호출 시 401
     - response content-type JSON
     - `$.success=false`
     - `$.message` 존재
     - employee/admin이 `/applications/{applicationId}/stage-results` 호출 시 403 + JSON body
   - `StageResultControllerTest`
     - anonymous가 `/admin/stages/{stageId}/results` 또는 command 호출 시 401 + JSON body
     - applicant가 `/admin/**` 호출 시 403 + JSON body
     - non-admin employee가 `/admin/**` 호출 시 403 + JSON body
   - `StageControllerTest`
     - anonymous/applicant/non-admin employee 차단 응답이 JSON인지 대표 케이스로 확인
   - 기존 business exception 응답은 기존처럼 `GlobalExceptionHandler`를 통해 유지되는지 regression 확인
     - validation 400
     - not found 404
   - Service 테스트 변경 최소화

5. 문서화
   - 구현 문서 생성:
     - docs/codex/implementation/phase-03e-4-security-exception-json-response.md
   - 사람용 HTML 리포트 생성:
     - docs/codex/reports/phase-03e-4-security-exception-json-response.html
   - 기존 문서 갱신:
     - docs/codex/design/phase-03e-admin-auth-hardening-design.md
     - docs/codex/design/phase-03-application-design.md
     - docs/codex/07-implementation-history.md

금지:
- URL authorization rule 변경 금지
- `anyRequest().permitAll()` 변경 금지
- StageResult actor 로직 변경 금지
- CurrentEmployeeService 변경 금지
- CurrentApplicantService 변경 금지
- DTO 응답 shape 변경 금지
- `ApiResponse` 구조 변경 금지
- business exception handler 대규모 변경 금지
- DB schema 변경 금지
- LDAP 설정 변경 금지
- Employee FK/AuditActor 추가 금지
- read audit logging 구현 금지
- message/notification 구현 금지

검증:
- ./gradlew.bat test --tests com.shinyoung.recruit.controller.ApplicationStageResultControllerTest
- ./gradlew.bat test --tests com.shinyoung.recruit.controller.StageResultControllerTest
- ./gradlew.bat test --tests com.shinyoung.recruit.controller.StageControllerTest
- ./gradlew.bat test --tests com.shinyoung.recruit.service.StageResultServiceTest --tests com.shinyoung.recruit.service.StageResultCorrectionServiceTest
- ./gradlew.bat clean test --no-daemon

완료 보고:
- 변경 파일 목록
- 401 응답 body 예시
- 403 응답 body 예시
- SecurityConfig 연결 방식
- 테스트 결과
- 남은 이슈