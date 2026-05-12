# 01. Project Context

이 문서는 Codex Cloud 신규 컨테이너에서 현재 Spring Boot 프로젝트를 이어 개발하기 위한 프로젝트 현황 문서다.

## 1. 프로젝트 개요

- 프로젝트명: `recruit`
- 성격: 채용 Renewal 백엔드 Spring Boot 프로젝트
- 현재 단계: 인증, 사용자, 메뉴, 공지사항 중심의 초기 백엔드 골격
- 목표: 레거시 채용 시스템 기능을 REST API 기반 Spring Boot 백엔드로 재구성
- 프론트엔드: 별도 Vue 프로젝트가 존재하며, 현재 백엔드 저장소에서는 정적 빌드 산출물을 제거한 상태

현재 백엔드는 완성된 채용 시스템이 아니라 다음 기반을 잡아둔 초기 상태로 본다.

- Spring Security Session 기반 인증 구조
- 지원자 DB 인증과 임직원 LDAP 인증 라우팅
- `User` / `Applicant` / `Employee` JPA 상속 구조
- 메뉴 API
- 공지사항 API
- 개인정보 암호화 유틸 일부
- JPA Auditing 기반 공통 필드

## 2. 기술 스택

| 구분 | 기준 |
| --- | --- |
| Java | 17 |
| Spring Boot | 4.x |
| Build | Gradle Wrapper |
| Root package | `com.shinyoung.recruit` |
| API 문서 | springdoc-openapi |
| Persistence | Spring Data JPA |
| 개발 DB | H2 |
| 운영 후보 DB | MariaDB |
| Security | Spring Security Session |
| LDAP | Spring Security LDAP / Spring LDAP |
| 암호화 | Custom AES util + JPA AttributeConverter, Jasypt |
| HTML text 처리 | Jsoup |
| 테스트 | JUnit 5, AssertJ, Spring Boot Test, Data JPA Test |

## 3. 최근 정리된 상태

사용자 기준 현재 프로젝트는 다음 정리가 완료된 상태다.

| 항목 | 상태 | Codex 주의사항 |
| --- | --- | --- |
| LDAP 하드코딩 제거 | 완료 | LDAP 설정값을 코드에 다시 넣지 않는다. |
| Gradle Wrapper `distributionUrl` 추가 | 완료 | wrapper 설정을 삭제하거나 되돌리지 않는다. |
| 정적 파일 제거 | 완료 | `src/main/resources/static`의 Vue 빌드 산출물을 다시 만들지 않는다. |

과거 점검 문서 또는 오래된 압축 파일에 위와 다른 내용이 있어도, 현재 작업 기준은 위 상태다.

## 4. 예상 디렉터리 구조

```text
recruit/
├── AGENTS.md
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── docs/codex/
│   ├── 01-project-context.md
│   ├── 02-domain-design.md
│   ├── 03-legacy-feature-map.md
│   ├── 04-implementation-guide.md
│   └── 05-codex-command-prompts.md
├── src/main/java/com/shinyoung/recruit/
│   ├── RecruitApplication.java
│   ├── common/
│   ├── config/
│   ├── controller/
│   ├── domain/
│   │   ├── entity/
│   │   └── repository/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── enumeration/
│   ├── exception/
│   ├── security/auth/
│   └── service/
├── src/main/resources/
│   ├── application.yaml
│   ├── logback.xml
│   └── logback-test.xml
└── src/test/
    ├── java/com/shinyoung/recruit/
    └── resources/application.yaml
```

`src/main/resources/static`은 제거된 상태가 기준이다. 백엔드 개발 중 임의로 복구하지 않는다.

## 5. 현재 패키지 책임

### 5.1 `common`

| 패키지 | 역할 |
| --- | --- |
| `common.crypto` | AES 암복호화, JPA AttributeConverter, static holder |
| `common.hash` | SHA-256 hash 유틸 |
| `common.util` | HTML 문자열에서 검색용 plain text 추출 |

주요 클래스:

- `AesCryptoUtil`
- `AesAttributeConverter`
- `CryptoHolder`
- `HashUtil`
- `HtmlTextUtils`

### 5.2 `config`

| 클래스 | 역할 |
| --- | --- |
| `AuthenticationConfig` | LDAP/DAO 인증 Provider, AuthenticationManager, PasswordEncoder 구성 |
| `SecurityConfig` | FilterChain, CORS, 세션 정책 구성 |
| `CryptoConfig` | AES 유틸 Bean 구성 |
| `JasyptConfig` | Jasypt StringEncryptor 구성 |
| `JpaConfig` | JPA Auditing 활성화 |
| `SwaggerConfig` | OpenAPI 문서 기본 정보 구성 |

주의:

- LDAP 설정값은 외부 설정으로 주입되어야 한다.
- AES key는 `crypto.aes.key` 설정에서 가져온다.
- `CryptoConfig`의 placeholder는 반드시 `${crypto.aes.key}` 형태인지 확인한다. 오래된 코드에 `${crypto.aes.key`처럼 닫는 brace가 빠진 흔적이 있을 수 있다.

### 5.3 `controller`

| 클래스 | Base path | 역할 |
| --- | --- | --- |
| `AuthController` | `/auth` | 로그인, 로그아웃, 현재 사용자 조회 |
| `BoardController` | `/board` | 공지사항 목록/상세/등록 |
| `MenuController` | `/menu` | 메뉴 트리, 메뉴 상세, breadcrumb, 메뉴 생성/수정 |

### 5.4 `domain.entity`

현재 구현된 주요 엔티티:

| Entity | 설명 |
| --- | --- |
| `BaseEntity` | 생성/수정 일시, 생성/수정자 공통 필드 |
| `User` | 지원자/임직원의 공통 상위 엔티티, `JOINED` 상속 |
| `Applicant` | 지원자 사용자, DB password, CI, CI hash, 연락처 |
| `Employee` | 임직원 사용자, LDAP 인증 대상 |
| `DeptRoleMapping` | 부서명 기준 권한 매핑 |
| `Menu` | 관리자/지원자 메뉴 구조 |
| `Notice` | 공지사항 |

앞으로 추가할 설계 엔티티는 `02-domain-design.md`를 기준으로 한다.

### 5.5 `domain.repository`

현재 구현된 주요 Repository:

| Repository | 설명 |
| --- | --- |
| `UserRepository` | `loginId` 기반 사용자 조회 |
| `ApplicantRepository` | `ciHash` 기반 지원자 조회 |
| `EmployeeRepository` | 임직원 사용자 저장/조회 |
| `DeptRoleMappingRepository` | 부서명 기준 권한 조회 |
| `MenuRepository` | 사이트별 메뉴 정렬 조회, path 조회 |
| `NoticeRepository` | 공지사항 조회 |
| `NoticeSpecification` | 공지사항 검색 조건 생성 |

### 5.6 `dto`

현재 스타일:

- Request DTO: `dto.request`
- Response DTO: `dto.response`
- 일부 Request는 class + Lombok getter/setter 사용
- 일부 DTO는 `record` 사용
- 신규 DTO는 가능하면 `record` 우선

주요 응답 래퍼:

```java
ApiResponse<T>
```

기본 성공 응답 메시지:

```text
정상 처리되었습니다.
```

### 5.7 `enumeration`

현재 Enum:

| Enum | 값 | 용도 |
| --- | --- | --- |
| `MenuSite` | `APPLICANT`, `ADMIN` | 지원자/관리자 메뉴 구분 |
| `MenuType` | `ROUTE`, `URL` | 내부 라우트/외부 URL 구분 |
| `NoticeSearchType` | `ALL`, `TITLE`, `CONTENT` | 공지사항 검색 구분 |

신규 Enum은 `enumeration` 패키지에 둔다.

### 5.8 `security.auth`

현재 인증 구조:

| 클래스 | 역할 |
| --- | --- |
| `CustomUserDetails` | DB/LDAP 사용자를 Spring Security principal로 표현 |
| `CustomUserDetailsService` | 지원자 DB 로그인용 UserDetailsService |
| `CustomLdapUserDetailsMapper` | LDAP 조회 결과를 `CustomUserDetails`로 변환 |
| `RoutingAuthenticationProvider` | 지원자 DB 인증과 임직원 LDAP 인증 분기 |

흐름:

1. `/auth/login` 요청
2. `AuthenticationManager.authenticate(...)`
3. `RoutingAuthenticationProvider` 진입
4. `loginId`로 기존 사용자 조회
5. 기존 사용자가 `Applicant`이면 DAO 인증
6. 기존 사용자가 `Employee`이면 LDAP 인증
7. 기존 사용자가 없으면 LDAP 인증 후 Employee JIT 생성 가능
8. 성공 시 세션에 `SecurityContext` 저장

## 6. 현재 API 현황

### 6.1 인증

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/auth/login` | 로그인 |
| POST | `/auth/logout` | 로그아웃 |
| GET | `/auth/me` | 현재 로그인 사용자 조회 |

### 6.2 메뉴

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/menu/tree?site=APPLICANT|ADMIN` | 메뉴 트리 조회 |
| GET | `/menu/{menuId}` | 메뉴 상세 조회 |
| GET | `/menu/breadcrumb?site=...&path=...` | breadcrumb 조회 |
| POST | `/menu/admin/menu` | 메뉴 생성 |
| POST | `/menu/admin/menu/{menuId}` | 메뉴 수정 |

### 6.3 공지사항

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/board/notices` | 공지사항 목록/검색/페이징 |
| GET | `/board/notices/{noticeId}` | 공지사항 상세 |
| POST | `/board/notices` | 공지사항 등록 |

## 7. 설정 기준

### 7.1 `application.yaml`

현재 개발 기준:

- server port: `8000`
- H2 console 활성화
- H2 memory DB 사용
- MariaDB 설정은 운영/연동 시 별도 profile로 분리 권장
- AES key는 환경변수 주입
- LDAP 설정은 외부 설정 주입

예시:

```yaml
crypto:
  aes:
    key: ${AES_SECRET_KEY}
```

LDAP 설정 예시는 실제 값을 넣지 않고 property 구조만 둔다.

```yaml
ldap:
  url: ${LDAP_URL}
  base: ${LDAP_BASE_DN}
  manager-dn: ${LDAP_MANAGER_DN}
  manager-password: ${LDAP_MANAGER_PASSWORD}
  user-search-base: ${LDAP_USER_SEARCH_BASE}
  user-search-filter: ${LDAP_USER_SEARCH_FILTER:(sAMAccountName={0})}
  group-search-base: ${LDAP_GROUP_SEARCH_BASE:}
```

실제 key 이름은 현재 코드와 맞춰 조정한다.

### 7.2 컨테이너 실행 시 환경변수

Codex Cloud 또는 신규 컨테이너에서는 최소 다음 값이 필요할 수 있다.

```bash
export AES_SECRET_KEY=22791194512954214612461221261067
```

LDAP 실제 접속이 필요한 작업이 아니라면 LDAP 환경변수는 dummy 값 또는 test profile로 우회한다.

## 8. 빌드/테스트 명령

기본 명령:

```bash
./gradlew clean test
```

애플리케이션 실행:

```bash
./gradlew bootRun
```

실행 권한이 없으면:

```bash
chmod +x ./gradlew
```

Gradle Wrapper가 정상이어야 하며, `gradle/wrapper/gradle-wrapper.properties`에는 `distributionUrl`이 있어야 한다.

## 9. 현재 구현 스타일 요약

### 9.1 Entity

- `BaseEntity` 상속
- `@Getter`, `@NoArgsConstructor` 사용
- 일부 Entity는 `@Setter` 사용
- `User`는 `@Inheritance(strategy = InheritanceType.JOINED)` 사용
- `Applicant`, `Employee`는 `@PrimaryKeyJoinColumn(name = "user_id")` 사용
- `Notice`, `Menu`는 정적 팩토리 메서드 사용

### 9.2 Service

- 생성자 주입 또는 Lombok `@RequiredArgsConstructor`
- 클래스 레벨 `@Transactional(readOnly = true)`
- 변경 메서드에 `@Transactional`
- Repository 조회 실패 시 `IllegalArgumentException` 또는 custom exception 사용

### 9.3 Controller

- `@RestController`
- `@RequestMapping`
- `ResponseEntity<ApiResponse<...>>`
- `@Valid @RequestBody`
- 조회 조건은 `@RequestParam`

### 9.4 Test

- Repository 테스트: `@DataJpaTest`
- 필요한 설정만 `@Import`
- AssertJ 사용
- 테스트명은 한글 허용

## 10. 구현상 주의할 기존 이슈

다음 항목은 Codex가 첫 작업 시 반드시 확인한다.

1. `CryptoConfig`의 `@Value` placeholder가 `${crypto.aes.key}`로 정상인지 확인한다.
2. Security 설정이 개발 중 `permitAll` 상태일 수 있으므로, 기능 구현 단계에 맞춰 인증 필요 API를 분리한다.
3. LDAP 테스트가 실제 서버 연결에 의존하지 않도록 한다.
4. Jasypt password property가 실제 설정과 맞는지 확인한다.
5. `src/main/resources/static` 제거 상태를 유지한다.
6. MariaDB profile 도입 전에는 H2와 호환되는 JPA 매핑을 우선 유지한다.

## 11. 앞으로 추가될 핵심 도메인

아래 도메인은 현재 설계상 중요하지만 아직 대부분 미구현 상태로 본다.

- `JobPosting`
- `JobPosition`
- `ApplicationFormConfig`
- `Application`
- `QuestionTemplate`
- `QuestionSet`
- `Answer`
- `Stage`
- `StageResult`
- `Education`
- `EducationSemesterGrade`
- `Career`
- `ApplicationMilitary`
- `ApplicationLanguage`
- `ApplicationAward`
- `ApplicationCertificate`
- `ApplicationGap`
- `ApplicationFollowUpQuestion`
- `DocumentEvaluation`
- `Interview`
- `InterviewParticipant`
- `InterviewEvaluation`
- `Attachment`
- `MessageBatch`
- `MessageSendLog`
- `CommonCode`
- `School`
- `ActivityLog`

상세 설계는 `02-domain-design.md`를 기준으로 한다.
