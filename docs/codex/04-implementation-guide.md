# 04. Implementation Guide

이 문서는 Codex가 실제 코드를 수정할 때 따라야 할 구현 규칙과 작업 순서를 정리한 문서다.

## 1. 작업 시작 절차

Codex는 매 작업 시작 시 다음을 수행한다.

1. `AGENTS.md`를 읽는다.
2. `docs/codex/01-project-context.md`를 읽고 현재 프로젝트 상태를 파악한다.
3. 도메인 기능이면 `docs/codex/02-domain-design.md`를 읽는다.
4. 레거시 기능 대응이면 `docs/codex/03-legacy-feature-map.md`를 읽는다.
5. 현재 파일 상태를 확인한다.
6. 필요한 경우 최소 빌드/테스트를 먼저 실행한다.

기본 테스트 명령:

```bash
export AES_SECRET_KEY=22791194512954214612461221261067
./gradlew clean test
```

LDAP 실제 연결이 필요 없는 작업에서는 LDAP 관련 값은 dummy/test profile로 우회한다.

## 2. 변경 범위 원칙

- 요청받은 기능 범위만 수정한다.
- 대규모 구조 개편은 사용자 요청 없이는 하지 않는다.
- 기능 구현과 전역 리팩터링을 한 작업에 섞지 않는다.
- formatter 대량 변경을 하지 않는다.
- 기존 동작 API를 불필요하게 깨지 않는다.
- 프론트엔드 정적 파일을 생성/복원하지 않는다.

## 3. 신규 기능 구현 기본 단위

신규 도메인 기능은 가능하면 아래 단위를 함께 구현한다.

```text
Entity
Repository
Request DTO
Response DTO
Service
Controller
Test
```

예: `JobPosting` 구현 시

```text
src/main/java/com/shinyoung/recruit/domain/entity/JobPosting.java
src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java
src/main/java/com/shinyoung/recruit/dto/request/JobPostingCreateRequest.java
src/main/java/com/shinyoung/recruit/dto/request/JobPostingUpdateRequest.java
src/main/java/com/shinyoung/recruit/dto/response/JobPostingResponse.java
src/main/java/com/shinyoung/recruit/service/JobPostingService.java
src/main/java/com/shinyoung/recruit/controller/JobPostingController.java
src/test/java/com/shinyoung/recruit/domain/repository/JobPostingRepositoryTest.java
src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java
```

Controller test는 프로젝트 테스트 구조가 안정화된 뒤 추가한다.

## 4. Entity 구현 규칙

### 4.1 기본 형태

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // fields...

    private JobPosting(...) {
        // assign
    }

    public static JobPosting create(...) {
        return new JobPosting(...);
    }

    public void update(...) {
        // change state
    }
}
```

기존 코드에는 public/protected 기본 생성자와 setter가 혼재되어 있다. 신규 코드는 가능한 한 다음을 우선한다.

- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- 필요한 생성은 `create(...)`
- 변경은 의미 있는 method
- setter 남발 금지

단, 기존 스타일과 충돌하거나 테스트 작성이 과하게 어려워지면 현실적으로 조정한다.

### 4.2 연관관계

기본:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "job_posting_id", nullable = false)
private JobPosting jobPosting;
```

원칙:

- N:1 단방향 우선
- 양방향은 조회/도메인 로직상 명확히 필요할 때만 사용
- cascade remove 기본 금지
- orphanRemoval 기본 금지
- 개인정보/지원서/평가 도메인에서는 삭제 cascade를 특히 조심한다.

### 4.3 Column/Index

- 필수값은 `nullable = false`
- 긴 HTML/Text는 `@Lob` 또는 `columnDefinition = "LONGTEXT"` 검토
- 중복 방지 필요 시 unique constraint 추가
- 자주 조회하는 FK/status/date는 index 검토

예:

```java
@Table(
    name = "job_posting",
    indexes = {
        @Index(name = "idx_job_posting_status", columnList = "status"),
        @Index(name = "idx_job_posting_period", columnList = "start_date,end_date")
    }
)
```

H2/MariaDB 호환성을 함께 고려한다.

## 5. DTO 구현 규칙

### 5.1 Request DTO

가능하면 record를 사용한다.

```java
public record JobPostingCreateRequest(
        @NotBlank String title,
        @NotNull LocalDateTime startDate,
        @NotNull LocalDateTime endDate
) {
}
```

검증 규칙:

- null 금지: `@NotNull`
- 문자열 공백 금지: `@NotBlank`
- 길이: `@Size`
- 숫자 범위: `@Min`, `@Max`
- 날짜 간 관계는 Service 또는 custom validator에서 검증

### 5.2 Response DTO

Entity 변환 메서드를 둔다.

```java
public record JobPostingResponse(
        Long id,
        String title,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    public static JobPostingResponse from(JobPosting jobPosting) {
        return new JobPostingResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getStatus().name(),
                jobPosting.getStartDate(),
                jobPosting.getEndDate()
        );
    }
}
```

Entity를 그대로 반환하지 않는다.

## 6. Service 구현 규칙

기본 형태:

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;

    public PageResponse<JobPostingSummaryResponse> getJobPostings(...) {
        // read
    }

    @Transactional
    public Long create(JobPostingCreateRequest request) {
        // validate
        // create entity
        // save
    }
}
```

원칙:

- 비즈니스 검증은 Service에서 수행한다.
- 단순 변환은 DTO factory에서 수행한다.
- Repository 결과가 없으면 명확한 exception을 던진다.
- 여러 Entity를 변경하는 작업은 Service method 단위 transaction으로 묶는다.

## 7. Controller 구현 규칙

기본 형태:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings")
public class JobPostingAdminController {

    private final JobPostingService jobPostingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody JobPostingCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.create(request)));
    }
}
```

원칙:

- 관리자 API와 지원자 API를 path 또는 controller로 분리한다.
- Controller는 request parsing과 response wrapping에 집중한다.
- 비즈니스 로직을 Controller에 넣지 않는다.
- 현재 프로젝트의 `ApiResponse<T>` 패턴을 유지한다.

## 8. API Path 규칙

기존 path와 충돌하지 않게 신규 path는 다음 방향을 권장한다.

### 8.1 관리자 API

```text
/admin/job-postings
/admin/applications
/admin/stages
/admin/interviews
/admin/messages
/admin/codes
```

### 8.2 지원자 API

```text
/job-postings
/applications
/applications/me
/me/password
```

### 8.3 면접관 API

```text
/interviewer/interviews
/interviewer/evaluations
```

### 8.4 기존 API

현재 존재:

```text
/auth/**
/menu/**
/board/**
```

기존 API path를 바꿔야 할 때는 별도 요청이 있을 때만 한다.

## 9. 예외 처리 규칙

현재 프로젝트에는 일부 custom exception만 있다.

권장 개선 방향:

1. 공통 business exception 도입
2. Error code enum 도입 여부 검토
3. `@RestControllerAdvice`로 validation/business/not-found/security error 응답 통일
4. 기존 `ApiResponse.fail(...)`와 호환

초기 최소 형태:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }
}
```

단, 공통 예외 체계 도입은 기능 구현과 별도 작업으로 분리하는 것이 좋다.

## 10. 보안/인증 구현 규칙

### 10.1 세션 인증 유지

이 프로젝트는 JWT 기반이 아니라 Spring Security Session 기반이다.

금지:

- 요청 없이 JWT 도입
- 요청 없이 OAuth2/OIDC 구조 변경
- 요청 없이 stateless API로 전환

### 10.2 LDAP 설정

LDAP 설정값은 외부 설정으로 주입한다.

나쁜 예:

```java
contextSource.setPassword("real-password");
```

좋은 예:

```java
contextSource.setPassword(ldapProperties.managerPassword());
```

설정값 class 후보:

```java
@ConfigurationProperties(prefix = "ldap")
public record LdapProperties(
        String url,
        String base,
        String managerDn,
        String managerPassword,
        String userSearchBase,
        String userSearchFilter,
        String groupSearchBase
) {
}
```

### 10.3 권한

- 임직원 권한은 LDAP 부서명 + `DeptRoleMapping` 기준
- 지원자 권한은 기본 `ROLE_APPLICANT` 후보
- 관리자/면접관은 Employee + role로 우선 표현
- Controller path별 권한 제한은 기능 안정화 후 단계적으로 적용

## 11. 개인정보/암호화 규칙

### 11.1 CI

- CI 원문은 암호화 저장
- CI 조회/중복확인은 hash 사용
- `ciHash` unique index 고려

### 11.2 연락처/주소/수신자 정보

암호화 후보:

- phoneNumber
- address
- recipientContact
- recipientName
- CI

검색이 필요한 경우:

- normalized value hash를 별도 필드로 둔다.

### 11.3 테스트 데이터

실제 이름/전화번호/CI/이메일을 테스트에 넣지 않는다.

좋은 예:

```java
String ciValue = "test-ci-value";
applicant.setEmail("applicant@example.com");
applicant.setPhoneNumber("01000000000");
```

## 12. 테스트 작성 규칙

### 12.1 Repository Test

```java
@DataJpaTest
@Import({JpaConfig.class})
class JobPostingRepositoryTest {
}
```

암호화 converter가 필요한 경우:

```java
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
```

### 12.2 Service Test

가능하면 mock 기반 단위 테스트를 사용한다.

- Mockito 사용 여부는 현재 의존성 확인 후 적용
- 복잡한 JPA 연관 저장은 `@SpringBootTest`보다 slice test 우선

### 12.3 Security Test

- 실제 LDAP 연결 금지
- 인증 provider는 mock/stub/profile 분리
- `/auth/login` 통합 테스트는 테스트용 provider 또는 test profile이 필요하다.

### 12.4 테스트명

현재 프로젝트처럼 한글 테스트명을 허용한다.

```java
@Test
void 공고를_등록한다() {
}
```

## 13. 빌드 실패 대응

Codex가 `./gradlew clean test` 실패를 만나면 다음 순서로 확인한다.

1. `AES_SECRET_KEY` 누락 여부
2. `gradlew` 실행 권한
3. `gradle-wrapper.properties`의 `distributionUrl`
4. `CryptoConfig` placeholder 오타
5. LDAP 설정값 누락 또는 실제 서버 연결 시도
6. H2/JPA schema 호환 오류
7. 테스트 코드 자체 오류

실패 원인을 수정 범위 안에서 해결할 수 있으면 수정한다. 범위를 벗어나면 보고한다.

## 14. 신규 도메인 구현 상세 순서

### 14.1 JobPosting slice

1. Enum 추가
   - `JobPostingStatus`
   - 필요 시 `JobPostingType`
2. Entity 추가
3. Repository 추가
4. DTO 추가
5. Service 추가
6. Controller 추가
7. Repository/Service test 추가
8. 빌드/테스트 확인

### 14.2 ApplicationFormConfig slice

1. `JobPosting` 조회 기반으로 config 생성
2. boolean flag validation
3. 공고별 1개 config 보장
4. 지원서 작성 시 config 조회 API 제공

### 14.3 Application slice

1. 지원자 본인 기준 생성
2. 공고 접수 기간 검증
3. 중복 지원 검증
4. 임시저장 상태
5. 최종제출 상태
6. 최종제출 후 수정 제한

### 14.4 StageResult slice

1. 공고별 stage 생성
2. 지원서별 stage result 저장
3. 관리자 결과 변경
4. 지원자 결과 조회

### 14.5 Interview slice

1. 면접 일정/조 생성
2. 지원자 participant 배정
3. 면접관 participant 배정
4. 면접관별 평가 생성/수정
5. 관리자 조회

## 15. Excel/PDF/메시지 기능 구현 시점

아래 기능은 core domain CRUD 이후에 구현한다.

- Apache POI Excel download/upload
- Puppeteer PDF 연동
- SMS/Email/알림톡 외부 연동
- 통계 집계
- 개인정보 파기

이 기능들은 외부 시스템/파일/대량처리/보안 정책이 얽히므로 초기 Entity 설계와 기본 CRUD가 안정된 뒤 진행한다.

## 16. 작업 완료 보고 체크리스트

작업 완료 전 확인:

- [ ] 요청 범위를 벗어난 변경이 없는가?
- [ ] 하드코딩된 secret이 없는가?
- [ ] 정적 frontend 산출물을 만들지 않았는가?
- [ ] Entity가 `BaseEntity`를 적절히 상속하는가?
- [ ] API 응답이 `ApiResponse<T>`를 유지하는가?
- [ ] DTO가 Entity를 직접 노출하지 않는가?
- [ ] 테스트를 추가/수정했는가?
- [ ] `./gradlew clean test`를 실행했는가?
- [ ] 실행하지 못했다면 이유를 명확히 남겼는가?

보고 형식:

```text
변경 요약
- ...

변경 파일
- ...

테스트 결과
- 실행 명령: ./gradlew clean test
- 결과: 성공/실패

주의 사항
- ...
```
