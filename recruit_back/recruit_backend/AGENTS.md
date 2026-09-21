# AGENTS.md — 백엔드 (`recruit_back/recruit_backend/`)

> 경로 표기 `{BE}` `{BT}` `{BR}` — 정의: `docs/domains/_index.md`.
> 작업 절차·검증 명령·git 규칙은 루트 `AGENTS.md`가 기준이다. 이 문서는 백엔드 전역 규칙만 담는다. 도메인별 API·규칙은 도메인 카드에 있다.

## 1. 개요

- 스택: Spring Boot 4.0.x · Java 17 · Spring Data JPA · Spring Security(세션 + LDAP) · Gradle Wrapper. 루트 패키지 `com.shinyoung.recruit`.
- 주요 라이브러리: springdoc-openapi, Jsoup, Apache POI(Excel), openhtmltopdf + Thymeleaf(PDF, `{BR}/templates/`·`{BR}/fonts/`).
- DB: 로컬 H2 파일 DB, 테스트 H2 메모리(`MODE=MySQL`), 운영 MariaDB(접속정보 외부 주입).
- 이 디렉터리에는 백엔드만 둔다. Vue 코드·정적 리소스(`src/main/resources/static`)·프론트 빌드 산출물을 만들지 않는다.
- 읽기 순서: 루트 `AGENTS.md` → `docs/domains/_index.md` → 도메인 카드 → 이 문서.
- ADR: `recruit_back/recruit_backend/docs/adr/`. 운영 수동 DDL: `recruit_back/recruit_backend/docs/ops/`.

## 2. 패키지 구조와 공통 기반

`{BE}` 아래 레이어별 평면 패키지다. 새 클래스는 아래 중 하나에 넣는다. 요청 없이 새 패키지를 만들지 않는다.

| 패키지 | 책임 |
|---|---|
| `controller` | REST 컨트롤러(요청 파싱·응답 포장), 파일 응답 헬퍼 `*ResponseFactory` |
| `service` | 비즈니스 로직·트랜잭션·사용자 확인, 보조 `@Component`(검증기·팩토리) |
| `domain/entity` · `domain/repository` | JPA 엔티티 · Spring Data 리포지토리(projection·Specification 포함) |
| `dto/request` · `dto/response` · `dto/condition` | 요청 DTO · 응답 DTO · 복합 검색 조건 |
| `enumeration` · `exception` | 모든 enum · 커스텀 예외와 `GlobalExceptionHandler` |
| `security/auth` | `CustomUserDetails`, 인증 provider, 401/403 핸들러, `RoleNames` |
| `config` | Spring 설정, `*Properties`, 필터 |
| `common/crypto` · `common/hash` · `common/util` | AES 암복호화 · SHA-256/HMAC · HTML 텍스트 |

공통 기반:

- `{BE}/dto/response/ApiResponse.java` — 응답 `{success, data, message}`. `ApiResponse.success(data)`(메시지 "정상 처리되었습니다."), `ApiResponse.fail(message)`.
- `{BE}/dto/response/PageResponse.java` — 목록 `{content, page, size, totalElements, totalPages, first, last}`, `PageResponse.from(Page)`.
- `{BE}/domain/entity/BaseEntity.java` — `createdAt`·`updatedAt` 자동 기록. `createdBy`·`updatedBy`는 `AuditorAware` 빈이 없어 항상 null.
- `{BE}/config/CorrelationIdFilter.java` — `X-Request-Id`(없거나 부적합하면 UUID)를 MDC `correlationId`와 응답 헤더에 넣는다. 감사 로그가 쓴다.
- 그 밖: `GlobalExceptionHandler`(5절), `WebMvcConfig`(4절), `AesAttributeConverter`(7절), `TimeConfig`(`Clock` 빈).

## 3. 코드 스타일

공통
- 주입: `@RequiredArgsConstructor` + `private final` 필드.
- 현재 시각: 주입한 `Clock`으로 `LocalDateTime.now(clock)`. 인자 없는 `now()` 금지.
- 쿼리: 파생 메서드 또는 JPQL `@Query`. native query 금지. H2(`MODE=MySQL`)·MariaDB 양쪽에서 동작해야 한다.
- 스키마: migration 도구 없이 Hibernate `ddl-auto`가 만든다. 기존 테이블의 컬럼·제약·인덱스를 바꾸면 운영 반영 SQL을 `recruit_back/recruit_backend/docs/ops/`에 추가한다. 새 테이블 DDL 필요 여부는 사용자에게 확인한다.

Entity
- `BaseEntity` 상속(기존 예외: `ActivityLog`, `ClientEventLog`, `JobPosition`, `ApplicationFormConfig`).
- `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. `@Setter` 금지(기존 `User`·`Applicant`·`Employee`·`DeptRoleMapping`만 예외).
- 생성 `public static X create(...)`, 변경은 의미 있는 메서드(`update`, `publish` 등).
- PK `Long id` + `GenerationType.IDENTITY`. enum `@Enumerated(EnumType.STRING)`. `@ManyToOne`·`@OneToOne`은 `FetchType.LAZY`, N:1 단방향 우선.
- `cascade`·`orphanRemoval`은 aggregate 자식에만(현재 `JobPosting`→`JobPosition`·`ApplicationFormConfig`, `Interview`→`InterviewParticipant`, `ApplicationFormPage`→`ApplicationFormPageItem`). 지원서·개인정보·평가에는 금지.
- 필수값 `nullable = false`, 긴 텍스트 `LONGTEXT`/`@Lob`, 조회 컬럼 `@Index`, 동시 수정 방지 `@Version`(현재 `StageResult`).

DTO
- `record`(기존 예외: `LoginRequest`, `ApiResponse`). 요청 DTO에 Bean Validation, 중첩 목록은 `List<@Valid X>`. 필드 간 관계는 서비스에서 검증.
- 엔티티 → 응답은 `public static XResponse from(Entity e)`. 목록은 `PageResponse<T>`. 엔티티·저장 경로·해시·암호문을 응답에 넣지 않는다.

Service
- `@Service`. 조회 위주 클래스는 클래스에 `@Transactional(readOnly = true)`, 변경 메서드에 `@Transactional`(클래스 레벨이 없으면 메서드마다).
- 비즈니스 검증·상태 전이·본인 소유 확인은 서비스에서. 여러 엔티티 변경은 한 메서드 트랜잭션으로.

Controller
- `@RestController`, 반드시 `{BE}/controller` 패키지(밖이면 `/api`가 붙지 않는다).
- 반환 `ResponseEntity.ok(ApiResponse.success(...))`. 파일 응답만 예외(`Resource`·`StreamingResponseBody`·`byte[]`).
- `@Valid @RequestBody`, `@RequestPart`, `@RequestParam`(`page` 기본 0, `size`). 로그인 사용자는 `@AuthenticationPrincipal CustomUserDetails userDetails`로 받아 서비스에 넘긴다.
- 비즈니스 로직·리포지토리 호출·`@Transactional`을 두지 않는다.

## 4. API 경로 규칙

- `WebMvcConfig`가 `/api`를 붙이므로 매핑에는 `/api`를 쓰지 않는다. `SecurityConfig` 매처와 MockMvc 테스트는 `/api` 포함 경로를 쓴다. 접두 없는 경로: `/swagger-ui`, `/api-docs`, `/h2-console`.
- HTTP 메서드는 GET·POST만(CORS 허용도 GET·POST뿐): 조회 `GET /admin/<자원>[/{id}]`, 생성 `POST /admin/<자원>`, 수정 `POST /admin/<자원>/{id}`, 삭제 `POST /admin/<자원>/{id}/delete`, 상태 변경 `POST /admin/<자원>/{id}/<동사>`(`publish`·`close`·`cancel`·`release`).

| 접두 | 사용자 |
|---|---|
| `/admin/...` | 관리자 |
| `/applications/...` · `/applicant/...` | 로그인 지원자 |
| `/interviewer/...` | 면접관(임직원) |
| `/job-postings` · `/codes` · `/schools` · `/addresses` · `/faqs` · `/board/notices` · `/menu/tree` · `/client-events` | 비로그인 공개 |
| `/auth/...` | 로그인·로그아웃·가입 |

- 새 관리자 API는 `/admin/` 아래에 만든다. `/admin/` 밖(`/menu`, `/board` 등)의 쓰기 API는 `anyRequest().permitAll()`로 공개되므로 `SecurityConfig`에 매처를 추가해야 한다.
- 기존 경로는 요청이 있을 때만 바꾼다. 도메인별 전체 경로는 카드 `## API 계약`.

## 5. 예외 처리

- 커스텀 예외는 `{BE}/exception`에 두고 `RuntimeException`을 직접 상속한다(생성자 `(String message)`). 공통 부모·에러코드 enum은 없다.
- `GlobalExceptionHandler`는 예외 클래스를 하나씩 매핑한다. **새 예외를 만들면 핸들러 메서드도 추가한다**(빠뜨리면 500). 도메인에 기존 `Invalid<도메인>Exception`·`<도메인>NotFoundException`이 있으면 재사용한다.

| 예외 | HTTP |
|---|---|
| `*NotFoundException` | 404 |
| `Invalid*Exception`, `ExportRowLimitExceededException`, `PdfBulkLimitExceededException`, 요청 검증·파싱 오류, `MaxUploadSizeExceededException` | 400 |
| `AuthenticationRequiredException` | 401 |
| `AccessForbiddenException` | 403 |
| `DataIntegrityViolationException`, `ObjectOptimisticLockingFailureException` | 409(고정 메시지) |
| `ClientEventRateLimitExceededException` | 429 |
| `ExportGenerationException`, `PdfGenerationException`, `StorageHealthScanException` | 500 |
| `AddressSearchException`, `SchoolSearchException`(외부 API) | 502 |

- 본문은 항상 `ApiResponse.fail(message)`. 메시지에 SQL·제약명·키·내부 경로를 넣지 않는다.
- 401 = 미인증(필터 `CustomAuthenticationEntryPoint`, 서비스 `AuthenticationRequiredException`), 403 = 권한·사용자 유형 불일치(필터 `CustomAccessDeniedHandler`, 서비스 `AccessForbiddenException`). 프론트는 401이면 로그인 화면으로 보낸다.
- `IllegalArgumentException`·`IllegalStateException`은 핸들러가 없어 500이 된다. API 오류에 쓰지 않는다.

## 6. 인증·인가

- Spring Security 세션 인증. JWT·OAuth·stateless로 바꾸지 않는다. CSRF·HTTP Basic·formLogin은 꺼져 있다.
- 로그인 `POST /auth/login`: `AuthenticationManager.authenticate` 직접 호출 → 세션 ID 변경 → `SecurityContext`를 세션에 저장.
- `RoutingAuthenticationProvider`: loginId의 `User`가 `Applicant`면 DB 비밀번호(BCrypt) + `ROLE_APPLICANT` 고정, `Employee`면 LDAP bind, 없으면 LDAP 성공 시 `Employee` 자동 생성(JIT).
- 임직원 권한 = `dept_role_mapping`(LDAP 그룹 cn에 부서명 포함) ∪ `user_role_mapping`(loginId 일치). 상세는 auth-account·role-menu 카드.
- 역할 상수 `{BE}/security/auth/RoleNames.java`(값에 `ROLE_` 포함): `ADMIN`(IT 관리자), `RECRUIT_ADMIN`(채용 운영), `PRIVACY_ADMIN`(정보보호), `INTERVIEWER`, `EMPLOYEE`, `APPLICANT`(매핑 화면에서 부여 불가).
- 매처는 `hasAuthority`/`hasAnyAuthority(RoleNames.X)`만. `hasRole`은 `ROLE_ROLE_`이 되므로 금지. 역할 문자열을 직접 쓰지 않는다.

`SecurityConfig` 요약(위에서 아래로 첫 일치):

| 경로(`/api` 포함) | 권한 |
|---|---|
| `/api/auth/login`·`logout`·`applicants/sign-up`·`applicants/check-email`, swagger·api-docs·h2-console, `/api/menu/tree` | 공개 |
| `POST /api/menu/admin/menu[/*]`, `POST /api/board/**` | ADMIN, RECRUIT_ADMIN |
| `GET /api/job-postings/{id}/application` | APPLICANT |
| `GET /api/job-postings/**`, `POST /api/client-events` | 공개 |
| `/api/admin/audit/**`·`retention/**`·`client-events/**` | 메서드별 세분(PRIVACY_ADMIN 등, privacy-audit·client-event-log 카드) |
| `/api/admin/**` | ADMIN, RECRUIT_ADMIN |
| `/api/applicant/**`, `/api/applications/**` | APPLICANT |
| `/api/interviewer/**` | EMPLOYEE, ADMIN, RECRUIT_ADMIN, INTERVIEWER |
| 그 밖의 모든 경로 | **공개**(`anyRequest().permitAll()`) |

- 좁은 매처를 넓은 매처(`/api/admin/**`)보다 위에 둔다. 순서가 보안 요구사항이다.
- 보호 접두 밖의 새 엔드포인트는 공개된다. 의도가 아니면 매처를 추가하고 `{BT}/config/SecurityConfigTest.java`에 허용·거부 테스트를 추가한다.
- 서비스 확인: 지원자 API는 `CurrentApplicantService.getCurrentApplicantId(userDetails)`, 관리자·면접관 명령은 `CurrentEmployeeService`(null → 401, 유형 불일치 → 403).
- LDAP 접속·검색 값은 코드에 쓰지 않고 `recruit.ldap.*`(`LdapProperties`)로만 주입한다(8절).

## 7. 개인정보·암호화

- `@Convert(converter = AesAttributeConverter.class)` 필드는 AES/CBC(무작위 IV)로 저장된다. 키 `crypto.aes.key`(= `AES_SECRET_KEY`, 32자). 암호문이 매번 달라 검색·비교·unique가 불가능하다.
- 암호화 필드: `Applicant.ci`, `ApplicationBasicInfo`의 이름·`countryCode`·연락처(`mobilePhone`·`emergencyPhone`·`email`)·장애 코드·주소.
- 평문(현행): `User.loginId`·`User.name`, `Applicant.email`(unique)·`Applicant.phoneNumber`. 암호화 전환은 요청 시에만(기존 데이터 이행 필요).
- 검색할 개인정보는 별도 해시 컬럼으로 찾는다. 예: `Applicant.ciHash` = `HashUtil.sha256(ci)`(unique, 중복 가입 확인).
- `HashUtil`: 키 없는 SHA-256(검색 키·파일 해시). `AuditHmac`: `AUDIT_HMAC_SECRET` 기반 HMAC-SHA256(감사 로그 가명 연결자·파기 덮어쓰기 값). `AuditHmac`에 CI·이메일·전화 원문을 넣지 않는다.
- 새 개인정보 필드는 암호화 여부를 사용자에게 확인한다. 파기 대상이면 privacy-audit 카드를 따른다.
- `v-html`로 렌더될 HTML은 응답 DTO에서 `HtmlTextUtils.sanitize`를 거친다(예: `NoticeDetailResponse`). 검색용 텍스트는 `HtmlTextUtils.extractText`.
- 로그·예외 메시지·응답에 개인정보 원문·비밀값·내부 저장 경로를 남기지 않는다.
- 테스트 데이터는 가짜 값만: `applicant@example.com`, `01000000000`, `test-ci-value`, `dummy`·`test`.

## 8. 설정·실행

- 설정 파일은 `{BR}/application.yaml` 하나(프로파일 파일 없음). 테스트는 `recruit_back/recruit_backend/src/test/resources/application.yaml`을 읽는다(H2 메모리, `ddl-auto: create-drop`, 테스트용 감사 HMAC 값).
- 핵심 키: `server.port`(8080), `spring.jpa.hibernate.ddl-auto`, `spring.datasource.*`(H2 파일 DB, 운영은 외부 설정), `crypto.aes.key`, `audit.*`, `recruit.*`, `client-event-log.*`.
- 새 설정은 `recruit.<기능>` 아래에 `${ENV_NAME:기본값}`로 추가하고 `{BE}/config/`에 `@ConfigurationProperties(prefix = "recruit.<기능>")` 클래스를 둔다. 최상위 `recruit:` 키 중복 금지(`{BT}/config/ApplicationYamlTest.java`가 검사). 기본값 없는 키는 테스트 yaml에도 넣는다.

환경변수(실제 값은 저장소·문서·로그에 남기지 않는다):

| 변수 | 부류 | 기본값 | 비고 |
|---|---|---|---|
| `AES_SECRET_KEY` | 비밀 | 없음(필수) | 32자. 로컬 예시 `22791194512954214612461221261067` — **로컬·테스트 전용 예시, 운영 키 아님** |
| `AUDIT_HMAC_SECRET` | 비밀 | 빈 값 | 비면 기동 실패(`AUDIT_ALLOW_FALLBACK_SECRET=true`면 비운영 대체 값, `prod` 프로파일에서는 거부) |
| `AUDIT_ALLOW_FALLBACK_SECRET` | 플래그 | `false` | 로컬에서만 `true` |
| `NICE_MOCK_ENABLED` | 플래그 | `false` | 로컬에서만 `true` |
| `NICE_SITE_CODE`·`NICE_SITE_PASSWORD`·`NICE_RETURN_URL`·`NICE_ERROR_URL` | 자격증명·환경 | 빈 값 | 상세 auth-nice-verification 카드 |
| `LDAP_MANAGER_DN` | 자격증명 | 없음 | 바인드 계정 DN. 유출 시 교체 대상 |
| `LDAP_MANAGER_PASSWORD` | 자격증명 | 없음 | 로그·저장소에 절대 남기지 않는다 |
| `LDAP_URL` | 환경 정보 | `ldap://`(미설정) | 내부망 주소 |
| `LDAP_BASE_DN`, `LDAP_USER_SEARCH_BASE`, `LDAP_GROUP_SEARCH_BASE` | 조직 정보 | 빈 값 | base DN, 사용자 검색 base, 그룹(부서) 검색 base |
| `LDAP_USER_SEARCH_FILTER` | 관용구 | `(sAMAccountName={0})` | AD 표준이라 기본값을 둔다 |
| `JUSO_CONFM_KEY`, `NEIS_API_KEY`, `UNIV_INFO_API_SERVICE_KEY`, `UNIV_DEPT_API_SERVICE_KEY` | 비밀(API 키) | 빈 값 | 없으면 해당 외부 검색만 502 |
| `SPRING_JPA_DDL_AUTO` | 설정 | `update` | 운영은 `validate`/`none` 권장 |
| `RECRUIT_ATTACHMENT_STORAGE_ROOT`, `RECRUIT_POSTING_IMAGE_STORAGE_ROOT` | 환경 정보 | `attachments`, `posting-images` | 상대경로면 실행 디렉터리 기준 |

- NICE 모듈(`libs/NiceID.jar`)은 기동에 **`--add-exports java.base/com.sun.crypto.provider=ALL-UNNAMED` 가 필수**다(없으면 `IllegalAccessError`). `bootRun`·`test` 는 `build.gradle` 에 있고 **운영 실행 스크립트에도 넣는다**.
- 조직 정보는 자격증명은 아니지만 조직 구조가 드러나므로 실제 값을 커밋하지 않는다.
- LDAP 값이 비어도 기동은 된다. 경고 로그가 남고 LDAP 로그인만 실패한다(최소 `LDAP_URL`·`LDAP_MANAGER_DN`·`LDAP_MANAGER_PASSWORD`·`LDAP_USER_SEARCH_BASE` 필요).
- 나머지 한도·타임아웃 변수(`RECRUIT_*`, `JUSO_*`, `NEIS_*`, `UNIV_*`, `CLIENT_EVENT_LOG_*`)는 `{BR}/application.yaml`에서 확인한다.
- 폐쇄망 등 환경변수 주입이 어려우면 소스를 고치지 말고 jar 옆 설정 파일로 덮어쓴다: `java -jar <jar> --spring.config.additional-location=file:./config/`.

로컬 실행(백엔드 디렉터리, 포트 8080, Swagger `http://localhost:8080/swagger-ui`):

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; $env:AUDIT_ALLOW_FALLBACK_SECRET='true'; $env:NICE_MOCK_ENABLED='true'; .\gradlew.bat bootRun
# Linux (실행 권한이 없으면 chmod +x ./gradlew)
AES_SECRET_KEY='<로컬 예시 키>' AUDIT_ALLOW_FALLBACK_SECRET=true NICE_MOCK_ENABLED=true ./gradlew bootRun
```

- 로그: `{BR}/logback.xml` → 실행 디렉터리 `logs/recruit.log`.
- Jasypt: `JasyptConfig`의 암호 속성 `app.modules.pkgs`가 `application.yaml`에 없고 `ENC(...)` 설정도 주석 처리돼 있어 현재 쓰이지 않는다. `ENC()` 도입 전 사용자에게 확인한다.

## 9. 테스트

- 수정한 클래스·패키지 테스트만 실행한다. 명령은 루트 `AGENTS.md` 5절. 전체 실행은 요청 시에만. `AES_SECRET_KEY` 환경변수가 필요하다.
- 테스트는 `{BT}` 아래 main과 같은 패키지에 둔다. 공용 픽스처는 `{BT}/support`.

| 대상 | 방식 |
|---|---|
| 리포지토리 | `@DataJpaTest`. 암호화 필드가 있으면 `@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})` |
| 서비스 | `@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")` + `@Transactional`(롤백). 외부 의존 없는 로직은 `@ExtendWith(MockitoExtension.class)` |
| 컨트롤러·보안 | `@SpringBootTest` + `webAppContextSetup(context).apply(springSecurity())`, 인증은 `.with(authentication(토큰))`(principal `CustomUserDetails.fromUser/fromLdap`), 비로그인 `.with(anonymous())` |
| 인증 provider | Mockito mock. 실제 LDAP 연결 금지 |

- 시간 의존: `@TestConfiguration` 안에 `@Bean @Primary Clock`(`Clock.fixed(...)`).
- 메서드명: 한글 설명형(`공고를_등록한다`)과 영어 snake_case가 섞여 있다. 같은 클래스의 방식을 따른다. 단언은 AssertJ, MockMvc `jsonPath("$.success")`.
- 힙: `build.gradle` test 블록 `maxHeapSize = '2g'`(캐시된 Spring 컨텍스트가 많아 기본 512MB면 OOM). 낮추지 않는다. 새 `@SpringBootTest`는 기존과 같은 `properties` 선언을 써서 컨텍스트를 재사용한다(속성·`@TestConfiguration`·`@MockitoBean` 조합이 다르면 새 컨텍스트).

## 10. 금지 사항

- 보안: 계정·비밀번호·LDAP DN/비밀번호·DB 접속정보·암호화 키·API 키를 코드·테스트·문서에 쓰지 않는다. 예시는 `example`·`dummy`·`test` 값.
- 구조(요청 없이 금지): 세션 인증 → JWT/OAuth, JPA → MyBatis, 멀티모듈화, 루트 패키지 변경, `ApiResponse<T>` 폐기, `User`/`Applicant`/`Employee` 상속 구조 변경, GET·POST 외 HTTP 메서드, 새 의존성.
- 리소스: `src/main/resources/static`·Vue 코드·프론트 빌드 산출물을 만들지 않는다. `recruit_back/recruit_backend/gradle/wrapper/gradle-wrapper.properties`를 임의로 바꾸지 않는다.
- 원본 설계 Excel의 `WBS(화면)`·`WBS(서버)` 탭은 열람·요약·반영하지 않는다(구현과 무관한 진행 문서).
- git: 요청 없이 `git commit`·`git push`·브랜치 삭제·강제 이동 금지. 작업 전 현재 브랜치와 변경 파일을 확인한다(루트 `AGENTS.md` 7절).

## 11. 빌드·테스트 실패 대응

아래 순서로 원인을 분류한다. 수정 범위 안이면 고치고, 밖이면 보고한다.

1. `AES_SECRET_KEY` 누락 — `Could not resolve placeholder 'AES_SECRET_KEY'`.
2. `bootRun` 기동 실패 `AUDIT_HMAC_SECRET (audit.hmac-secret) must be set` — 로컬은 `AUDIT_ALLOW_FALLBACK_SECRET=true`.
3. Gradle Wrapper — `gradlew` 실행 권한, 배포본(gradle-9.2.1)·의존성(`mavenCentral()`) 다운로드 실패. 외부망이 없으면 미리 채운 Gradle 캐시(`GRADLE_USER_HOME`)나 사내 미러가 필요하다. 설정 변경은 사용자에게 확인한다.
4. 테스트 `OutOfMemoryError` — 9절 힙·컨텍스트 재사용.
5. `application.yaml` 중복 키·문법 오류 — `ApplicationYamlTest`.
6. LDAP 설정 누락 또는 실제 LDAP 연결 시도.
7. H2/JPA 스키마 호환 오류(MariaDB 전용 문법 등).
8. 기존 테스트 실패 — 내 변경과 무관하면 보고만 한다.
9. 실제 구현 오류.

## 12. 보고 형식

```text
변경 요약
- ...

변경 파일
- ...

테스트 결과
- 실행 명령: ...
- 결과: 성공/실패
- 실패 시 원인: ...

계약·카드 변경
- ... (없으면 "계약 영향 없음")

주의 사항·남은 이슈
- ...
```

테스트를 실행하지 못했으면 이유를 적는다.
