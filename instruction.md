# 작업 지시문 — 지원자 이메일 인증 설계 및 구현

## 0. 작업 목적

채용 백엔드의 지원자 회원가입 흐름에 이메일 유효성 인증 프로세스를 추가한다.

현재 지원자 sign-up은 `/api/auth/applicants/sign-up`에서 `ApplicantSignUpService.signUp()` 중심으로 처리된다.

지원자의 `loginId`는 이메일 주소로 사용한다. 따라서 회원가입 전에 해당 이메일 주소의 소유권을 인증해야 한다.

이번 구현의 기본 방식은 다음이다.

- v1 구현 방식: 6자리 숫자 이메일 인증 코드 발송 방식
- v1 제외 방식: 링크 버튼 클릭 인증 방식
- 링크 방식은 향후 확장 가능성만 고려하고, 이번 구현에서는 API/메일/검증 로직을 만들지 않는다.

---

## 1. 반드시 먼저 확인할 기존 파일

구현 전에 아래 파일을 먼저 읽고 기존 코드 스타일, 예외 처리 방식, 테스트 패턴, 응답 포맷을 따른다.

- `src/main/java/com/shinyoung/recruit/controller/ApplicantSignUpController.java`
- `src/main/java/com/shinyoung/recruit/service/ApplicantSignUpService.java`
- `src/main/java/com/shinyoung/recruit/dto/request/ApplicantSignUpRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/ApplicantSignUpResponse.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/User.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/Applicant.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/UserRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/ApplicantRepository.java`
- `src/main/java/com/shinyoung/recruit/config/SecurityConfig.java`
- `src/main/java/com/shinyoung/recruit/exception/InvalidApplicantSignUpException.java`
- `src/main/java/com/shinyoung/recruit/dto/response/ApiResponse.java`
- 기존 controller/service/repository test

---

## 2. 핵심 정책

### 2.1 loginId는 이메일 주소다

지원자 회원가입에서 `loginId`는 이메일 주소로 사용한다.

`ApplicantSignUpRequest.loginId`에는 다음 validation을 적용한다.

- `@NotBlank`
- `@Email`
- `@Size(max = 255)`

기존 `email` 필드는 `loginId`와 의미가 중복된다.

이번 구현에서는 호환성을 위해 `ApplicantSignUpRequest.email`을 바로 제거하지 않아도 된다. 단, 저장 정책은 반드시 다음을 따른다.

- 신규 FE는 `email` 필드를 보내지 않아도 된다.
- request.email 값이 null/blank이면 무시한다.
- request.email 값이 존재하면 정규화한 `loginId`와 동일해야 한다.
- request.email과 loginId가 다르면 `InvalidApplicantSignUpException`을 발생시킨다.
- `Applicant.email`에는 클라이언트가 보낸 email이 아니라 정규화된 `loginId`를 저장한다.

최종 저장 정책:

    applicant.setLoginId(normalizedLoginId);
    applicant.setEmail(normalizedLoginId);

정규화 기준:

- trim
- lower-case
- blank는 invalid

### 2.2 이메일 인증은 sign-up 전에 완료되어야 한다

회원가입 요청에는 이메일 인증 완료 증빙값이 포함되어야 한다.

`ApplicantSignUpRequest`에 다음 필드를 추가한다.

    Long emailVerificationId;
    String emailVerificationProof;

`ApplicantSignUpService.signUp()`는 기존 중복 체크와 저장 전에 이메일 인증 proof를 검증하고 consume해야 한다.

호출 예시:

    emailVerificationService.consumeVerified(
        request.emailVerificationId(),
        request.emailVerificationProof(),
        normalizedLoginId
    );

검증 조건:

- emailVerificationId가 null이면 실패
- emailVerificationProof가 null/blank이면 실패
- verification이 존재해야 함
- purpose가 `APPLICANT_SIGN_UP`이어야 함
- status가 `VERIFIED`여야 함
- 만료되지 않았어야 함
- verification.email이 normalized loginId와 같아야 함
- proof hash가 일치해야 함
- 성공 시 status를 `CONSUMED`로 변경해야 함
- 한 번 consume된 proof는 재사용할 수 없어야 함

---

## 3. 신규 도메인

### 3.1 Entity 추가

신규 엔티티를 추가한다.

파일 경로 예시:

    src/main/java/com/shinyoung/recruit/domain/entity/EmailVerification.java

필드 기준:

    @Entity
    @Table(name = "email_verification")
    public class EmailVerification extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, length = 255)
        private String email;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 50)
        private EmailVerificationPurpose purpose;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private EmailVerificationMethod method;

        @Column(nullable = false, length = 128)
        private String secretHash;

        @Column(length = 128)
        private String proofHash;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private EmailVerificationStatus status;

        @Column(nullable = false)
        private LocalDateTime expiresAt;

        private LocalDateTime verifiedAt;
        private LocalDateTime consumedAt;

        @Column(nullable = false)
        private int attemptCount;

        @Column(nullable = false)
        private int resendCount;

        private LocalDateTime lastSentAt;
    }

필요한 상태 변경 메서드를 엔티티에 둔다.

예시:

    public boolean isExpired(LocalDateTime now)
    public boolean isPending()
    public boolean isVerified()
    public boolean isConsumable(LocalDateTime now)
    public void increaseAttempt()
    public void markVerified(String proofHash, LocalDateTime now)
    public void markConsumed(LocalDateTime now)
    public void markExpired()
    public void markBlocked()

기존 프로젝트 스타일이 setter 중심이면 과도하게 스타일을 바꾸지 말고 일관성을 우선한다. 단, status 변경은 가능하면 의미 있는 메서드로 감싼다.

### 3.2 Enum 추가

파일 경로 예시:

    src/main/java/com/shinyoung/recruit/domain/enums/EmailVerificationPurpose.java
    src/main/java/com/shinyoung/recruit/domain/enums/EmailVerificationMethod.java
    src/main/java/com/shinyoung/recruit/domain/enums/EmailVerificationStatus.java

내용:

    public enum EmailVerificationPurpose {
        APPLICANT_SIGN_UP
    }

    public enum EmailVerificationMethod {
        CODE
    }

    public enum EmailVerificationStatus {
        PENDING,
        VERIFIED,
        CONSUMED,
        EXPIRED,
        BLOCKED
    }

이번 v1에서는 `LINK` enum을 추가하지 않는다. 링크 방식은 후속 phase로 남긴다.

---

## 4. Repository

신규 repository를 추가한다.

파일 경로:

    src/main/java/com/shinyoung/recruit/domain/repository/EmailVerificationRepository.java

기준:

    public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

        Optional<EmailVerification> findByIdAndPurpose(
            Long id,
            EmailVerificationPurpose purpose
        );

        long countByEmailAndPurposeAndCreatedAtAfter(
            String email,
            EmailVerificationPurpose purpose,
            LocalDateTime createdAtAfter
        );

        Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(
            String email,
            EmailVerificationPurpose purpose
        );
    }

주의:

- `BaseEntity`의 생성일 필드명이 `createdAt`이 아니면 실제 필드명에 맞게 repository method 이름을 조정한다.
- 동일 email에 대해 history를 보존해야 하므로 email 단독 unique 제약을 걸지 않는다.

---

## 5. API 설계

### 5.1 인증코드 발송 API

Endpoint:

    POST /api/auth/applicants/email-verifications

Request DTO:

파일 예시:

    src/main/java/com/shinyoung/recruit/dto/request/ApplicantEmailVerificationSendRequest.java

내용:

    public record ApplicantEmailVerificationSendRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email
    ) {}

Response DTO:

파일 예시:

    src/main/java/com/shinyoung/recruit/dto/response/ApplicantEmailVerificationSendResponse.java

내용:

    public record ApplicantEmailVerificationSendResponse(
        Long verificationId,
        long expiresInSeconds,
        long resendAfterSeconds
    ) {}

처리 정책:

1. email trim + lower-case 정규화
2. `UserRepository.existsByLoginId(email)`로 이미 가입된 loginId인지 확인
3. 이미 가입된 이메일이면 `InvalidApplicantSignUpException("이미 사용 중인 이메일입니다.")`
4. 동일 이메일 기준 60초 이내 재발송 차단
5. 동일 이메일 기준 하루 발송 횟수 제한
6. 6자리 숫자 코드 생성
7. 코드 원문은 DB에 저장하지 않고 hash만 저장
8. TTL은 기본 10분
9. 메일 발송
10. 메일 발송 성공 후 verification 저장 또는 저장 후 메일 발송 실패 시 transaction rollback 되도록 구성
11. 응답으로 verificationId, expiresInSeconds, resendAfterSeconds 반환

응답 예시:

    {
      "verificationId": 1,
      "expiresInSeconds": 600,
      "resendAfterSeconds": 60
    }

### 5.2 인증코드 확인 API

Endpoint:

    POST /api/auth/applicants/email-verifications/{verificationId}/confirm

Request DTO:

파일 예시:

    src/main/java/com/shinyoung/recruit/dto/request/ApplicantEmailVerificationConfirmRequest.java

내용:

    public record ApplicantEmailVerificationConfirmRequest(
        @NotBlank(message = "code는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "code는 6자리 숫자여야 합니다.")
        String code
    ) {}

Response DTO:

파일 예시:

    src/main/java/com/shinyoung/recruit/dto/response/ApplicantEmailVerificationConfirmResponse.java

내용:

    public record ApplicantEmailVerificationConfirmResponse(
        boolean verified,
        String emailVerificationProof
    ) {}

처리 정책:

1. verificationId로 조회
2. purpose는 `APPLICANT_SIGN_UP`만 허용
3. status가 `PENDING`이 아니면 실패
4. 만료됐으면 `EXPIRED` 처리 후 실패
5. attemptCount가 5회 이상이면 `BLOCKED` 처리 후 실패
6. 입력 code hash와 저장된 secretHash 비교
7. 불일치 시 attemptCount 증가
8. attemptCount가 maxAttempts 이상이면 BLOCKED 처리
9. 일치 시 status를 `VERIFIED`로 변경
10. 1회성 proof 생성
11. proof 원문은 응답으로 반환
12. DB에는 proofHash만 저장
13. verifiedAt 저장

응답 예시:

    {
      "verified": true,
      "emailVerificationProof": "one-time-proof-token"
    }

### 5.3 회원가입 API 수정

기존 Endpoint 유지:

    POST /api/auth/applicants/sign-up

Request에 다음 필드를 추가한다.

    {
      "loginId": "applicant@example.com",
      "password": "password123!",
      "name": "홍길동",
      "phoneNumber": "01012345678",
      "email": "applicant@example.com",
      "ci": "...",
      "emailVerificationId": 1,
      "emailVerificationProof": "..."
    }

정책:

- `email`은 optional이다.
- `email` 값이 있으면 normalized loginId와 반드시 같아야 한다.
- 최종 저장 시 `Applicant.loginId`와 `Applicant.email`은 모두 normalized loginId로 저장한다.

---

## 6. Controller 설계

신규 컨트롤러를 추가한다.

파일 경로 예시:

    src/main/java/com/shinyoung/recruit/controller/ApplicantEmailVerificationController.java

기준:

    @Validated
    @RestController
    @RequestMapping("/auth/applicants/email-verifications")
    public class ApplicantEmailVerificationController {

        private final EmailVerificationService emailVerificationService;

        public ApplicantEmailVerificationController(EmailVerificationService emailVerificationService) {
            this.emailVerificationService = emailVerificationService;
        }

        @PostMapping
        public ResponseEntity<ApiResponse<ApplicantEmailVerificationSendResponse>> send(
            @Valid @RequestBody ApplicantEmailVerificationSendRequest request
        ) {
            return ResponseEntity.ok(ApiResponse.success(
                emailVerificationService.sendApplicantSignUpCode(request)
            ));
        }

        @PostMapping("/{verificationId}/confirm")
        public ResponseEntity<ApiResponse<ApplicantEmailVerificationConfirmResponse>> confirm(
            @PathVariable Long verificationId,
            @Valid @RequestBody ApplicantEmailVerificationConfirmRequest request
        ) {
            return ResponseEntity.ok(ApiResponse.success(
                emailVerificationService.confirmApplicantSignUpCode(verificationId, request)
            ));
        }
    }

주의:

- 기존 프로젝트가 `/api` prefix를 filter/context-path 등으로 붙이는 구조인지 확인한다.
- 기존 `ApplicantSignUpController`가 `/auth/applicants`로 선언되어 있고 SecurityConfig에서는 `/api/auth/...`로 열어둔 상태이므로 기존 패턴을 그대로 따른다.

---

## 7. Service 설계

신규 서비스를 추가한다.

파일 경로:

    src/main/java/com/shinyoung/recruit/service/EmailVerificationService.java

책임:

- 이메일 정규화
- 가입된 email/loginId 중복 확인
- 발송 제한 검증
- 6자리 코드 생성
- code hash 생성
- proof 생성
- proof hash 생성
- 인증 상태 변경
- sign-up 시 proof consume
- 메일 발송 adapter 호출

주요 메서드:

    @Transactional
    public ApplicantEmailVerificationSendResponse sendApplicantSignUpCode(
        ApplicantEmailVerificationSendRequest request
    )

    @Transactional
    public ApplicantEmailVerificationConfirmResponse confirmApplicantSignUpCode(
        Long verificationId,
        ApplicantEmailVerificationConfirmRequest request
    )

    @Transactional
    public void consumeVerified(
        Long verificationId,
        String proof,
        String loginId
    )

`sendApplicantSignUpCode()` 처리 상세:

1. email normalize
2. `UserRepository.existsByLoginId(normalizedEmail)` 확인
3. 중복이면 실패
4. 최근 발송 verification 조회
5. 최근 발송이 60초 이내면 실패
6. 최근 24시간 발송 횟수 count
7. dailySendLimit 이상이면 실패
8. 6자리 code 생성
9. secretHash 생성
10. EmailVerification 생성
11. status = PENDING
12. method = CODE
13. purpose = APPLICANT_SIGN_UP
14. expiresAt = now + ttl
15. lastSentAt = now
16. 메일 발송
17. 저장
18. response 반환

`confirmApplicantSignUpCode()` 처리 상세:

1. verification 조회
2. 없으면 실패
3. PENDING 아니면 실패
4. 만료면 EXPIRED 처리 후 실패
5. attemptCount >= maxAttempts이면 BLOCKED 처리 후 실패
6. code hash 비교
7. 불일치면 attempt 증가 후 실패
8. 일치하면 proof 생성
9. proofHash 저장
10. status VERIFIED
11. verifiedAt 저장
12. response 반환

`consumeVerified()` 처리 상세:

1. verificationId null이면 실패
2. proof blank이면 실패
3. loginId normalize
4. verification 조회
5. purpose가 APPLICANT_SIGN_UP인지 확인
6. status가 VERIFIED인지 확인
7. 만료 여부 확인
8. verification.email과 normalized loginId 동일 여부 확인
9. proofHash 비교
10. 일치하면 status CONSUMED
11. consumedAt 저장
12. 불일치 또는 재사용은 실패

---

## 8. 메일 발송

Spring Boot Mail을 사용한다.

`build.gradle`에 mail 의존성이 없으면 추가한다.

    implementation 'org.springframework.boot:spring-boot-starter-mail'

메일 발송을 `EmailVerificationService`에 직접 박지 말고 adapter/interface로 분리한다.

파일 예시:

    src/main/java/com/shinyoung/recruit/service/EmailSender.java
    src/main/java/com/shinyoung/recruit/service/SmtpEmailSender.java

Interface:

    public interface EmailSender {
        void send(String to, String subject, String body);
    }

구현체:

    @Service
    public class SmtpEmailSender implements EmailSender {

        private final JavaMailSender javaMailSender;

        public SmtpEmailSender(JavaMailSender javaMailSender) {
            this.javaMailSender = javaMailSender;
        }

        @Override
        public void send(String to, String subject, String body) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
        }
    }

메일 제목 예시:

    신영 채용 시스템 이메일 인증번호입니다.

메일 본문 예시:

    신영 채용 시스템 이메일 인증번호입니다.

    인증번호: 123456

    인증번호는 10분간 유효합니다.
    본인이 요청하지 않았다면 이 메일을 무시해 주세요.

테스트에서는 실제 SMTP를 사용하지 말고 `EmailSender`를 mock 처리한다.

---

## 9. 설정

`application.yml`에 설정을 추가한다.

실제 운영 계정/비밀번호/secret은 반드시 환경변수로 주입한다.

예시:

    spring:
      mail:
        host: ${MAIL_HOST:localhost}
        port: ${MAIL_PORT:1025}
        username: ${MAIL_USERNAME:}
        password: ${MAIL_PASSWORD:}
        properties:
          mail:
            smtp:
              auth: ${MAIL_SMTP_AUTH:false}
              starttls:
                enable: ${MAIL_SMTP_STARTTLS:false}

    email-verification:
      hmac-secret: ${EMAIL_VERIFICATION_HMAC_SECRET:local-dev-email-verification-secret}
      applicant-sign-up:
        code-length: 6
        ttl-minutes: 10
        resend-cooldown-seconds: 60
        max-attempts: 5
        daily-send-limit: 10

설정 바인딩용 properties class를 추가한다.

파일 예시:

    src/main/java/com/shinyoung/recruit/config/EmailVerificationProperties.java

주의:

- 운영 secret을 기본값으로 사용하지 않도록 운영 profile에서는 환경변수 필수 정책을 검토한다.
- local/dev에서는 테스트 편의를 위해 기본값 허용 가능하다.

---

## 10. Hash 정책

코드와 proof는 원문 저장 금지.

우선순위:

1. 기존 프로젝트에 HMAC utility가 있으면 HMAC-SHA256 사용
2. 없으면 기존 `HashUtil.sha256()`를 사용하되 secret을 반드시 포함

hash 입력 예시:

    HashUtil.sha256(email + ":" + purpose + ":" + rawCode + ":" + secret)

proof hash 입력 예시:

    HashUtil.sha256(email + ":" + purpose + ":" + rawProof + ":" + secret)

주의:

- 코드 원문은 메일 발송에만 사용하고 DB에 저장하지 않는다.
- proof 원문은 confirm 응답으로만 반환하고 DB에는 저장하지 않는다.
- 로그에 code/proof 원문을 남기지 않는다.

---

## 11. SecurityConfig 수정

다음 API를 permitAll에 추가한다.

- `POST /api/auth/applicants/email-verifications`
- `POST /api/auth/applicants/email-verifications/*/confirm`

기존 permitAll 라인 근처에 추가한다.

주의:

- `/api/admin/**`, `/api/applicant/**` 같은 broad matcher보다 앞에 둔다.
- CORS allowed methods에 POST는 이미 있으면 유지한다.
- CSRF 정책은 현재 프로젝트 정책을 따른다.
- 기존 sign-up, check-email permitAll은 유지한다.

---

## 12. ApplicantSignUpService 수정

기존 `signUp()` 흐름을 보존하되 다음을 반영한다.

처리 순서 권장:

1. loginId normalize
2. name, phoneNumber, ci trim
3. request.email이 존재하면 normalize 후 loginId와 같은지 확인
4. `UserRepository.existsByLoginId(loginId)` 중복 체크
5. `ApplicantRepository.existsByEmail(loginId)` 중복 체크
6. ciHash 중복 체크
7. `emailVerificationService.consumeVerified(emailVerificationId, emailVerificationProof, loginId)` 호출
8. Applicant 생성
9. applicant.setLoginId(loginId)
10. applicant.setEmail(loginId)
11. password encode
12. applicantRepository.save(applicant)
13. response 반환

주의:

- signUp 전체는 기존처럼 `@Transactional` 유지
- consume과 save가 같은 transaction에 참여해야 함
- save 실패 시 consume만 커밋되는 구조가 되면 안 됨
- 기존 임직원 LDAP/JIT 로그인 흐름에 영향 주지 말 것
- 기존 `User.loginId` unique 정책 유지
- 기존 `Applicant.email` unique 정책 유지

---

## 13. 예외 처리

기존 `InvalidApplicantSignUpException`을 우선 재사용한다.

필요 메시지 예시:

- 이미 사용 중인 이메일입니다.
- 이메일 인증이 필요합니다.
- 이메일 인증 정보가 유효하지 않습니다.
- 이메일 인증번호가 만료되었습니다.
- 이메일 인증번호가 일치하지 않습니다.
- 이메일 인증 시도 횟수를 초과했습니다.
- 이메일 인증이 완료된 주소와 회원가입 이메일이 다릅니다.
- 이메일 인증번호 재발송은 잠시 후 다시 시도해 주세요.
- 이메일 인증 요청 횟수를 초과했습니다.

새 exception이 필요하면 만들 수 있으나, API 응답 일관성을 위해 기존 가입 예외 처리 패턴을 우선한다.

---

## 14. DB 마이그레이션

프로젝트에서 Flyway/Liquibase를 쓰고 있으면 신규 migration을 추가한다.

테이블명:

    email_verification

컬럼 예시:

    id bigint primary key
    email varchar(255) not null
    purpose varchar(50) not null
    method varchar(20) not null
    secret_hash varchar(128) not null
    proof_hash varchar(128)
    status varchar(20) not null
    expires_at timestamp not null
    verified_at timestamp
    consumed_at timestamp
    attempt_count int not null
    resend_count int not null
    last_sent_at timestamp
    created_at timestamp
    updated_at timestamp

권장 인덱스:

    idx_email_verification_email_purpose_created_at
    idx_email_verification_status_expires_at

주의:

- email 단독 unique 금지
- verification history 보존
- 기존 BaseEntity 컬럼명에 맞춘다.

---

## 15. 테스트 필수

### 15.1 EmailVerificationServiceTest 추가

필수 케이스:

1. 정상 발송 시 PENDING 저장
2. 발송 시 email trim/lower-case 정규화
3. 이미 가입된 loginId면 발송 실패
4. 60초 이내 재발송 차단
5. 일일 발송 제한 초과 시 실패
6. 정상 코드 확인 시 VERIFIED + proof 반환
7. 잘못된 코드 입력 시 attemptCount 증가
8. 5회 실패 시 BLOCKED
9. 만료된 코드 확인 시 EXPIRED
10. verified proof consume 성공
11. 다른 email/loginId로 consume 시 실패
12. 이미 CONSUMED된 인증 재사용 실패
13. proof 불일치 실패
14. code/proof 원문이 DB에 저장되지 않는지 확인

### 15.2 ApplicantEmailVerificationControllerTest 추가

필수 케이스:

1. `POST /api/auth/applicants/email-verifications` 정상
2. email blank validation 실패
3. email format validation 실패
4. `POST /api/auth/applicants/email-verifications/{id}/confirm` 정상
5. code blank validation 실패
6. code 6자리 숫자 pattern 실패

### 15.3 SignUp 연동 테스트 추가

기존 `ApplicantSignUpServiceTest` 또는 관련 테스트에 추가한다.

필수 케이스:

1. 이메일 인증 없이 가입 실패
2. 인증 완료 proof로 가입 성공
3. 인증된 이메일과 loginId가 다르면 가입 실패
4. request.email과 loginId가 다르면 가입 실패
5. 가입 성공 시 `Applicant.loginId == Applicant.email`
6. 기존 loginId 중복 체크 유지
7. 기존 ciHash 중복 체크 유지
8. 가입 성공 후 verification status가 CONSUMED인지 확인
9. 같은 proof로 재가입/재사용 실패

### 15.4 Security 테스트

기존 SecurityConfig 테스트가 있으면 추가한다.

필수 케이스:

1. 이메일 인증 발송 API permitAll
2. 이메일 인증 확인 API permitAll
3. 기존 sign-up permitAll 유지
4. 기존 check-email permitAll 유지

---

## 16. 문서 업데이트

구현 후 기존 문서 규칙에 맞게 implementation note를 추가한다.

후보:

- `docs/codex/07-implementation-history.md`
- `docs/codex/implementation/phase-xx-applicant-email-verification.md`
- 필요 시 HTML report

문서에 기록할 내용:

- v1은 6자리 숫자 코드 방식만 구현
- 링크 방식은 제외
- loginId=email 정책
- request.email은 호환용이며 최종 저장은 loginId 기준
- 인증 proof는 1회성 consume 구조
- code/proof 원문 저장 금지
- TTL/attempt/resend/daily limit 정책
- SMTP 설정은 환경변수 기반

---

## 17. 수용 기준

작업 완료 조건:

1. 지원자 회원가입 전 이메일 인증코드 발송 API가 동작한다.
2. 인증코드 확인 API가 동작한다.
3. 인증 완료 proof 없이는 지원자 회원가입이 불가능하다.
4. 인증된 이메일과 sign-up loginId가 다르면 회원가입이 불가능하다.
5. request.email 값이 있고 loginId와 다르면 회원가입이 불가능하다.
6. 가입 성공 시 `Applicant.loginId`와 `Applicant.email`이 동일한 normalized email로 저장된다.
7. 인증 code/proof 원문은 DB에 저장되지 않는다.
8. 인증은 TTL, 최대 실패 횟수, 재발송 제한, 일일 발송 제한을 가진다.
9. 인증 완료 proof는 sign-up 성공 시 `CONSUMED` 처리되고 재사용할 수 없다.
10. 관련 controller/service/sign-up/security 테스트가 추가된다.
11. 기존 로그인 라우팅 구조와 임직원 LDAP/JIT 로그인 흐름이 깨지지 않는다.
12. 전체 테스트가 통과한다.

---

## 18. 구현 시 주의사항

- 기존 로그인 라우팅 구조를 깨지 말 것.
- `User.loginId` unique 정책을 유지할 것.
- `Applicant.email` unique 정책을 유지하되, 이제 loginId와 동일한 값으로 저장할 것.
- 기존 임직원 LDAP/JIT 로그인 흐름에 영향 주지 말 것.
- public API이므로 인증 발송/확인 endpoint에는 abuse 방어 로직을 포함할 것.
- 메일 발송 실패 시 성공 응답하지 말 것.
- 테스트에서는 실제 SMTP를 사용하지 말고 `EmailSender`를 mock 처리할 것.
- 운영 secret, mail password는 application.yml에 평문으로 넣지 말 것.
- 인증번호와 proof 원문을 로그에 남기지 말 것.
- 링크 버튼 방식은 이번 phase에서 구현하지 말 것.