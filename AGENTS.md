# AGENTS.md

이 저장소는 신영증권 채용 Renewal 백엔드 Spring Boot 프로젝트다. Codex는 기존 코드 스타일과 현재 구현 방향을 최대한 유지하면서 점진적으로 개발해야 한다.

## 1. 반드시 먼저 읽을 문서

작업을 시작하기 전에 아래 문서를 순서대로 읽어라.

1. `docs/codex/01-project-context.md`
2. `docs/codex/02-domain-design.md`
3. `docs/codex/03-legacy-feature-map.md`
4. `docs/codex/04-implementation-guide.md`
5. 필요 시 `docs/codex/05-codex-command-prompts.md`

`AGENTS.md`는 행동 규칙이고, 상세 설계/현황은 `docs/codex` 하위 문서가 기준이다.

## 2. 프로젝트 기준

- Runtime: Java 17
- Framework: Spring Boot 4.x
- Build: Gradle Wrapper
- Root package: `com.shinyoung.recruit`
- Application name: `recruit`
- 기본 개발 DB: H2
- 운영 후보 DB: MariaDB
- 인증 방향: Spring Security Session 기반
- 지원자 인증: DB password 인증
- 임직원 인증: LDAP 인증
- API 응답 기본 형태: `ApiResponse<T>`
- JPA Auditing 공통 필드: `BaseEntity`

## 3. 최근 정리된 상태

사용자가 프로젝트 파일을 아래와 같이 정리한 상태를 전제로 한다.

1. LDAP 하드코딩 값 제거 완료
2. `gradle-wrapper.properties`의 `distributionUrl` 추가 완료
3. `src/main/resources/static` 및 포함 정적 파일 제거 완료

따라서 Codex는 다음을 지켜야 한다.

- LDAP URL, base DN, manager DN, password, 검색 base/filter 등은 코드에 하드코딩하지 않는다.
- Gradle Wrapper 설정을 임의로 되돌리지 않는다.
- 제거된 프론트엔드 빌드 산출물 또는 `static` 리소스를 다시 만들지 않는다.
- 백엔드 작업 중 Vue/정적 리소스 생성 작업을 하지 않는다.

## 4. 금지 사항

### 4.1 원본 Excel 관련 금지

원본 설계 Excel이 저장소에 포함되어 있더라도 다음 탭은 확인하거나 개발 기준으로 사용하지 않는다.

- `WBS(화면)`
- `WBS(서버)`

두 WBS 탭은 Spring Boot 구현과 무관한 프로젝트 진행 문서이므로 열람/요약/반영하지 않는다.

### 4.2 보안 금지

- 계정, 비밀번호, LDAP 관리자 DN, LDAP password, DB password, 암호화 키를 코드에 하드코딩하지 않는다.
- 테스트 코드에도 실제 운영/사내 접속 정보를 넣지 않는다.
- 샘플 값이 필요하면 `example`, `dummy`, `test` 값을 사용한다.
- 민감정보 검색용 필드는 평문이 아니라 hash 필드를 별도로 둔다.
- CI, 전화번호, 이메일, 주소 등 개인정보는 저장/검색 방식을 명확히 분리한다.

### 4.3 구조 변경 금지

- 명확한 요청 없이 Spring Security Session 기반 인증을 JWT/OAuth 구조로 바꾸지 않는다.
- 명확한 요청 없이 JPA를 MyBatis로 바꾸지 않는다.
- 명확한 요청 없이 멀티모듈 구조로 바꾸지 않는다.
- 명확한 요청 없이 패키지 루트 `com.shinyoung.recruit`를 바꾸지 않는다.
- 명확한 요청 없이 기존 API 응답 형식 `ApiResponse<T>`를 폐기하지 않는다.
- 명확한 요청 없이 기존 `User`/`Applicant`/`Employee` 상속 구조를 갈아엎지 않는다.

## 5. 코드 스타일 원칙

### 5.1 패키지 구조

기존 패키지 구조를 우선 유지한다.

```text
com.shinyoung.recruit
├── common
├── config
├── controller
├── domain
│   ├── entity
│   └── repository
├── dto
│   ├── request
│   └── response
├── enumeration
├── exception
├── security.auth
└── service
```

새 기능도 특별한 이유가 없으면 위 구조에 맞춰 추가한다.

예시:

- Entity: `domain.entity`
- Repository: `domain.repository`
- Service: `service`
- Controller: `controller`
- Request DTO: `dto.request`
- Response DTO: `dto.response`
- Enum: `enumeration`
- 인증/인가 세부 클래스: `security.auth`
- 설정 클래스: `config`

### 5.2 Entity 스타일

- 공통 감사 필드는 `BaseEntity`를 상속한다.
- PK는 기본적으로 `Long id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용한다.
- 연관관계는 가능한 한 객체 참조로 표현한다.
- `@ManyToOne`은 기본적으로 `fetch = FetchType.LAZY`를 사용한다.
- Enum은 `@Enumerated(EnumType.STRING)`으로 저장한다.
- Entity를 Controller 응답으로 직접 반환하지 않는다.
- Entity 생성은 가능하면 정적 팩토리 메서드 또는 명확한 생성 메서드를 사용한다.
- 도메인 상태 변경은 setter 남발보다 의미 있는 메서드로 감싼다.

기존 코드에는 setter가 섞여 있으므로 전체 리팩터링을 선행하지 말고, 새 도메인부터 점진적으로 개선한다.

### 5.3 DTO 스타일

- 신규 Request/Response DTO는 가능하면 `record`를 우선 사용한다.
- Bean Validation은 Request DTO에 둔다.
- Response DTO에는 `from(Entity entity)` 정적 팩토리 메서드를 둔다.
- 목록 응답은 기존 `PageResponse<T>` 패턴을 우선 사용한다.

### 5.4 Service 스타일

- Service 클래스에는 `@Service`를 사용한다.
- 읽기 전용 클래스/메서드는 `@Transactional(readOnly = true)`를 사용한다.
- 변경 메서드는 별도 `@Transactional`을 명시한다.
- Controller에 비즈니스 로직을 넣지 않는다.
- Repository 조회 실패는 명확한 예외로 처리한다.

### 5.5 Controller 스타일

- Controller는 `@RestController`를 사용한다.
- Base path는 도메인 단위로 단순하게 잡는다.
- 응답은 기본적으로 `ResponseEntity<ApiResponse<...>>` 형태를 유지한다.
- 성공 메시지/실패 메시지 형태는 기존 `ApiResponse`와 충돌하지 않게 한다.
- `@Valid`를 사용해 Request 검증을 수행한다.

## 6. 인증/인가 기준

현재 방향은 다음과 같다.

- Spring Security Session 기반
- `/auth/login`에서 `AuthenticationManager`를 직접 사용
- 로그인 성공 시 `SecurityContext`를 세션에 저장
- 지원자: DB 기반 password 인증
- 임직원: LDAP 인증
- 임직원 최초 로그인 시 LDAP 인증 성공 후 Employee JIT 생성 가능
- 부서명 기반 역할 매핑은 `DeptRoleMapping`을 사용

주의:

- LDAP 설정값은 반드시 외부 설정으로 주입한다.
- 실제 AD/LDAP 서버에 의존하는 테스트는 기본 단위 테스트로 만들지 않는다.
- 인증 관련 코드는 작은 단위로 검증하고, 통합 테스트에서는 mock/stub/profile을 사용한다.

## 7. 설계 기준

도메인 설계는 `docs/codex/02-domain-design.md`를 기준으로 한다.

핵심 루트는 다음이다.

- `JobPosting`: 채용 공고
- `Application`: 지원서
- `User`: 지원자/임직원/면접관/관리자 계정의 공통 기반
- `Stage`: 전형 단계
- `Interview`: 면접 일정/조
- `MessageBatch`, `MessageSendLog`: SMS/Email/알림톡 발송 이력
- `CommonCode`: 공통 코드
- `ActivityLog`: 사용자 동선/감사 로그

레거시 기능 범위는 `docs/codex/03-legacy-feature-map.md`를 참고한다. 레거시 Endpoint/JSP는 신규 API의 참고 자료일 뿐이며, 그대로 복제하는 것이 목표는 아니다.

## 8. 구현 우선순위 원칙

한 번에 전체 채용 시스템을 구현하지 않는다. 작은 단위의 vertical slice로 진행한다.

권장 순서:

1. 빌드/테스트 안정화
2. 공통 예외 처리, 응답 규격, 검증 규칙 정리
3. 인증/세션/권한 기본 흐름 안정화
4. `JobPosting` + `JobPosition` + `ApplicationFormConfig`
5. `Stage` + `StageResult`
6. `Application` 기본 생성/조회/임시저장/최종제출
7. 학력/경력/자격/어학/병역/포상/공백기간 세부 도메인
8. 질문 템플릿/질문 세트/답변
9. 면접 일정/참가자/평가
10. 메시지 발송 이력
11. 통계/엑셀/PDF/파기 관리

각 단계는 Entity, Repository, Service, Controller, DTO, Test를 함께 추가한다.

## 9. 테스트 기준

- 변경한 기능은 테스트를 추가하거나 기존 테스트를 보완한다.
- Repository 테스트는 H2 + `@DataJpaTest` 기반을 우선 사용한다.
- AttributeConverter 또는 외부 설정이 필요한 테스트는 필요한 config만 `@Import`한다.
- Security 통합 테스트는 실제 LDAP에 연결하지 않도록 한다.
- 테스트 메서드명은 현재 프로젝트처럼 한글 설명형을 허용한다.
- AssertJ를 우선 사용한다.

작업 완료 전 기본 확인:

```bash
./gradlew clean test
```

로컬/컨테이너 환경에서 암호화 키가 필요하면 예시 값으로 환경변수를 설정한다.

```bash
export AES_SECRET_KEY=22791194512954214612461221261067
./gradlew clean test
```

## 10. Codex 작업 방식

Codex는 작업 전 다음을 먼저 수행한다.

1. `AGENTS.md`와 `docs/codex` 문서 읽기
2. 현재 브랜치 상태 확인
3. `./gradlew clean test` 또는 최소 `./gradlew test` 실행 가능 여부 확인
4. 실패 시 원인을 분류한다.
   - 환경변수 누락
   - Gradle Wrapper 문제
   - LDAP 외부 연결 의존
   - 기존 테스트 실패
   - 실제 구현 오류
5. 사용자가 요청한 범위 내에서만 수정한다.
6. 변경 후 테스트를 실행하고 결과를 요약한다.

## 11. 응답/보고 형식

Codex가 작업을 완료하면 다음 형식으로 보고한다.

```text
변경 요약
- ...

변경 파일
- ...

테스트 결과
- 실행 명령: ...
- 결과: 성공/실패
- 실패 시 원인: ...

주의 사항
- ...
```

테스트를 실행하지 못했으면 실행하지 못한 이유를 명확히 남긴다.
