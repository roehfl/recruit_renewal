# 02. Domain Design

이 문서는 설계 Excel에서 Spring Boot 구현에 필요한 도메인 설계만 추려 Codex 개발 기준으로 재정리한 문서다.

원본 Excel의 `WBS(화면)`, `WBS(서버)` 탭은 이 문서 작성 대상에서 제외되었으며, Codex도 해당 탭을 확인하거나 반영하지 않는다.

## 1. 도메인 설계 기본 원칙

### 1.1 현재 코드와 맞출 기준

현재 프로젝트에는 이미 다음 구조가 있다.

- `BaseEntity`: `createdAt`, `updatedAt`, `createdBy`, `updatedBy`
- `User`: 지원자/임직원의 공통 상위 Entity
- `Applicant`: 지원자
- `Employee`: 임직원
- `DeptRoleMapping`: 부서별 권한 매핑
- `Menu`: 메뉴
- `Notice`: 공지사항

신규 도메인은 기존 스타일을 우선 유지한다.

### 1.2 PK/FK 기준

- 기본 PK는 `Long id`를 사용한다.
- JPA 연관관계는 단순 `Long xxxId`만 두는 것보다 Entity 참조를 우선 고려한다.
- 단, 화면/API DTO에서는 ID를 사용한다.
- `@ManyToOne(fetch = FetchType.LAZY)`를 기본으로 한다.
- 일괄 삭제/파기 등 대량 작업이 필요한 도메인은 FK 제약과 cascade를 신중하게 적용한다.

### 1.3 감사 필드 기준

원본 설계서에는 `createdAt`, `createdDateTime`, `updatedAt`, `updatedDateTime`이 혼재한다.

구현 기준은 현재 프로젝트의 `BaseEntity`와 맞춰 다음으로 통일한다.

```java
createdAt
updatedAt
createdBy
updatedBy
```

### 1.4 Enum 기준

설계서의 Code/Enum/String 후보는 다음 기준으로 정리한다.

- 값이 고정적이고 비즈니스 로직 분기에 사용되면 Enum
- 관리자 화면에서 추가/수정해야 하는 값이면 `CommonCode`
- 외부/레거시 연동 코드와 매핑되어야 하면 코드 문자열 보관 + 표시명은 `CommonCode`

Enum은 `@Enumerated(EnumType.STRING)`으로 저장한다.

### 1.5 개인정보/민감정보 기준

지원자 정보에는 개인정보가 많다.

- CI: 암호화 저장 + hash 검색 필드 분리
- 휴대폰번호/주소/이메일 등: 암호화 또는 정책 검토 후 저장
- 검색/중복확인: 평문 검색 대신 hash 또는 normalized field 사용
- 메시지 발송 이력의 수신 연락처/이름도 암호화 대상 후보

### 1.6 레거시 테이블 기준

설계 문서의 Legacy Table은 신규 Entity 설계 참고 자료다. 신규 테이블명을 반드시 레거시 테이블명과 동일하게 맞출 필요는 없다.

레거시 명칭 오탈자 후보:

| 원본 표기 | 구현 권장 |
| --- | --- |
| `Attatchment` | `Attachment` |
| `Carrier` | `Career` |
| `jobReportingId` | `jobPostingId` |
| `catidate` | `candidate` |
| `corelationId` | `correlationId` |
| `contryCode` | `countryCode` |

구현 시 class/field 명은 명확한 영어 표기를 사용하고, 레거시 표기는 주석 또는 migration 문서에서만 참고한다.

## 2. 전체 도메인 관계 요약

```text
JobPosting
├── JobPosition              // 공고별 응시구분/직군/근무지
├── ApplicationFormConfig    // 공고별 지원서 항목 사용 여부
├── QuestionSet              // 공고별 질문 세트
├── Stage                    // 공고별 전형 단계
├── MessageBatch             // 공고별 메시지 발송 묶음
└── Interview                // 공고별 면접 일정/조

User
├── Applicant                // 지원자
└── Employee                 // 임직원/관리자/면접관 기반

Application
├── Answer
├── Education
│   └── EducationSemesterGrade
├── Career
├── ApplicationLanguage
├── ApplicationAward
├── ApplicationMilitary
├── ApplicationCertificate
├── ApplicationGap
├── ApplicationFollowUpQuestion
├── Attachment
├── DocumentEvaluation
├── InterviewEvaluation
└── StageResult

Interview
├── InterviewParticipant     // 지원자/면접관 모두 포함
└── InterviewEvaluation      // 면접관 N명 x 지원자 M명 평가

MessageBatch
└── MessageSendLog
```

## 3. 구현 우선순위 기준 도메인 그룹

### 3.1 공고/지원서 설정 그룹

- `JobPosting`
- `JobPosition`
- `ApplicationFormConfig`
- `QuestionTemplate`
- `QuestionSet`

이 그룹은 관리자 전형설정 화면의 기반이다.

### 3.2 지원서 작성 그룹

- `Application`
- `Answer`
- `Education`
- `EducationSemesterGrade`
- `Career`
- `ApplicationLanguage`
- `ApplicationAward`
- `ApplicationMilitary`
- `ApplicationCertificate`
- `ApplicationGap`
- `Attachment`

이 그룹은 지원자 지원서 작성/확인/최종제출의 기반이다.

### 3.3 전형/평가 그룹

- `Stage`
- `StageResult`
- `DocumentEvaluation`
- `Interview`
- `InterviewParticipant`
- `InterviewEvaluation`

이 그룹은 서류전형, 1차면접, 최종면접, 면접관 평가의 기반이다.

### 3.4 메시지/로그/공통 그룹

- `MessageBatch`
- `MessageSendLog`
- `ActivityLog`
- `CommonCode`
- `School`
- `ApplicationFollowUpQuestion`

이 그룹은 운영/관리 기능의 기반이다.

## 4. Entity 상세 설계

아래 필드는 원본 설계를 구현 기준으로 정리한 것이다. 실제 구현 시에는 validation, nullable, length, index, unique 제약을 함께 검토한다.

## 4.1 JobPosting

채용 공고 루트 Entity다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `title` | `String` | 공고 제목 |
| `type` | `JobPostingType` 또는 code | 공고 유형 |
| `description` | `String` / `@Lob` | 공고 내용, HTML 가능성 있음 |
| `startDate` | `LocalDateTime` | 공고 시작일 |
| `endDate` | `LocalDateTime` | 공고 종료일 |
| `status` | `JobPostingStatus` | `OPEN`, `CLOSED` 등 |

관계:

- `JobPosting` 1 : N `JobPosition`
- `JobPosting` 1 : 1 `ApplicationFormConfig`
- `JobPosting` 1 : N `QuestionSet`
- `JobPosting` 1 : N `Stage`
- `JobPosting` 1 : N `Application`

구현 메모:

- 공고 내용은 Summernote HTML을 받을 수 있으므로 HTML 원문과 검색용 text 분리를 고려한다.
- 공고 상태는 날짜 기반 자동 계산과 수동 상태를 분리할지 검토한다.

## 4.2 JobPosition

공고별 지원사항이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `jobPosting` | `JobPosting` | 공고 FK |
| `applicationType` | `String` 또는 `CommonCode` | 응시구분 |
| `jobFamily` | `String` 또는 `CommonCode` | 지원분야/직군 |
| `workLocation` | `String` 또는 `CommonCode` | 근무지 |

구현 메모:

- 응시구분/직군/근무지는 코드값 성격이 강하다.
- 관리자가 공고별로 선택 가능한 지원사항을 구성하는 기능과 연결된다.

## 4.3 ApplicationFormConfig

공고별 지원서 항목 사용 여부 설정이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `jobPosting` | `JobPosting` | 공고 FK, 1:1 |
| `requireAward` | `boolean` | 포상 입력 필요 여부 |
| `requireEducation` | `boolean` | 학력사항 입력 필요 여부 |
| `requireGap` | `boolean` | 공백기간 입력 필요 여부 |
| `requireCareer` | `boolean` | 경력사항 입력 필요 여부 |
| `requireLanguage` | `boolean` | 어학 입력 필요 여부 |
| `requireCertificate` | `boolean` | 자격증 입력 필요 여부 |
| `requireCareerExplain` | `boolean` | 경력기술서 필요 여부 |
| `requireGrade` | `boolean` | 성적 입력 필요 여부 |

구현 메모:

- 지원서 화면에서 입력 섹션 노출 여부를 결정한다.
- 서버 검증도 이 설정을 기준으로 해야 한다.

## 4.4 User / Applicant / Employee

현재 프로젝트는 이미 `User` 상위 Entity와 `Applicant`, `Employee` 하위 Entity를 사용한다.

### User

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `loginId` | `String` | 로그인 ID |
| `name` | `String` | 이름 |

설계서에는 `userType`, `email`, `password`, `phoneNumber`, `birthDate`, `gender`, `address`, `nationality`, `veteranStatus`, `disabilityGrade`, `disabilityType` 등이 User에 포함되어 있으나, 현재 구현은 Applicant/Employee 분리 구조다.

구현 기준:

- 공통 로그인 식별자와 이름은 `User`
- 지원자 개인정보는 `Applicant`
- 임직원/부서 정보는 `Employee`
- 관리자/면접관 역할은 별도 User subclass보다 role/authority로 우선 처리

### Applicant

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `email` | `String` | 지원자 이메일 |
| `userName` | `String` | 지원자 이름. 기존 `User.name`과 중복 여부 정리 필요 |
| `password` | `String` | BCrypt password |
| `phoneNumber` | `String` | 휴대폰번호 |
| `ci` | `String` | NICE 본인인증 CI, 암호화 저장 |
| `ciHash` | `String` | CI 중복확인/조회용 hash |
| `birthDate` | `LocalDate` | 생년월일 후보 |
| `gender` | Enum | 성별 |
| `address` | `String` | 주소 |
| `nationality` | `String` | 국적 |
| `veteranStatus` | `String` 또는 code | 보훈 |
| `disabilityGrade` | Enum/code | 장애등급 |
| `disabilityType` | Enum/code | 장애유형 |

구현 메모:

- 이메일은 변경될 수 있으므로 영구 PK로 쓰지 않는다.
- CI는 평문 검색 금지. `ciHash`를 기준으로 중복 체크한다.
- `User.name`과 `Applicant.userName` 중복은 향후 정리 필요. 신규 필드 추가 시 중복을 늘리지 않는다.

### Employee

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `deptName` | `String` | LDAP department |
| `empNo` | `String` 후보 | 사번이 필요하면 추가 검토 |

구현 메모:

- LDAP 인증 성공 후 JIT 생성할 수 있다.
- 부서별 권한은 `DeptRoleMapping`에서 조회한다.

## 4.5 Application

지원서 루트 Entity다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `applicant` | `Applicant` | 지원자 FK |
| `jobPosting` | `JobPosting` | 공고 FK |
| `jobPosition` | `JobPosition` 후보 | 선택한 응시구분/직군/근무지 묶음 |
| `appliedDepartmentCode` | code/string | 지원 부서 코드 |
| `appliedJobRoleCode` | code/string | 지원 직무 코드 |
| `allowReassignment` | `Boolean` | 적합성 동의 여부 |
| `status` | `ApplicationStatus` 후보 | 임시저장/제출/취소 등 |
| `submittedAt` | `LocalDateTime` 후보 | 최종 제출 시각 |

관계:

- `Application` N : 1 `Applicant`
- `Application` N : 1 `JobPosting`
- `Application` 1 : N `Answer`
- `Application` 1 : N `Education`
- `Application` 1 : N `Career`
- `Application` 1 : 0..1 `ApplicationMilitary`
- `Application` 1 : N `ApplicationLanguage`
- `Application` 1 : N `ApplicationAward`
- `Application` 1 : N `ApplicationCertificate`
- `Application` 1 : N `ApplicationGap`
- `Application` 1 : N `Attachment`
- `Application` 1 : N `StageResult`

구현 메모:

- 지원서 작성은 임시저장과 최종제출 상태를 분리해야 한다.
- 최종제출 후 수정 가능 여부는 전형/공고 상태와 함께 검증한다.

## 4.6 QuestionTemplate

질문 은행이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `question` | `String` | 질문 내용 |

구현 메모:

- 공통 질문 원본이다.
- 공고에 적용할 때는 `QuestionSet`으로 복제하여 질문 문구 변경 영향을 막는 방향이 안전하다.

## 4.7 QuestionSet

공고별 질문 세트다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `jobPosting` | `JobPosting` | 공고 FK |
| `question` | `String` | 질문 문구. Template에서 복제 |
| `orderNo` | `Integer` | 질문 순서. Java 예약어 `order` 사용 금지 |
| `limitLength` | `Integer` | 답변 글자수 제한 |
| `template` | `QuestionTemplate` 후보 | 원본 template 추적용 |

구현 메모:

- `order`는 SQL/Java 맥락에서 혼동되므로 `orderNo`, `sortOrder`, `displayOrder` 중 하나를 사용한다.
- 공고 질문은 지원서 제출 이후 문구가 바뀌면 안 되므로 복제 저장이 적합하다.

## 4.8 Answer

지원서 자기소개/질문 답변이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `questionSet` | `QuestionSet` | 질문 FK |
| `answer` | `String` / `@Lob` | 답변 내용 |

구현 메모:

- 답변 길이는 `QuestionSet.limitLength` 기준으로 서버 검증한다.
- 질문당 답변은 보통 `Application + QuestionSet` unique가 필요하다.

## 4.9 ApplicationLanguage

어학 사항이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `languageType` | Enum/code | 언어 종류 |
| `testCode` | String/code | 시험 종류 |
| `score` | `String` | 점수/등급 |
| `examDate` | `LocalDate` | 시험일 |
| `expiryDate` | `LocalDate` | 만료일 |
| `issuingOrg` | `String` | 주관 기관 |

## 4.10 ApplicationAward

포상 사항이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `awardName` | `String` | 포상명 |
| `awardDescription` | `String` | 포상 설명 |
| `awardOrg` | `String` | 포상 기관 |
| `awardDate` | `LocalDate` | 포상 일자 |
| `awardType` | Enum/code | 교내/대외/사내 등 |

## 4.11 ApplicationMilitary

병역 사항이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK, 1:1 후보 |
| `militaryStatus` | Enum/code | 필/미필/면제/복무중 |
| `militaryType` | String/code | 군별 |
| `serviceStartDate` | `LocalDate` | 복무 시작일 |
| `serviceEndDate` | `LocalDate` | 복무 종료일 |
| `exemptionReason` | `String` | 면제 사유 |

구현 메모:

- 복무기간 계산은 레거시 서버 Endpoint가 있었으나 신규에서는 클라이언트 로직 대체 가능성이 있다.
- 서버에서는 날짜 유효성 검증만 담당하는 방향이 적합하다.

## 4.12 ApplicationCertificate

자격증 사항이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `certificateName` | `String` | 자격증명 |
| `issuingOrg` | `String` | 발급기관 |
| `certificateKey` | `String` | 자격증 번호 |
| `acquireDate` | `LocalDate` | 취득일 |

## 4.13 Education

학력 사항이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `schoolType` | Enum/code | 고등학교/전문대/대학교/대학원 |
| `degreeType` | Enum/code | master/doctor/integrated_md 등 대학원 학위 |
| `schoolName` | `String` | 학교명 |
| `schoolLocation` | `String` | 소재지 |
| `dayTime` | `boolean` | 주/야간 |
| `mainCampus` | `boolean` | 본/분교 |
| `majorName` | `String` | 전공 |
| `subMajorName` | `String` | 부전공 |
| `educationStatus` | Enum/code | 졸업/재학/휴학/중퇴/수료 |
| `admissionDate` | `LocalDate` | 입학일 |
| `graduationDate` | `LocalDate` | 졸업일 |
| `gpa` | `String` | 대표 학점 |
| `gpaScale` | `String` | 학점 기준 |
| `countryCode` | `String` | 국가코드 |
| `educationMode` | Enum/code | 일반대/사이버대 등 |

구현 메모:

- 학력 한 row가 고등학교/대학교/석사/박사 등을 모두 표현한다.
- 대학원은 `degreeType`으로 석사/박사/석박통합을 구분한다.
- 학교 master data(`School`)와 직접 FK를 맺을지, 이름을 snapshot으로 저장할지 결정이 필요하다.

## 4.14 EducationSemesterGrade

학기별 성적이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `education` | `Education` | 학력 FK |
| `academicYear` | `Integer` | 학년 |
| `semesterType` | Enum/code | 1학기/2학기/여름/겨울 등 |
| `gpa` | `String` | 평점 |
| `gpaScale` | `String` | 만점 기준 |
| `earnedCredits` | `Integer` | 이수 학점 |
| `gpaType` | Enum | 전체 학점/전공 학점 |
| `degreeType` | Enum | 학사/석사/박사 |

구현 메모:

- 동일 `Education` 아래에 전체/전공 학점이 모두 쌓일 수 있으므로 `gpaType`이 중요하다.
- 대학교/석사/박사 구분이 필요한 경우 `Education.degreeType`과 중복되지 않도록 설계한다.

## 4.15 ApplicationGap

공백기간이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `gapStartDate` | `LocalDate` | 공백 시작일 |
| `gapEndDate` | `LocalDate` | 공백 종료일 |
| `description` | `String` | 공백기간 설명 |

## 4.16 Career

경력 사항이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `careerStartAt` | `LocalDate` | 시작일 |
| `careerEndAt` | `LocalDate` | 종료일 |
| `companyName` | `String` | 회사명 |
| `current` | `boolean` | 재직중 여부 |
| `employmentType` | String/code | 정규직/계약직/인턴 등 |
| `jobTitle` | `String` | 직무 |
| `positionTitle` | `String` | 직급 |
| `leaveReason` | `String` | 퇴사 사유 |
| `responsibilities` | `String` / `@Lob` | 담당 업무 |

## 4.17 CommonCode

공통 코드다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `groupCode` | `String` | 코드 그룹 |
| `code` | `String` | 실제 코드 값 |
| `displayName` | `String` | 표시명 |
| `ascending` | `boolean` | 오름차순 정렬 여부 |
| `active` | `boolean` | 사용 여부 |
| `description` | `String` | 코드 설명 |

구현 메모:

- 코드 테이블은 강한 FK보다 application-level validation 중심으로 운영할 가능성이 높다.
- `groupCode + code` unique index를 고려한다.

## 4.18 ActivityLog

사용자 동선/감사 로그다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `actorUserId` | `String` 또는 `Long` | 행위자 user id |
| `actorRole` | `String` | 행위자 role |
| `actionType` | Code/Enum | `CREATE`, `UPDATE`, `DELETE`, `VIEW_PAGE`, `ACCESS_API` 등 |
| `targetType` | `String` | `PAGE`, `API`, table name 등 |
| `targetId` | `Long` 또는 `String` | 대상 pk/API endpoint/screen id |
| `userAgent` | `String` | 접속기기 |
| `ipAddress` | `String` 후보 | 접속 IP |
| `correlationId` | `String` 후보 | 서버 correlation id |
| `traceId` | `String` 후보 | OpenTelemetry trace id |

구현 메모:

- 금융권 감사/추적 관점에서 중요하다.
- AOP 또는 filter/interceptor 기반 자동 기록을 검토한다.

## 4.19 ApplicationFollowUpQuestion

면접/전형 중 지원자에게 추가 입력받는 질문이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `questionText` | `String` | 질문 |
| `answerText` | `String` / `@Lob` | 답변 |
| `openAt` | `LocalDateTime` | 공개 시간 |
| `dueAt` | `LocalDateTime` | 마감 시간 |

## 4.20 School

학교 master data다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `schoolName` | `String` | 학교명 |
| `address` | `String` | 주소 |
| `region` | `String` | 지역 |
| `schoolType` | String/code | 고등학교/대학교 등 |
| `educationMode` | String/code | OnCampus/Online 등 |
| `countryCode` | `String` | 국가코드 |

구현 메모:

- 지원서의 학교 입력을 자동완성/검색하는 용도다.
- 외부 API 또는 master import 방식은 별도 결정이 필요하다.

## 4.21 Stage

공고별 전형 단계다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `jobPosting` | `JobPosting` | 공고 FK |
| `stageType` | `StageType` | `DOCUMENT`, `INTERVIEW` 등 |
| `stageName` | `String` | 전형명 |
| `stageOrder` | `Integer` | 전형 순서 |
| `stageStatus` | `StageStatus` | `IN_PROGRESS`, `ANNOUNCING`, `CLOSED` |
| `finalStage` | `boolean` | 마지막 전형 여부 |

구현 메모:

- 공고 생성 시 서류전형/1차면접/최종면접 기본 stage 자동 생성 가능성을 고려한다.
- 결과 발표 상태와 전형 진행 상태를 구분할지 검토한다.

## 4.22 StageResult

지원서의 단계별 결과다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `stage` | `Stage` | 전형 단계 FK |
| `result` | `StageResultType` | `PASS`, `FAIL` 등 |
| `announcedAt` | `LocalDateTime` 후보 | 발표 시각 |

구현 메모:

- `Application + Stage` unique index를 고려한다.
- 엑셀 업로드로 대량 갱신되는 핵심 대상이다.

## 4.23 DocumentEvaluation

서류 평가다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `decision` | Enum | Pass/Fail |
| `score` | `Integer` | 점수 |
| `comment` | `String` | 코멘트 |

구현 메모:

- 서류 평가와 stage result는 역할이 다르다. 평가 점수/의견은 `DocumentEvaluation`, 최종 단계 결과는 `StageResult`로 분리한다.

## 4.24 Interview

면접 일정/조 정보다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK, 면접 조 번호로 활용 가능 |
| `jobPosting` | `JobPosting` | 공고 FK |
| `stage` | `Stage` 후보 | 몇 차 면접인지 연결 |
| `scheduledAt` | `LocalDateTime` | 면접 일정 |
| `interviewType` | `String` 또는 Enum | 인터뷰 형태 |
| `location` | `String` 후보 | 장소 |
| `roomName` | `String` 후보 | 면접실 |
| `groupName` | `String` 후보 | 조 이름 |

구현 메모:

- 원본 `interviewParticipantId`는 1:N 관계 표현으로 보기 어렵다. 구현은 `Interview` 1:N `InterviewParticipant`가 자연스럽다.

## 4.25 InterviewParticipant

면접 구성원이다. 지원자와 면접관을 모두 포함한다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `interview` | `Interview` | 면접 FK |
| `user` | `User` | 지원자 또는 면접관 FK |
| `application` | `Application` 후보 | 지원자 participant일 때 지원서 연결 |
| `role` | `InterviewParticipantRole` | `CANDIDATE`, `INTERVIEWER` |
| `sortOrder` | `Integer` 후보 | 표시 순서 |

구현 메모:

- 면접관 N명, 지원자 M명이면 평가 데이터는 N x M으로 생성될 수 있다.
- 면접관은 Employee/User 기반 role로 관리한다.

## 4.26 InterviewEvaluation

면접 평가다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `interview` | `Interview` | 면접 FK |
| `interviewer` | `User` 또는 `Employee` | 면접관 FK |
| `preInterviewMemo` | `String` | 면접 전 메모 |
| `presentationComment` | `String` | 발표평가 의견 |
| `presentationGrade` | `String` | 발표평가 등급 |
| `otherComment` | `String` | 기타평가 의견 |
| `otherGrade` | `String` | 기타평가 등급 |
| `overallComment` | `String` | 종합평가 의견 |
| `overallGrade` | `String` | 종합평가 등급 |
| `rankInSameGrade` | `String` | 동일등급 내 순위 |
| `specialNote` | `String` | 특이사항 |

구현 메모:

- `Interview + Application + Interviewer` unique index를 고려한다.
- 면접관별 임시저장/최종저장 상태가 필요할 수 있다.

## 4.27 MessageBatch

메시지 발송 묶음이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `jobPosting` | `JobPosting` | 공고 FK |
| `messageType` | Enum/code | SMS/EMAIL/ALIMTALK 등 |
| `messageTitle` | `String` | 메시지 제목 |
| `messageBody` | `String` / `@Lob` | 메시지 내용 |
| `sendReason` | `String` | 발송 사유 |
| `totalCount` | `Integer` | 전체 건수 |
| `successCount` | `Integer` | 성공 건수 |
| `failCount` | `Integer` | 실패 건수 |
| `status` | Enum 후보 | 대기/진행/완료/실패 |

## 4.28 MessageSendLog

개별 메시지 발송 이력이다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `messageBatch` | `MessageBatch` | 발송 묶음 FK |
| `recipientUser` | `User` 후보 | 수신자 |
| `deliveryStatus` | Enum/code | Success/Fail/Pending |
| `failReason` | `String` | 실패 사유 |
| `recipientContact` | `String` | 전화번호/이메일, 암호화 후보 |
| `recipientName` | `String` | 수신자 이름, 암호화 후보 |
| `sentAt` | `LocalDateTime` 후보 | 발송 시각 |

## 4.29 Attachment

첨부파일 정보다.

| 필드 | 타입 후보 | 설명 |
| --- | --- | --- |
| `id` | `Long` | PK |
| `application` | `Application` | 지원서 FK |
| `filePath` | `String` | 파일 경로 |
| `fileType` | `String` 또는 Enum | 사진/경력기술서 등 |
| `fileName` | `String` | 원본 파일명 |
| `storedFileName` | `String` 후보 | 저장 파일명 |
| `contentType` | `String` 후보 | MIME type |
| `fileSize` | `Long` 후보 | 파일 크기 |

구현 메모:

- 파일명 Content-Disposition 처리 시 한글/공백/브라우저 호환성을 고려한다.

## 5. 추천 Enum 후보

아래는 최초 구현 시 필요한 최소 Enum 후보이며, 실제 요구에 맞춰 조정한다.

| Enum | 값 후보 |
| --- | --- |
| `JobPostingStatus` | `DRAFT`, `OPEN`, `CLOSED`, `ARCHIVED` |
| `JobPostingType` | `OPEN_RECRUIT`, `REGULAR`, `INTERN`, `EXPERIENCED` |
| `ApplicationStatus` | `DRAFT`, `SUBMITTED`, `CANCELLED` |
| `StageType` | `DOCUMENT`, `FIRST_INTERVIEW`, `FINAL_INTERVIEW` |
| `StageStatus` | `IN_PROGRESS`, `ANNOUNCING`, `CLOSED` |
| `StageResultType` | `PASS`, `FAIL`, `PENDING` |
| `InterviewParticipantRole` | `CANDIDATE`, `INTERVIEWER` |
| `MessageType` | `SMS`, `EMAIL`, `ALIMTALK` |
| `DeliveryStatus` | `PENDING`, `SUCCESS`, `FAIL` |
| `EducationLevel` | `HIGH_SCHOOL`, `COLLEGE`, `UNIVERSITY`, `GRADUATE` |
| `DegreeType` | `BACHELOR`, `MASTER`, `DOCTOR`, `INTEGRATED_MD` |
| `GpaType` | `TOTAL`, `MAJOR` |

코드가 자주 바뀌거나 관리자가 수정해야 하는 값은 Enum 대신 `CommonCode`를 사용한다.

## 6. 초기 구현 vertical slice 제안

### Slice 1: JobPosting 관리

구성:

- `JobPosting`
- `JobPostingRepository`
- `JobPostingService`
- `JobPostingController`
- request/response DTO
- repository/service/controller test

API 후보:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/admin/job-postings` | 공고 목록 |
| GET | `/admin/job-postings/{jobPostingId}` | 공고 상세 |
| POST | `/admin/job-postings` | 공고 등록 |
| PUT 또는 PATCH | `/admin/job-postings/{jobPostingId}` | 공고 수정 |
| POST | `/admin/job-postings/{jobPostingId}/close` | 공고 마감 |

### Slice 2: JobPosition + ApplicationFormConfig

구성:

- 공고별 지원분야/응시구분/근무지
- 공고별 지원서 섹션 사용 여부

### Slice 3: Stage 기본

구성:

- 공고별 전형 단계
- 기본 stage 생성
- 전형 상태 변경

### Slice 4: Application 기본

구성:

- 지원서 생성
- 임시저장
- 최종제출
- 내 지원서 조회

### Slice 5: 지원서 세부 항목

구성:

- 학력
- 경력
- 자격증
- 어학
- 병역
- 포상
- 공백기간
- 첨부파일

## 7. 구현 시 관계 결정 가이드

### 7.1 양방향 연관관계는 신중히 사용

초기 구현은 단방향 N:1 중심이 낫다.

예:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "job_posting_id", nullable = false)
private JobPosting jobPosting;
```

`JobPosting`에서 `List<Application>`을 바로 들고 싶어도, 필요한 조회는 Repository query로 해결하는 방향을 우선한다.

### 7.2 Cascade는 기본 금지

채용 시스템은 개인정보/평가/전형결과가 얽히므로 cascade delete는 위험하다.

- 생성 편의 목적 cascade는 신중히 검토
- 삭제는 soft delete 또는 명시적 파기 service에서 처리
- 지원자 정보 파기 기능은 별도 정책 기반으로 구현

### 7.3 DTO에서 Entity 노출 금지

응답 DTO는 항상 primitive/id/string 중심으로 구성한다.

나쁜 예:

```java
public record ApplicationResponse(Application application) {}
```

좋은 예:

```java
public record ApplicationResponse(Long id, Long jobPostingId, String status) {
    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
            application.getId(),
            application.getJobPosting().getId(),
            application.getStatus().name()
        );
    }
}
```

## 8. 레거시 테이블 매핑 요약

| 신규 도메인 | 레거시 테이블/자료 |
| --- | --- |
| `JobPosting` | `TRMMAA100M00`, `TRMMAA200L00` |
| `User` / `Applicant` | `TRALAA100M00`, `TRALPA100M00` |
| `Interview` | `TRMMIS100L00`, `TRMMIS200L00` |
| `InterviewEvaluation` | `TRMMIS700L00`, `TRMMIS900L00`, `TRMMIT100L00` |
| `Stage` / `StageResult` | `TRMMCA100L00` |
| `Attachment` | `TRALFA200L00` |
| `MessageBatch` / `MessageSendLog` | `TRMMSD100L00`, `TRMMSD100M00`, `TRMMSD200M00`, `TRMMSD300M00` |
| `Career` | `TRALCA100L00` |
| `ActivityLog` | `TRALAA200L00`, `TRMMAA300L00` |
| `ApplicationLanguage` | `TRALLA100L00` |
| `ApplicationMilitary` | `TRALMA100L00` |
| `ApplicationAward` | `TRALPA200M00` |
| `ApplicationCertificate` | `TRALQA100L00` |
| `Education` | `TRALSA200L00` |
| `EducationSemesterGrade` | `TRALSA300L00`, `TRALSA400L00` |
| `ApplicationGap` | `TRALV1200M00` |
| `CommonCode` | `TRMMSA100D00`, `TRMMLA100M00` |
| `ApplicationFollowUpQuestion` | `TRMMIT600D00`, `TRMMIT500L00` |
| `School` | `TRMMSB100L00`, `TRMMSC100L00` |

레거시 테이블명은 migration/분석 참고용이며 신규 Entity/table 명명은 현재 프로젝트 컨벤션을 따른다.
