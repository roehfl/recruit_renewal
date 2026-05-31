# Infra 01 - 전역 `/api` 경로 Prefix

## Phase summary

모든 애플리케이션 컨트롤러 엔드포인트 앞에 공통 `/api` prefix를 부여한다. 컨트롤러
매핑을 개별 수정하지 않고 `WebMvcConfigurer.configurePathMatch`의 `addPathPrefix`로
`com.shinyoung.recruit.controller` 패키지에 한해 중앙에서 적용한다. Spring Security
요청 매처와 컨트롤러 MockMvc 테스트 경로를 함께 `/api`로 정렬한다.

H2 datasource는 별도 변경하지 않는다. 메인 `application.yaml`의
`jdbc:h2:~/recruit`는 이미 파일(인디스크) 모드로 `~/recruit.mv.db`에 영속되므로
현 설정을 유지한다(사용자 확인). 테스트용 `jdbc:h2:mem:testdb`는 ephemeral 목적이라
의도적으로 인메모리를 유지한다.

## Purpose

- 프론트엔드/리버스 프록시가 API 트래픽을 단일 `/api` prefix로 식별·라우팅할 수 있게 한다.
- 컨트롤러마다 prefix를 하드코딩하지 않고 단일 지점에서 관리한다.
- springdoc(swagger, api-docs)·H2 콘솔 같은 비-컨트롤러 엔드포인트는 영향받지 않게 한다.

## Scope

- `com.shinyoung.recruit.controller` 패키지 컨트롤러 전 엔드포인트에 `/api` prefix 부여.
- Spring Security `requestMatchers` 경로를 `/api/...`로 정렬.
- 컨트롤러 MockMvc 테스트의 요청 경로 28개 파일을 `/api/...`로 정렬.

## Out of scope

- swagger-ui / api-docs / v3/api-docs / h2-console 경로(컨트롤러 아님, prefix 미적용).
- springdoc `springdoc.api-docs.path` 등 설정값.
- H2 datasource URL/모드 변경.
- 서비스/도메인/DTO 로직 변경.
- CORS 허용 origin/메서드 변경.

## Changed files

### New

- `src/main/java/com/shinyoung/recruit/config/WebMvcConfig.java`

### Modified

- `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java`
  (컨트롤러 대상 `requestMatchers` 8개 라인에 `/api` prefix, swagger/h2-console은 유지)
- 컨트롤러 MockMvc 테스트 28개 파일(요청 경로 문자열 리터럴 `/api` 정렬):
  - `AdminApplicationControllerTest`, `AdminApplicationFormLayoutControllerTest`,
    `AdminApplicationSectionControllerTest`, `AdminAttachmentStorageHealthControllerTest`,
    `ApplicantInterviewControllerTest`, `ApplicantSignUpControllerTest`,
    `ApplicationAnswerControllerTest`, `ApplicationAttachmentControllerTest`,
    `ApplicationAttachmentDownloadControllerTest`, `ApplicationAwardControllerTest`,
    `ApplicationCareerControllerTest`, `ApplicationCertificateControllerTest`,
    `ApplicationControllerTest`, `ApplicationEducationControllerTest`,
    `ApplicationGapPeriodControllerTest`, `ApplicationLanguageControllerTest`,
    `ApplicationMilitaryControllerTest`, `ApplicationStageResultControllerTest`,
    `InterviewAdminControllerTest`, `InterviewerEvaluationControllerTest`,
    `InterviewEvaluationAdminControllerTest`, `InterviewerInterviewControllerTest`,
    `JobPostingControllerTest`, `JobPostingPublicControllerTest`,
    `JobPostingQuestionControllerTest`, `QuestionTemplateControllerTest`,
    `StageControllerTest`, `StageResultControllerTest`

## Class-by-class explanation

### WebMvcConfig (New)

- package: `com.shinyoung.recruit.config`
- class type: Config
- responsibility: 컨트롤러 엔드포인트 전역 `/api` prefix 부여.
- key methods:
  - `configurePathMatch(PathMatchConfigurer)` →
    `configurer.addPathPrefix("/api", HandlerTypePredicate.forBasePackage("com.shinyoung.recruit.controller"))`
- 구현 노트:
  - `forBasePackage`로 우리 컨트롤러 패키지에만 한정 → springdoc/H2 콘솔 핸들러 미영향.
  - prefix는 핸들러 매핑 단계에서 적용되므로 컨트롤러의 `@RequestMapping`/`@GetMapping` 값은
    그대로 둔다(코드 가독성 측면에서 컨트롤러는 도메인 상대경로만 유지).

### SecurityConfig (Modified)

- package: `com.shinyoung.recruit.config`
- class type: Config
- responsibility: 인증/인가 필터 체인. 요청 매처가 실제 요청 URI(`/api/...`)와 일치하도록 정렬.
- 변경 매처:
  - `/api/auth/login`, `/api/auth/logout`, `/api/auth/applicants/sign-up` → permitAll
  - `/api/menu/tree` → permitAll
  - `GET /api/job-postings/{jobPostingId}/application` → `ROLE_APPLICANT`
  - `GET /api/job-postings/**` → permitAll
  - `/api/admin/**` → `ROLE_ADMIN`, `ROLE_RECRUIT_ADMIN`
  - `/api/applicant/**` → `ROLE_APPLICANT`
  - `/api/interviewer/**` → `ROLE_EMPLOYEE`, `ROLE_ADMIN`, `ROLE_RECRUIT_ADMIN`, `ROLE_INTERVIEWER`
  - `/api/applications/**` → `ROLE_APPLICANT`
- 유지 매처(비-컨트롤러): `/swagger-ui/**`, `/api-docs/**`, `/v3/api-docs/**`, `/h2-console/**`

## API list

엔드포인트 자체는 변경 없이 모든 경로 앞에 `/api`만 추가된다. 대표 예시:

| Method | Before | After |
| --- | --- | --- |
| POST | `/auth/login` | `/api/auth/login` |
| POST | `/auth/applicants/sign-up` | `/api/auth/applicants/sign-up` |
| GET | `/job-postings` | `/api/job-postings` |
| GET | `/job-postings/{id}` | `/api/job-postings/{id}` |
| POST | `/applications` | `/api/applications` |
| GET | `/applications/{applicationId}` | `/api/applications/{applicationId}` |
| GET | `/admin/job-postings/{jobPostingId}/stages` | `/api/admin/job-postings/{jobPostingId}/stages` |
| POST | `/admin/interviews/{interviewId}/evaluations/initialize` | `/api/admin/interviews/{interviewId}/evaluations/initialize` |
| GET | `/interviewer/interviews` | `/api/interviewer/interviews` |
| GET | `/menu/tree` | `/api/menu/tree` |

비-컨트롤러 경로(`/swagger-ui`, `/api-docs`, `/v3/api-docs`, `/h2-console`)는 prefix 없이 유지.

## Entity relationship summary

엔티티/도메인 변경 없음.

## Business rules

- prefix는 `com.shinyoung.recruit.controller` 패키지 핸들러에만 적용된다.
- 보안 인가 규칙은 의미 변화 없이 경로만 `/api`로 이동한다(권한 매핑 동일).
- 비-컨트롤러 인프라 엔드포인트(swagger/api-docs/h2-console)는 prefix 대상이 아니다.

## Test coverage

- 테스트 명령:
  - 대표 검증: `./gradlew.bat test --tests StageControllerTest --tests InterviewEvaluationAdminControllerTest --tests SecurityConfigTest`
  - 전체: `./gradlew.bat test` (`AES_SECRET_KEY` 예시 키 주입)
- 라우팅 검증 결과: `/api/...` 엔드포인트 라우팅·인가 단언 전부 통과
  (StageController의 endpoint 호출, InterviewEvaluationAdmin 8/8 인가 포함, SecurityConfig 통과).
- 사전-실패(본 변경과 무관, 시스템 클럭 의존): 일부 컨트롤러 테스트의 fixture가
  공고 접수기간(예: 2026-05-01~2026-05-30)을 사용하는데, 실행 시점 시스템 날짜가
  2026-06-01이라 `JobApplicationService.create`의 접수기간 검증
  (`접수기간 내에만 지원서를 처리할 수 있습니다`)에서 fixture 셋업이 실패한다.
  이는 HTTP 라우팅 이전 서비스 계층 예외로, `/api` prefix와 무관하다.
- 전체 스위트 결과 수치는 `docs/codex/reports/infra-01-api-path-prefix.html` Test 섹션에 기록.

## Known limitations

- 컨트롤러 소스의 `@*Mapping` 값에는 `/api`가 보이지 않는다(중앙 config 적용).
  새 컨트롤러도 도메인 상대경로만 작성하면 자동으로 `/api`가 붙는다는 점을 인지해야 한다.
- 시스템 클럭이 테스트 fixture의 접수기간을 벗어나면 일부 컨트롤러 테스트가 날짜 사유로
  실패한다(별도 안정화 과제, 본 작업 범위 밖).

## Next phase considerations

- 날짜 의존 fixture를 고정 `Clock` 주입 또는 동적 접수기간으로 안정화(테스트 하드닝).
- 프론트엔드/배포 프록시의 base URL을 `/api`로 정렬했는지 확인.
