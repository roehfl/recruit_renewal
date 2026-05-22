# Phase 03j - JobPosting Domain Display & Position Metadata Expansion Design

## 1. 개요

Phase 03j는 채용공고(`JobPosting`)와 모집분야(`JobPosition`) 도메인을 지원자 화면 표시, 관리자 검색/정렬, 공고 노출 제어에 맞게 확장하기 위한 설계 전용 Phase다.

이번 Phase는 설계 산출물 작성만 수행한다. Java 소스, 테스트, DB migration, 설정 파일, API 구현은 변경하지 않는다.

## 2. 현재 구현 상태

현재 공고 도메인은 Phase 01a/01b에서 구현된 최소 vertical slice를 기준으로 한다.

| 영역 | 현재 상태 |
|---|---|
| 관리자 API | `GET /admin/job-postings`, `GET /admin/job-postings/{id}`, `POST /admin/job-postings`, `POST /admin/job-postings/{id}`, `POST /admin/job-postings/{id}/publish`, `POST /admin/job-postings/{id}/close` |
| 공개 API | `GET /job-postings`, `GET /job-postings/{id}` |
| `JobPosting` | `title`, `contentHtml`, `receptionStartDateTime`, `receptionEndDateTime`, `status`, `publishedAt`, `closedAt`, `jobPositions`, `applicationFormConfig` |
| `JobPosition` | `positionName`, `headcount`, `sortOrder` |
| 공개 목록 조회 | `JobPostingPublicListProjection`으로 `id`, `title`, 접수기간만 조회 |
| 공개 상세 조회 | `id + PUBLISHED` 조건과 `@EntityGraph`로 모집분야와 지원서 항목 설정 조회 |
| 접수 가능 여부 | `accepting` boolean을 응답에서 계산 |
| Application 연동 | `JobApplicationService`가 `JobPosting.status == PUBLISHED`와 접수기간 안쪽 여부로 생성/수정/제출/철회를 검증 |

현재 `EmploymentType` enum은 이미 존재한다.

```java
FULL_TIME, CONTRACT, INTERN, FREELANCE, PART_TIME, ETC
```

## 3. 문제점

### 3.1 `JobPosting`의 한계

현재 `JobPosting` 필드만으로는 다음 요구를 안정적으로 처리하기 어렵다.

| 요구 | 현재 한계 |
|---|---|
| 공고 목록 카드용 짧은 설명 | `contentHtml`만 있어 목록에 쓰기 어렵고 HTML 원문 노출 위험이 있다. |
| 공고 구분 표시 | 신입공채, 경력채용, 인턴채용, 수시채용 같은 분류 필드가 없다. |
| 지원자 화면 노출 제어 | `PUBLISHED`만으로 공개 여부를 판단해 운영 상태와 화면 노출이 결합되어 있다. |
| 노출 기간과 접수 기간 분리 | 접수 전 홍보, 접수 마감 후 결과 안내용 노출 같은 정책을 표현하기 어렵다. |
| 상단 고정/수동 정렬 | `createdAt desc` 중심 정렬만 가능하다. |
| 접수 상태 표시 | `accepting` boolean만 있어 접수예정/접수중/접수마감 표시가 부족하다. |

### 3.2 `JobPosition`의 한계

현재 `positionName`에 모든 표시 정보를 합쳐 넣는 방식은 다음 확장에 불리하다.

| 요구 | 현재 한계 |
|---|---|
| 지원구분 필터 | 신입/경력/신입경력 구분 컬럼이 없다. |
| 직군/직무 태그 | 직군과 세부 직무를 분리할 수 없다. |
| 근무지 필터 | 근무지가 문자열 안에 섞일 수밖에 없다. |
| 고용형태 표시 | 기존 `EmploymentType`을 활용할 위치가 없다. |
| 통계/검색 | `positionName` 문자열 파싱에 의존하게 된다. |

## 4. 설계 목표

1. 공고 운영 상태(`status`)와 지원자 화면 노출 여부(`visible`)를 분리한다.
2. 접수 기간(`reception*`)과 화면 노출 기간(`display*`)을 분리한다.
3. 접수 가능 여부는 저장 컬럼이 아니라 응답 파생값으로 제공한다.
4. 공개 목록 정렬을 `pinned`, 접수 상태 우선순위, `displayOrder`, 접수 종료일, 게시일 기준으로 명확히 한다.
5. `JobPosition.positionName`은 유지하고, 검색/필터 가능한 메타데이터를 별도 컬럼으로 추가한다.
6. Phase 01a/01b 관리자/공개 API와 Phase 03 Application 생성/제출 흐름을 깨지 않는 점진적 확장으로 설계한다.

## 5. 제외 범위

이번 Phase에서 설계하지 않거나 구현 대상으로 삼지 않는 범위는 다음과 같다.

| 범위 | 처리 |
|---|---|
| 알림/SMS/Email 발송 | 제외 |
| LDAP/사내 인증 연동 | 제외 |
| 면접 일정/평가 | 제외 |
| 전형 결과 도메인 변경 | 제외 |
| 첨부파일 업로드/다운로드/삭제/복구 | 제외 |
| 지원서 상세 섹션 도메인 변경 | 제외 |
| 지원서 항목별 필수 정책 확장 | 제외. `ApplicationFormConfig` 확장은 후속 Phase 후보로만 둔다. |
| 공통코드 관리자 화면 | 제외. `jobGroup`, `workLocation`은 장기적으로 공통코드 후보로만 기록한다. |
| 엑셀/PDF/통계 | 제외 |
| DB migration 파일 생성 | 제외 |
| Java/Test 구현 | 제외 |

## 6. `JobPosting` 확장 설계

### 6.1 필드 추가안

| 필드 | 타입 | nullable | 기본값 | API 노출 | 목적 |
|---|---|---:|---|---|---|
| `postingType` | `JobPostingType` enum | false 권장 | `PUBLIC_RECRUITMENT` | 관리자 목록/상세, 공개 목록/상세 | 공고 큰 분류 표시와 필터 후보 |
| `summary` | `String` | true | `null` | 관리자 목록/상세, 공개 목록/상세 | 목록 카드용 짧은 설명 |
| `displayStartDateTime` | `LocalDateTime` | true | `null` | 관리자 목록/상세 | 공개 화면 노출 시작 시각 |
| `displayEndDateTime` | `LocalDateTime` | true | `null` | 관리자 목록/상세 | 공개 화면 노출 종료 시각 |
| `visible` | `boolean` | false | `true` | 관리자 목록/상세 | 지원자 공개 화면 노출 여부 |
| `pinned` | `boolean` | false | `false` | 관리자 목록/상세, 공개 목록/상세 선택 노출 | 공개 목록 상단 고정 |
| `displayOrder` | `Integer` | false 권장 | `0` | 관리자 목록/상세 | 고정/일반 공고 내 수동 정렬값. 공개 API 응답에는 노출하지 않는다. |

### 6.2 상태와 노출의 분리

`status`와 `visible`은 역할이 다르다.

| 값 | 책임 |
|---|---|
| `JobPostingStatus` | 관리자 운영 상태. `DRAFT`, `PUBLISHED`, `CLOSED` |
| `visible` | 지원자 공개 화면 노출 여부 |
| `displayStartDateTime/displayEndDateTime` | 공개 화면 노출 가능 기간 |
| `receptionStartDateTime/receptionEndDateTime` | 지원서 생성/수정/제출/철회 가능 기간 |

공개 API의 기본 노출 조건은 아래 조합으로 판단한다.

```text
status = PUBLISHED
visible = true
displayStartDateTime is null or displayStartDateTime <= now
displayEndDateTime is null or displayEndDateTime >= now
```

접수마감 공고라도 `displayEndDateTime`이 남아 있으면 공개 목록에 노출할 수 있다. 다만 `accepting=false`, `receptionStatus=CLOSED`로 표현해야 한다.

### 6.3 기존 Application 흐름과의 호환성

`JobApplicationService`는 현재 `PUBLISHED`와 접수기간으로 지원서 command 가능 여부를 판단한다. Phase 03j 구현에서는 신규 지원서 생성과 기존 지원서 command를 분리한다.

| 흐름 | 유지/변경 |
|---|---|
| 신규 지원서 생성 | `status == PUBLISHED` + `ReceptionStatus.ACCEPTING` + `visible == true` + display 기간 안쪽 조건을 모두 만족해야 한다. 숨김 공고 ID를 알고 있어도 신규 유입은 차단한다. |
| 기존 지원서 수정/제출/철회 | 이미 작성 중인 지원서를 살리기 위해 기존처럼 `status == PUBLISHED` + 접수기간 안쪽 조건을 유지한다. `visible/display` 조건은 적용하지 않는다. |
| `visible=false` 공고 | 공개 목록/상세와 신규 지원서 생성에서는 숨긴다. 기존 DRAFT/SUBMITTED 지원서 command는 접수기간 기준으로만 처리한다. |
| display 기간 | 공개 조회와 신규 지원서 생성 조건으로 사용한다. 기존 지원서 command 가능 여부로는 사용하지 않는다. |
| reception 기간 | 지원서 command 가능 여부와 접수 상태 파생값 기준 |

비공개 링크형 공고를 허용해야 한다면 `visible=false`의 의미를 별도 enum 또는 접근 토큰 정책으로 재설계해야 한다. 이번 Phase의 기본 결정은 "화면에서 숨김"을 신규 지원서 생성 차단으로 해석한다.

## 7. `JobPosition` 확장 설계

### 7.1 필드 추가안

| 필드 | 타입 | nullable | 기본값/보정 | API 노출 | 목적 |
|---|---|---:|---|---|---|
| `applicationType` | `JobPositionApplicationType` enum | false 권장 | 기존 데이터는 `NEW_GRADUATE_OR_EXPERIENCED` 보정 후보 | 관리자/공개 모집분야 응답 | 신입/경력/신입경력 구분 |
| `jobGroup` | `String` | true로 시작 | 기존 데이터는 `null` 유지, 응답 null 허용/화면 미표시 | 관리자/공개 모집분야 응답 | 직군/분야 태그와 필터. 공통코드 확정 전 임시 문자열을 저장하지 않는다. |
| `jobTitle` | `String` | true | `null` | 관리자/공개 모집분야 응답 | 세부 직무명. 없으면 `positionName`으로 표시 가능 |
| `workLocation` | `String` | true로 시작 | 기존 데이터는 `null` 유지, 응답 null 허용/화면 미표시 | 관리자/공개 모집분야 응답 | 근무지 표시와 필터. 공통코드 확정 전 임시 문자열을 저장하지 않는다. |
| `employmentType` | `EmploymentType` enum | false 권장 | 기존 데이터는 `FULL_TIME` 또는 `ETC` 보정 후보 | 관리자/공개 모집분야 응답 | 고용형태 |
| `headcount` | `Integer` | false 유지 | 기존 정책 유지 | 관리자/공개 모집분야 응답 | 채용 인원 |
| `sortOrder` | `Integer` | false 유지 | 기존 정책 유지 | 관리자/공개 모집분야 응답 | 모집분야 표시 순서 |

### 7.2 `positionName` 유지 원칙

`positionName`은 제거하거나 rename하지 않는다.

| 필드 | 역할 |
|---|---|
| `positionName` | 화면 표시용 모집분야명. 기존 Application snapshot의 원천이므로 유지 |
| `applicationType` | 지원구분 필터/태그 |
| `jobGroup` | 직군 필터/태그 |
| `jobTitle` | 세부 직무명 |
| `workLocation` | 근무지 필터/태그 |
| `employmentType` | 고용형태 필터/태그 |

지원서 생성 시 저장하는 `jobPositionNameSnapshot`은 기존처럼 `positionName`을 사용한다. 직군/근무지/고용형태 snapshot이 필요해지는 것은 통계/출력 요구가 확정된 후 별도 Phase에서 검토한다.

### 7.3 채용 인원 표시 정책

Phase 03j 구현에서는 기존 `headcount @NotNull @Min(1)` 정책을 유지하는 것이 안전하다.

미정/비공개/00명 표시가 필요하면 후속 Phase에서 다음 중 하나를 별도로 설계한다.

| 대안 | 판단 |
|---|---|
| `headcount` nullable 허용 | 기존 validation과 DB not null 정책을 바꿔야 하므로 이번 Phase에서는 보류 |
| `headcountDisplayText` 추가 | 화면 표시 정책이 명확해지면 가장 안전한 후보 |
| `headcountPublicVisible` 추가 | 관리자 내부 인원과 공개 노출을 분리해야 할 때 후보 |

## 8. 신규/재사용 enum 설계

### 8.1 `JobPostingType` 신규 enum 후보

| 값 | 설명 |
|---|---|
| `PUBLIC_RECRUITMENT` | 공개채용/공채 |
| `EXPERIENCED_RECRUITMENT` | 경력채용 |
| `INTERN_RECRUITMENT` | 인턴채용 |
| `ROLLING_RECRUITMENT` | 수시채용 |

구현 시 `@Enumerated(EnumType.STRING)`으로 저장한다. enum 컬럼 길이는 향후 값 확장을 고려해 `varchar(50)` 이상을 권장한다.

`REGULAR` 같은 명칭은 사용하지 않는다. 채용/인사 도메인에서 `REGULAR`는 정규직으로 읽힐 수 있어 `EmploymentType.FULL_TIME`과 의미가 충돌한다. `JobPostingType` 값은 공고 유형임이 드러나도록 `*_RECRUITMENT` suffix를 붙인다.

### 8.2 `JobPositionApplicationType` 신규 enum 후보

| 값 | 설명 |
|---|---|
| `NEW_GRADUATE` | 신입 |
| `EXPERIENCED` | 경력 |
| `NEW_GRADUATE_OR_EXPERIENCED` | 신입/경력 |

지원구분이 운영 중 자주 바뀌거나 코드 관리 대상이 되면 장기적으로 `CommonCode` 전환을 검토한다. 다만 이번 Phase 구현에서는 공통코드 관리자 기능을 만들지 않는다.

### 8.3 `EmploymentType` 재사용

고용형태는 기존 `EmploymentType` enum을 재사용한다. 신규 enum을 만들지 않는다.

| 값 | 설명 후보 |
|---|---|
| `FULL_TIME` | 정규직 |
| `CONTRACT` | 계약직 |
| `INTERN` | 인턴 |
| `FREELANCE` | 프리랜서 |
| `PART_TIME` | 파트타임 |
| `ETC` | 기타 |

### 8.4 `ReceptionStatus` 신규 enum 후보

`ReceptionStatus`는 DB 컬럼이 아니라 응답용 파생값이다.

| 값 | 판정 |
|---|---|
| `UPCOMING` | `now < receptionStartDateTime` |
| `ACCEPTING` | `receptionStartDateTime <= now <= receptionEndDateTime` |
| `CLOSED` | `now > receptionEndDateTime` |

`JobPostingStatus.CLOSED`는 운영자가 공고를 마감한 상태이고, `ReceptionStatus.CLOSED`는 접수 종료일이 지난 시간 기반 상태다. 같은 이름의 `CLOSED`라도 책임이 다르다.

## 9. 접수 상태 파생값 설계

### 9.1 응답 정책

기존 공개 응답의 `accepting` boolean은 유지하고, `receptionStatus`를 추가한다.

| 필드 | 성격 | 계산 |
|---|---|---|
| `receptionStatus` | 시간 기반 표시 상태 | 접수 시작/종료일과 `now`로 계산 |
| `accepting` | 기존 호환용 접수기간 기반 boolean | `status == PUBLISHED`이고 `receptionStatus == ACCEPTING`. `visible/display` 조건은 포함하지 않는다. |

공개 API는 `status=PUBLISHED` 공고만 노출하므로 공개 응답에서 `accepting`은 현재처럼 접수기간 기준과 사실상 같다. 관리자 API에서는 `status=CLOSED` 공고도 볼 수 있으므로 `accepting=false`와 `receptionStatus=ACCEPTING`이 동시에 나올 수 있다. 이 경우 문구는 "운영상 마감, 접수기간상 접수중"처럼 프론트에서 구분 가능해야 한다.

`accepting`은 신규 지원서 생성 가능 여부를 완전히 표현하지 않는다. 예를 들어 관리자 응답에서는 `status=PUBLISHED`, `visible=false`, `receptionStatus=ACCEPTING`, `accepting=true`가 가능하지만, 신규 지원서 생성은 6.3의 service guard에 따라 차단된다.

이번 Phase에서는 `creatable`, `applicationCreatable`, `publiclyVisible` 같은 추가 boolean 응답 필드를 만들지 않는다. 신규 지원서 생성 가능 여부는 응답 필드가 아니라 `JobApplicationService`의 생성 guard에서 판단한다.

### 9.2 계산 위치

계산은 Service/DTO 변환 경계에서 수행한다.

- `Clock` 주입을 유지한다.
- Entity에 `now` 의존 계산을 넣지 않는다.
- `ReceptionStatus.from(start, end, now)` 같은 enum static factory를 둘 수 있다.

## 10. 공개 공고 필터링/정렬 설계

### 10.1 공개 목록/상세 필터

공개 목록과 상세는 같은 공개 조건을 적용한다.

```text
status = PUBLISHED
visible = true
(displayStartDateTime is null or displayStartDateTime <= now)
(displayEndDateTime is null or displayEndDateTime >= now)
```

공개 상세에서 조건을 만족하지 않는 공고는 존재하지 않는 공고와 동일하게 `JobPostingNotFoundException`으로 처리해 현재 숨김 정책을 유지한다.

### 10.2 공개 목록 정렬

권장 정렬은 다음 순서다.

1. `pinned desc`
2. `receptionStatusPriority asc`
3. `displayOrder asc`
4. `receptionEndDateTime asc`
5. `publishedAt desc`
6. `id desc`

`receptionStatusPriority`는 다음 순서로 둔다.

| ReceptionStatus | priority | 의미 |
|---|---:|---|
| `ACCEPTING` | 1 | 접수중 |
| `UPCOMING` | 2 | 접수예정 |
| `CLOSED` | 3 | 접수마감 |

접수마감 공고도 display 기간 안이면 공개 목록에 노출될 수 있으므로, `receptionEndDateTime asc`를 `receptionStatusPriority`보다 앞에 두면 이미 마감된 공고가 접수중 공고보다 위로 올라올 수 있다. 따라서 접수중, 접수예정, 접수마감 순서를 먼저 고정한다.

`displayOrder`는 non-null 기본값 `0`으로 설계한다. 기존 데이터 보정 전 null이 섞일 가능성이 있으면 query에서 `coalesce(displayOrder, 0)` 또는 Java 정렬에서 null-safe 처리를 사용한다.

Spring Data 메서드명만으로는 `receptionStatusPriority` 정렬을 표현하기 어렵다. 구현 Phase에서는 `@Query`의 `CASE WHEN` 정렬을 우선 검토한다. 공개 목록 규모가 작고 운영상 상한이 명확하다면 공개 조건 결과를 제한적으로 조회한 뒤 Service에서 `ReceptionStatus` 계산, 정렬, pagination을 수행하는 대안을 검토할 수 있다. 단순히 DB page를 먼저 가져온 뒤 Service에서 page 내부만 재정렬하는 방식은 전체 정렬을 깨므로 사용하지 않는다.

### 10.3 관리자 목록 정렬

관리자 목록은 운영 확인이 목적이므로 기존 `createdAt desc`를 유지하거나 다음 정렬을 선택할 수 있다.

1. `createdAt desc`
2. `id desc`

관리자 화면에서 공개 노출 순서 미리보기가 필요하면 별도 query parameter로 공개 정렬을 적용하는 확장을 후속 Phase로 분리한다.

## 11. 관리자 API Request/Response 변경안

### 11.1 `JobPostingCreateRequest`

| 필드 | validation 후보 | nullable | 기본값 처리 | 비고 |
|---|---|---:|---|---|
| `postingType` | `@NotNull` 후보 | 초기 호환을 위해 request nullable 허용 가능 | null이면 `PUBLIC_RECRUITMENT` | 기존 클라이언트 호환을 우선하면 service default 적용 |
| `summary` | `@Size(max = 500)` + HTML 태그 금지 검증 | true | 없음 | plain text만 허용. `<b>test</b>` 같은 HTML은 400으로 거절 |
| `displayStartDateTime` | 없음 | true | 없음 | null이면 시작 제한 없음 |
| `displayEndDateTime` | 없음 | true | 없음 | null이면 종료 제한 없음 |
| `visible` | 없음 | true wrapper `Boolean` 권장 | null이면 `true` | primitive boolean은 누락과 false 구분이 어려움 |
| `pinned` | 없음 | true wrapper `Boolean` 권장 | null이면 `false` | 상단 고정 |
| `displayOrder` | `@Min(0)` | true | null이면 `0` | 수동 정렬 |
| `jobPositions` | 기존 `@NotEmpty List<@Valid ...>` | false | 없음 | 확장된 `JobPositionRequest` 사용 |

`postingType`은 도메인상 필수지만, 기존 API 요청과 테스트가 깨지지 않도록 첫 구현에서는 nullable request + service default를 권장한다. 운영 UI가 새 필드를 보내도록 전환된 뒤 `@NotNull` 강화 여부를 결정한다.

### 11.2 `JobPostingUpdateRequest`

생성과 동일한 필드를 받는다. 단, 상태 변경은 계속 `publish`/`close` command API만 사용한다.

마감된 공고 수정 금지 정책은 유지한다.

### 11.3 `JobPositionRequest`

| 필드 | validation 후보 | nullable | 기본값 처리 | 비고 |
|---|---|---:|---|---|
| `positionName` | `@NotBlank`, `@Size(max = 100)` | false | 없음 | 기존 필드 유지 |
| `applicationType` | `@NotNull` 후보 | 초기 호환을 위해 nullable 허용 가능 | null이면 `NEW_GRADUATE_OR_EXPERIENCED` | 지원구분 |
| `jobGroup` | `@Size(max = 100)` | true | null 유지, 응답 null 허용/화면 미표시 | 직군. 공통코드 확정 전 임시 기본값을 저장하지 않음 |
| `jobTitle` | `@Size(max = 100)` | true | 없음 | 세부 직무 |
| `workLocation` | `@Size(max = 100)` | true | null 유지, 응답 null 허용/화면 미표시 | 근무지. 공통코드 확정 전 임시 기본값을 저장하지 않음 |
| `employmentType` | `@NotNull` 후보 | 초기 호환을 위해 nullable 허용 가능 | null이면 `FULL_TIME` 또는 `ETC` | 기존 enum 재사용 |
| `headcount` | `@NotNull @Min(1)` | false | 없음 | 기존 정책 유지 |
| `sortOrder` | `@NotNull @Min(0)` | false | 없음 | 같은 공고 내 중복 금지 권장 |

### 11.4 관리자 응답

#### `JobPostingListResponse`

| 필드 | 노출 |
|---|---|
| `id`, `title`, `postingType`, `summary` | 목록 표시 |
| `status`, `visible`, `pinned`, `displayOrder` | 운영 상태/노출 제어 |
| `displayStartDateTime`, `displayEndDateTime` | 노출 기간 |
| `receptionStartDateTime`, `receptionEndDateTime` | 접수 기간 |
| `receptionStatus`, `accepting` | 표시 상태와 기존 호환용 접수기간 boolean. `accepting`은 신규 지원서 생성 가능 여부가 아니다. |
| `publishedAt`, `closedAt`, `createdAt`, `updatedAt` | 운영 확인 |
| `positionCount`, `positions` 또는 `positionSummaries` | 모집분야 요약 |

#### `JobPostingDetailResponse`

목록 응답 필드 전체에 `contentHtml`, 확장된 `jobPositions`, `applicationFormConfig`를 포함한다.

#### `JobPositionResponse`

| 필드 |
|---|
| `id` |
| `positionName` |
| `applicationType` |
| `jobGroup` |
| `jobTitle` |
| `workLocation` |
| `employmentType` |
| `headcount` |
| `sortOrder` |

## 12. 지원자 공개 API Response 변경안

### 12.1 `JobPostingPublicListResponse`

지원자 공개 목록은 내부 운영 상태를 노출하지 않는다.

| 필드 | 노출 여부 |
|---|---|
| `id` | 노출 |
| `title` | 노출 |
| `postingType` | 노출 |
| `summary` | 노출 |
| `receptionStartDateTime`, `receptionEndDateTime` | 노출 |
| `receptionStatus` | 노출 |
| `accepting` | 노출. 기존 호환 유지. 공개 API에서는 공개 조건을 통과한 공고만 내려가지만, 필드 의미 자체는 접수기간 기반 boolean이다. |
| `pinned` | 선택 노출 가능. 상단 고정 뱃지를 표시할 필요가 있으면 포함한다. |
| `displayOrder` | 비노출. 내부 정렬용 값이므로 공개 API 계약에 포함하지 않는다. |
| `positions` | 노출 권장. 단, 목록 성능을 위해 요약 필드만 포함 |
| `status`, `visible`, `displayStartDateTime`, `displayEndDateTime` | 비노출 권장 |
| `contentHtml` | 목록에서는 비노출 |

### 12.2 `JobPostingPublicDetailResponse`

공개 상세는 목록 필드 전체에 `contentHtml`, 확장된 `jobPositions`, `applicationFormConfig`를 포함한다.

공개 상세에도 `status`, `visible`, 내부 노출 기간은 노출하지 않는다.

### 12.3 `JobPositionPublicResponse`

관리자 모집분야 응답과 동일한 분류 필드를 제공하되 내부 관리용 필드는 포함하지 않는다.

| 필드 |
|---|
| `id` |
| `positionName` |
| `applicationType` |
| `jobGroup` |
| `jobTitle` |
| `workLocation` |
| `employmentType` |
| `headcount` |
| `sortOrder` |

## 13. Repository/조회 전략

### 13.1 공개 목록

권장 방식은 기존 Spring Data JPA 안에서 해결한다.

1. `JobPostingPublicListProjection` 또는 custom `@Query` projection을 확장해 공고 scalar 필드를 조회한다.
2. 목록 페이지의 posting id 목록으로 `JobPositionRepository`에서 모집분야를 별도 일괄 조회한다.
3. Service에서 `jobPostingId` 기준으로 grouping해 list response를 조립한다.

이 방식은 QueryDSL 도입 없이 N+1을 피할 수 있고, 기존 projection 기반 공개 목록 구조를 크게 바꾸지 않는다.

후보 repository 메서드:

```java
Page<JobPostingPublicListProjection> findPublicList(...);
List<JobPosition> findByJobPostingIdInOrderByJobPostingIdAscSortOrderAsc(Collection<Long> jobPostingIds);
```

`ReceptionStatus` 기반 정렬은 `now`와 접수기간을 함께 봐야 하므로 repository 설계에서 별도로 다뤄야 한다. 우선안은 JPQL `@Query`에서 `CASE WHEN :now between ... THEN 1 ...` 형태로 priority를 계산해 정렬하는 방식이다. Service 정렬을 선택할 경우에는 page를 가져온 뒤 page 내부만 정렬하지 말고, 정렬 대상 전체 또는 충분히 제한된 결과를 조회한 뒤 정렬과 pagination을 같은 계층에서 수행해야 한다.

### 13.2 공개 상세

공개 상세는 기존처럼 `@EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})`를 유지하되 공개 조건에 `visible`과 display 기간을 추가한다.

### 13.3 관리자 목록

관리자 목록은 기존처럼 pageable entity 조회를 유지할 수 있다. 모집분야 요약을 포함한다면 공개 목록과 같은 두 단계 조회 전략을 사용한다.

### 13.4 QueryDSL/Specification

이번 구현 Phase에서 QueryDSL을 새로 도입하지 않는다. 관리자 검색 조건이 크게 늘어나는 별도 Phase에서 Specification 또는 QueryDSL 도입 여부를 재검토한다.

## 14. Validation 정책

### 14.1 `JobPosting`

| 규칙 | 정책 |
|---|---|
| `title` | 필수, 공백 금지 |
| `contentHtml` | 필수, 공백 금지 |
| `postingType` | 도메인 필수. 초기 request 호환을 위해 service default 가능 |
| `summary` | nullable 허용, 최대 500자 후보, HTML 태그 입력은 400으로 거절. sanitize 저장보다 reject를 기본안으로 한다. |
| 접수 기간 | `receptionStartDateTime < receptionEndDateTime` 유지 |
| 노출 기간 | 둘 다 있으면 `displayStartDateTime <= displayEndDateTime` |
| 노출 종료와 접수 종료 관계 | `displayEndDateTime`이 접수 종료보다 먼저 끝나는 것을 허용한다. 조기 노출 중단이 가능해야 한다. |
| `visible` | null request면 `true` |
| `pinned` | null request면 `false` |
| `displayOrder` | null request면 `0`, 값은 0 이상 |
| `CLOSED` 수정 | 기존처럼 일반 수정 금지 유지 |

### 14.2 `JobPosition`

| 규칙 | 정책 |
|---|---|
| 모집분야 수 | 공고당 최소 1개 유지 |
| `positionName` | 필수, 공백 금지 |
| `applicationType` | 도메인 필수. 초기 request 호환 default 가능 |
| `jobGroup` | 초기 구현에서는 nullable 허용, 응답은 null 허용/화면 미표시. 공통코드/운영값 확정 후 필수 전환 검토 |
| `jobTitle` | nullable |
| `workLocation` | 초기 구현에서는 nullable 허용, 응답은 null 허용/화면 미표시. 공통코드/운영값 확정 후 필수 전환 검토 |
| `employmentType` | 기존 `EmploymentType` 재사용, 도메인 필수 후보 |
| `headcount` | nullable 허용하지 않음, `@Min(1)` 유지 |
| `sortOrder` | 필수, 0 이상 |
| `sortOrder` 중복 | 같은 공고 요청 안에서 중복 금지 권장 |

`sortOrder` 중복 검증은 생성/수정 request를 entity로 변환하기 전에 Service에서 수행한다.

## 15. Migration/호환성 고려사항

### 15.1 추가 컬럼 후보

| 테이블 | 컬럼 | 타입 후보 | nullable/default |
|---|---|---|---|
| `job_posting` | `posting_type` | `varchar(50)` | not null, default/backfill `PUBLIC_RECRUITMENT` |
| `job_posting` | `summary` | `varchar(500)` | nullable |
| `job_posting` | `display_start_date_time` | `timestamp` | nullable |
| `job_posting` | `display_end_date_time` | `timestamp` | nullable |
| `job_posting` | `visible` | boolean | not null, default true |
| `job_posting` | `pinned` | boolean | not null, default false |
| `job_posting` | `display_order` | integer | not null, default 0 |
| `job_position` | `application_type` | `varchar(50)` | not null, default/backfill `NEW_GRADUATE_OR_EXPERIENCED` |
| `job_position` | `job_group` | `varchar(100)` | nullable로 시작 |
| `job_position` | `job_title` | `varchar(100)` | nullable |
| `job_position` | `work_location` | `varchar(100)` | nullable로 시작 |
| `job_position` | `employment_type` | `varchar(50)` | not null, default/backfill `FULL_TIME` 또는 `ETC` |

### 15.2 기존 데이터 보정 전략

기존 공고/모집분야 데이터가 있는 환경에서는 다음 보정이 필요하다.

| 필드 | 보정 후보 |
|---|---|
| `postingType` | `PUBLIC_RECRUITMENT` |
| `visible` | `true` |
| `pinned` | `false` |
| `displayOrder` | `0` |
| `displayStartDateTime/displayEndDateTime` | `null` |
| `applicationType` | `NEW_GRADUATE_OR_EXPERIENCED` |
| `jobGroup` | `null` 유지. 응답은 null을 허용하고 화면에서 미표시하며, 운영 코드 확정 후 수동 보정 |
| `workLocation` | `null` 유지. 응답은 null을 허용하고 화면에서 미표시하며, 운영 코드 확정 후 수동 보정 |
| `employmentType` | `FULL_TIME` 또는 `ETC` |

### 15.3 운영 DB 주의사항

- enum은 `EnumType.STRING`을 유지하고 컬럼 길이는 50 이상으로 둔다.
- boolean은 DB default와 Java factory default를 함께 둔다.
- H2 테스트에서는 Java default만 믿지 말고 request default/service default 케이스를 테스트한다.
- 실제 migration 체계가 확정되지 않았으므로 구현 Phase에서는 DDL 자동 생성 환경과 운영 수동 DDL을 구분해 문서화해야 한다.
- 기존 API 테스트는 응답 필드 추가로 보강한다. request 필수 필드 강화는 기존 테스트/클라이언트 전환 계획과 함께 진행한다.

## 16. 테스트 계획

### 16.1 Service 테스트 후보

| 테스트 |
|---|
| 공고 생성 시 `postingType`, `summary`, display 기간, `visible`, `pinned`, `displayOrder` 저장 |
| 공고 생성 요청에서 확장 필드가 null이면 기본값 적용 |
| `summary`에 HTML 태그가 포함되면 생성/수정 실패 |
| 공고 수정 시 확장 필드 변경 |
| `displayStartDateTime > displayEndDateTime`이면 실패 |
| 기존 접수 기간 validation 유지 |
| 마감된 공고 일반 수정 금지 유지 |
| 모집분야 확장 필드 저장 |
| 모집분야 확장 필드 null 기본값 적용 |
| 같은 공고 request 안에서 `sortOrder` 중복이면 실패 |
| 공개 목록에서 `visible=false` 제외 |
| 공개 목록에서 display 기간 밖 공고 제외 |
| 공개 목록에서 접수마감 공고라도 display 기간 안이면 노출 |
| 공개 목록 정렬: `pinned desc`, `receptionStatusPriority asc`, `displayOrder asc`, `receptionEndDateTime asc`, `publishedAt desc`, `id desc` |
| `receptionStatus`가 `UPCOMING`, `ACCEPTING`, `CLOSED`로 계산 |
| `accepting`은 기존 공개 응답 호환을 유지 |
| 신규 지원서 생성은 `visible=false` 공고에서 실패 |
| 신규 지원서 생성은 display 기간 밖 공고에서 실패 |
| 기존 지원서 수정/제출/철회는 `visible/display` 조건 변경과 무관하게 기존 접수기간 정책을 유지 |
| `jobGroup`, `workLocation`이 null이면 공개 화면에서 미표시 정책을 유지 |

### 16.2 Controller 테스트 후보

| 테스트 |
|---|
| 관리자 생성 request 확장 필드 binding |
| 관리자 상세 response 확장 필드 포함 |
| 관리자 목록 response 확장 필드 포함 |
| 지원자 공개 목록 response 확장 필드 포함 |
| 지원자 공개 상세 response 확장 필드 포함 |
| `summary` 길이 초과 시 400 |
| `summary` HTML 태그 포함 시 400 |
| display 기간 역전 시 400 |
| 모집분야 `sortOrder` 중복 시 400 |
| 공개 상세에서 `visible=false` 공고는 404 |
| 공개 상세에서 display 기간 밖 공고는 404 |

## 17. 다음 구현 Phase 분리안

### Phase 03j-1: Entity/DTO/Service 확장

- `JobPostingType`, `JobPositionApplicationType`, `ReceptionStatus` enum 추가
- `JobPosting`, `JobPosition` 필드 추가
- 관리자 request/response 확장
- 기본값 처리와 validation 구현
- 관리자 service/controller 테스트 보강
- 공개 목록 필터, 공개 목록 정렬, 공개 response 변경은 Phase 03j-1에서 구현하지 않는다.
- `ReceptionStatus` enum을 추가하더라도 공개 API 적용은 Phase 03j-2에서만 진행한다.
- 03j-1에서 공개 API 파일을 수정해야 하는 경우는 컴파일 호환 보정에 한정하고, 공개 노출 정책과 응답 계약은 기존 상태를 유지한다.

### Phase 03j-2: 공개 조회 필터/정렬 확장

- 공개 목록 projection 확장
- visible/display 기간 필터 적용
- 공개 목록 정렬 변경. `ReceptionStatus` priority 정렬은 JPQL `CASE WHEN` 또는 전체 결과 Service 정렬 + pagination 방식으로 구현
- 공개 목록 positions 요약 일괄 조회
- 공개 response에 `receptionStatus`와 확장 모집분야 추가
- 공개 service/controller 테스트 보강

### Phase 03j-3: 운영 검색/필터 보강 후보

- 관리자 목록 검색 조건 추가
- `postingType`, `visible`, `pinned`, 접수 상태 필터 후보
- `jobGroup`, `workLocation`, `employmentType` 공개 필터 후보
- Specification/QueryDSL 도입 여부 재검토

## 18. 최종 결정 요약

| 결정 | 내용 |
|---|---|
| 공고 상태/노출 분리 | `status`는 운영 상태, `visible`과 display 기간은 공개 화면 노출 조건으로 사용 |
| 신규 지원서 생성 가드 | 숨김 공고 ID 직접 호출을 막기 위해 신규 생성에는 `visible/display` 조건을 적용하고, 기존 지원서 수정/제출/철회에는 적용하지 않음 |
| 접수 상태 | `ReceptionStatus`는 저장하지 않고 응답에서 파생 |
| 기존 `accepting` 유지 | 프론트 호환을 위해 `accepting`을 유지하고 `receptionStatus`를 추가. `accepting`은 `status=PUBLISHED + 접수기간` 기준이며 `visible/display`를 포함하지 않는다. |
| 생성 가능 여부 응답 | `creatable`, `applicationCreatable`, `publiclyVisible` 같은 boolean은 이번 Phase에서 추가하지 않고, 신규 지원 가능 여부는 Service guard에서만 판단 |
| 모집분야 확장 | `positionName`은 유지하고 지원구분/직군/직무/근무지/고용형태를 별도 필드로 분리 |
| 고용형태 | 기존 `EmploymentType` enum 재사용 |
| 공개 조회 | `PUBLISHED + visible + display 기간` 조건 적용, 접수마감 공고도 display 기간 안이면 노출 가능하되 접수중/접수예정/접수마감 순으로 정렬 |
| 공개 API 노출 | `pinned`은 선택 노출 가능, `displayOrder`는 내부 정렬값이므로 공개 응답에서 제외 |
| 조회 전략 | QueryDSL 신규 도입 없이 projection 확장 + positions 일괄 조회로 N+1 회피. 접수 상태 정렬은 JPQL CASE 또는 전체 결과 Service 정렬로 별도 처리 |
| API 호환성 | 필수 도메인 필드는 추가하되 첫 구현에서는 request null default를 허용해 기존 테스트/클라이언트 파손을 줄임 |
| Phase 경계 | Phase 03j-1은 관리자 중심 Entity/DTO/Service 확장까지만 진행하고, 공개 목록 필터/정렬/공개 response 변경은 Phase 03j-2에서만 구현 |

## 변경 파일

이번 설계 Phase에서 생성/수정한 문서는 다음과 같다.

| 파일 | 상태 |
|---|---|
| `docs/codex/design/phase-03j-job-posting-domain-expansion-design.md` | 신규 |
| `docs/codex/reports/phase-03j-job-posting-domain-expansion-design.html` | 신규 |
| `docs/codex/07-implementation-history.md` | 설계 완료 이력 추가 |

## API 목록

이번 Phase에서는 runtime API를 구현하지 않는다. 다음 구현 Phase에서 기존 API의 응답/요청 필드를 확장한다.

| Method | Path | Phase 03j 영향 |
|---|---|---|
| `GET` | `/admin/job-postings` | 관리자 목록 응답 확장 |
| `GET` | `/admin/job-postings/{id}` | 관리자 상세 응답 확장 |
| `POST` | `/admin/job-postings` | 관리자 생성 request 확장 |
| `POST` | `/admin/job-postings/{id}` | 관리자 수정 request 확장 |
| `GET` | `/job-postings` | 공개 목록 필터/정렬 및 응답 확장 |
| `GET` | `/job-postings/{id}` | 공개 상세 필터 및 응답 확장 |

## 테스트 명령과 결과

| 항목 | 내용 |
|---|---|
| 테스트 명령 | 실행하지 않음 |
| 결과 | 문서 전용 설계 Phase라 Java 소스, 테스트, 설정, schema, runtime API를 변경하지 않았다. 다음 구현 Phase에서 대상 service/controller 테스트를 실행한다. |

## 남은 이슈

- `postingType`, `applicationType`, `jobGroup`, `workLocation`의 실제 운영 코드 값은 화면/운영 정책 확정 후 조정할 수 있다.
- 명시적 DB migration 체계가 없으므로 구현 Phase에서 H2 자동 DDL과 운영 DB DDL 반영 방식을 분리해야 한다.
- 공개 목록에 모집분야 전체를 내려줄지, 요약만 내려줄지는 프론트 카드 요구와 성능 기준을 함께 확인해야 한다.
- 공통코드 관리자 기능은 이번 Phase 범위 밖이며, 도입 시 별도 설계가 필요하다.
