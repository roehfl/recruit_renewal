# Phase 03j 설계 지시문 — JobPosting Domain Display & Position Metadata Expansion

## 0. 작업 모드

- 작업 유형: **설계 문서 작성 전용**
- 소스 코드 구현 금지
- 테스트 코드 구현 금지
- Entity/DTO/Repository/Service/Controller 수정 금지
- DB migration 파일 생성 금지
- 이번 작업은 다음 구현 Phase에서 사용할 **설계 산출물**을 만드는 것이 목적이다.

## 1. 목적

현재 채용공고(`JobPosting`)와 모집분야(`JobPosition`) 도메인은 최소 CRUD 수준으로만 구성되어 있어, 지원자 화면과 관리자 공고 관리 화면에서 필요한 표시/검색/정렬/분류 메타데이터를 충분히 제공하지 못한다.

이번 Phase의 목적은 다음 구현을 위한 설계를 작성하는 것이다.

1. `JobPosting` 도메인에 공고 노출, 목록 표시, 접수 상태 표시, 정렬에 필요한 컬럼을 보강한다.
2. `JobPosition` 도메인에 지원구분, 직군/직무, 근무지, 고용형태 등 모집분야 메타데이터를 보강한다.
3. 관리자 공고 등록/수정/상세/목록 API와 지원자 공개 공고 목록/상세 API의 응답 확장 방향을 정한다.
4. 기존 Phase 01a/01b 구현과 Phase 03 지원서 흐름을 깨지 않는 점진적 확장안을 제시한다.
5. 구현 범위와 제외 범위를 명확히 분리해 다음 구현 Phase가 과확장되지 않게 한다.

## 2. 반드시 참고할 기존 문서

아래 문서를 먼저 읽고 현재 설계/구현 흐름을 파악하라.

- `AGENTS.md`
- `docs/codex/01-project-context.md`
- `docs/codex/02-domain-design.md`
- `docs/codex/06-implementation-roadmap.md`
- `docs/codex/07-implementation-history.md`
- `docs/codex/design/phase-03-application-design.md`
- `docs/codex/implementation/phase-01a-job-posting.md`
- `docs/codex/implementation/phase-01b-job-posting-public-read.md`
- `docs/codex/reports/phase-01a-job-posting.html`
- `docs/codex/reports/phase-01b-job-posting-public-read.html`

## 3. 반드시 참고할 기존 소스

아래 파일을 기준으로 현재 구현 상태를 확인하라.

### Domain

- `src/main/java/com/shinyoung/recruit/domain/entity/JobPosting.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/JobPosition.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationFormConfig.java`

### Enumeration

- `src/main/java/com/shinyoung/recruit/enumeration/JobPostingStatus.java`
- `src/main/java/com/shinyoung/recruit/enumeration/EmploymentType.java`

### Repository

- `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingPublicListProjection.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobPositionRepository.java`

### Service

- `src/main/java/com/shinyoung/recruit/service/JobPostingService.java`
- `src/main/java/com/shinyoung/recruit/service/JobPostingPublicService.java`

### Controller

- `src/main/java/com/shinyoung/recruit/controller/JobPostingController.java`
- `src/main/java/com/shinyoung/recruit/controller/JobPostingPublicController.java`

### Request DTO

- `src/main/java/com/shinyoung/recruit/dto/request/JobPostingCreateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPostingUpdateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPositionRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/ApplicationFormConfigRequest.java`

### Response DTO

- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingListResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicListResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPositionResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPositionPublicResponse.java`

### Test

- `src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java`
- `src/test/java/com/shinyoung/recruit/service/JobPostingPublicServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/JobPostingControllerTest.java`
- `src/test/java/com/shinyoung/recruit/controller/JobPostingPublicControllerTest.java`

파일이 없거나 이름이 다른 경우, 실제 프로젝트 구조를 기준으로 가장 가까운 파일을 찾아 확인하라.

## 4. 현재 문제 인식

설계 문서에는 현재 문제를 반드시 명시하라.

### 4.1 `JobPosting`의 현재 한계

현재 `JobPosting`은 대략 다음 필드 중심이다.

- `title`
- `contentHtml`
- `receptionStartDateTime`
- `receptionEndDateTime`
- `status`
- `publishedAt`
- `closedAt`
- `jobPositions`
- `applicationFormConfig`

이 구조만으로는 다음 화면 요구사항을 안정적으로 처리하기 어렵다.

- 공고 목록 카드용 짧은 설명
- 공고 구분 표시
- 지원자 화면 노출 여부 제어
- 노출 시작/종료 기간과 접수 시작/종료 기간의 분리
- 상단 고정 공고
- 표시 순서
- 접수예정/접수중/접수마감 표시
- 공개 목록 정렬 기준

### 4.2 `JobPosition`의 현재 한계

현재 `JobPosition`은 대략 다음 필드 중심이다.

- `positionName`
- `sortOrder`

이 구조만으로는 다음 정보를 분리해서 표현하기 어렵다.

- 지원구분
- 직군
- 세부 직무
- 근무지
- 고용형태
- 채용 인원 표시 정책

`positionName` 하나에 모든 값을 문자열로 합쳐 넣는 방식은 검색, 필터, 화면 태그, 통계 확장에 불리하므로 이번 설계에서 컬럼 분리 방향을 제시하라.

## 5. 설계 범위

이번 Phase에서 설계해야 하는 범위는 아래로 제한한다.

### 5.1 포함 범위

- `JobPosting` 추가 필드 설계
- `JobPosition` 추가 필드 설계
- 필요한 신규 enum 설계
- 기존 `EmploymentType` 재사용 여부 판단
- 관리자 공고 생성/수정 Request 확장 설계
- 관리자 공고 목록/상세 Response 확장 설계
- 지원자 공개 공고 목록/상세 Response 확장 설계
- 공개 목록 필터링 조건 설계
- 공개 목록 정렬 조건 설계
- 접수 상태 파생값 설계
- validation 정책 설계
- 기존 데이터 호환성 및 migration 고려사항 설계
- 구현 Phase에서 작성해야 할 테스트 항목 설계
- 사람이 보는 HTML 현황 문서 작성

### 5.2 제외 범위

아래 항목은 이번 설계에서 구현 대상으로 다루지 말고, 별도 미구현 범위로도 길게 확장하지 말라.

- 사용자 알림 발송 연동
- 사내 인증 연동
- 면접 일정/면접 평가 도메인
- 전형 결과 도메인 변경
- 첨부파일 업로드/다운로드/삭제/복구 기능 변경
- 지원서 상세 섹션 도메인 변경
- 지원서 항목별 필수 여부 정책 확장
- 공통코드 관리자 화면
- 엑셀 다운로드/업로드
- 통계 화면
- PDF 출력

단, 공통코드와 지원서 필수 여부 정책은 향후 Phase 후보로 짧게만 언급할 수 있다. 이번 설계의 구현 대상처럼 작성하지 마라.

## 6. 권장 도메인 설계 방향

아래 방향을 기본안으로 삼되, 기존 코드와 충돌하는 부분이 있으면 근거를 남기고 조정하라.

### 6.1 `JobPosting` 추가 필드 후보

설계 문서에는 각 필드의 목적, 타입, nullable 여부, 기본값, API 노출 여부를 표로 작성하라.

권장 후보는 다음과 같다.

| 필드 | 권장 타입 | 기본 방향 |
|---|---|---|
| `postingType` | 신규 enum 또는 String | 공고의 큰 분류. 예: 신입공채, 경력채용, 인턴채용, 수시채용 등 |
| `summary` | String | 지원자 목록/카드용 짧은 설명. 길이 제한 필요 |
| `displayStartDateTime` | `LocalDateTime` nullable | 화면 노출 시작일시. null이면 별도 제한 없음 |
| `displayEndDateTime` | `LocalDateTime` nullable | 화면 노출 종료일시. null이면 별도 제한 없음 |
| `visible` | boolean | 지원자 공개 화면 노출 여부. 기본값 true 권장 |
| `pinned` | boolean | 지원자 목록 상단 고정 여부. 기본값 false 권장 |
| `displayOrder` | Integer | 고정/일반 공고 내 수동 정렬값. 기본값 0 권장 |

주의:

- `status`와 `visible`을 혼동하지 마라.
  - `status`: 관리자 운영 상태. `DRAFT`, `PUBLISHED`, `CLOSED`.
  - `visible`: 지원자 공개 화면 노출 여부.
- `receptionStartDateTime/receptionEndDateTime`과 `displayStartDateTime/displayEndDateTime`을 혼동하지 마라.
  - reception: 지원 접수 가능 기간.
  - display: 화면 노출 가능 기간.
- 접수 상태는 DB 컬럼으로 저장하지 말고 응답에서 파생하는 방향을 우선 검토하라.

### 6.2 `JobPosition` 추가 필드 후보

설계 문서에는 각 필드의 목적, 타입, nullable 여부, 기본값, API 노출 여부를 표로 작성하라.

권장 후보는 다음과 같다.

| 필드 | 권장 타입 | 기본 방향 |
|---|---|---|
| `applicationType` | 신규 enum 또는 String | 해당 모집분야의 지원구분. 예: 신입, 경력, 신입/경력 |
| `jobGroup` | String | 직군. 예: IT, 영업, 관리, 리서치 등 |
| `jobTitle` | String nullable | 세부 직무명. 필요 없으면 `positionName`으로 대체 가능 |
| `workLocation` | String | 근무지. 예: 서울, 부산 등 |
| `employmentType` | 기존 `EmploymentType` 재사용 우선 | 고용형태. 예: 정규직, 계약직, 인턴 등 |
| `headcount` | Integer | 기존 필드 유지. nullable 허용 여부를 설계에서 판단 |
| `sortOrder` | Integer | 기존 필드 유지 |

주의:

- 기존 `positionName`은 제거하거나 rename하지 말고 유지하는 방향을 우선 검토하라.
- `positionName`은 화면 표시용 모집분야명으로 유지하고, 분류/필터 가능한 값은 별도 필드로 분리하는 방향을 권장한다.
- 이미 `EmploymentType` enum이 존재하므로, 고용형태에는 신규 enum을 만들기 전에 재사용 가능성을 먼저 검토하라.
- `jobGroup`, `workLocation`은 장기적으로 공통코드 후보지만, 이번 Phase에서 공통코드 관리 기능을 만들지는 않는다.

## 7. 접수 상태 파생값 설계

지원자 공개 목록/상세와 관리자 목록/상세에서 사용할 수 있는 접수 상태 파생값을 설계하라.

권장 enum 예시:

```java
public enum ReceptionStatus {
    UPCOMING,
    ACCEPTING,
    CLOSED
}
```

판정 기준 예시:

- `now < receptionStartDateTime` → `UPCOMING`
- `receptionStartDateTime <= now <= receptionEndDateTime` → `ACCEPTING`
- `now > receptionEndDateTime` → `CLOSED`

주의:

- `JobPostingStatus.CLOSED`와 `ReceptionStatus.CLOSED`의 의미 차이를 명확히 설명하라.
- `ReceptionStatus`는 저장 컬럼이 아니라 응답용 파생값으로 설계하는 방향을 우선 검토하라.
- 기존 `accepting` boolean 응답은 유지할지, `receptionStatus`로 대체할지, 둘 다 제공할지 판단하라.
- 프론트 호환성을 고려하면 당장은 `accepting`을 유지하고 `receptionStatus`를 추가하는 방식이 안전하다.

## 8. 공개 공고 필터링 설계

지원자 공개 공고 목록/상세 조회 조건을 설계하라.

권장 공개 조건:

- `status = PUBLISHED`
- `visible = true`
- `displayStartDateTime IS NULL OR displayStartDateTime <= now`
- `displayEndDateTime IS NULL OR displayEndDateTime >= now`

주의:

- 접수마감 공고라도 display 기간이 남아 있으면 목록에 노출할 수 있다.
- 접수 가능 여부는 `receptionStatus` 또는 `accepting`으로 표현하고, 공개 여부와 분리하라.
- 공개 상세 조회도 목록과 동일한 공개 조건을 적용하는 방향을 우선 검토하라.

## 9. 공개 공고 정렬 설계

지원자 공개 공고 목록 정렬 기준을 설계하라.

권장 정렬:

1. `pinned` desc
2. `displayOrder` asc
3. `receptionEndDateTime` asc
4. `createdAt` desc 또는 `publishedAt` desc

주의:

- 정렬 기준은 deterministic 해야 한다.
- `displayOrder`가 null 가능이면 null 처리 기준을 명시하라.
- 관리자 목록과 지원자 공개 목록의 정렬 기준은 다를 수 있다.

## 10. Request DTO 설계

관리자 공고 생성/수정 Request 확장안을 설계하라.

대상:

- `JobPostingCreateRequest`
- `JobPostingUpdateRequest`
- `JobPositionRequest`

설계에 반드시 포함할 내용:

- 추가 필드 목록
- validation annotation 후보
- nullable 허용 여부
- 기본값 처리 위치
- 생성/수정에서 동일하게 받을지 여부
- 기존 API 호환성 영향

권장 방향:

- `postingType`은 필수 후보로 검토한다.
- `summary`는 목록 카드용이므로 입력 허용한다.
- `displayStartDateTime/displayEndDateTime`은 nullable 허용을 우선 검토한다.
- `visible`, `pinned`, `displayOrder`는 요청에서 받되 null이면 기본값을 적용할지 검토한다.
- `JobPositionRequest`에는 `applicationType`, `jobGroup`, `workLocation`, `employmentType` 추가를 우선 검토한다.

## 11. Response DTO 설계

아래 응답 확장안을 설계하라.

### 11.1 관리자 공고 목록 응답

대상:

- `JobPostingListResponse`

포함 후보:

- `id`
- `title`
- `postingType`
- `summary`
- `status`
- `visible`
- `pinned`
- `displayOrder`
- `displayStartDateTime`
- `displayEndDateTime`
- `receptionStartDateTime`
- `receptionEndDateTime`
- `receptionStatus`
- `publishedAt`
- `closedAt`
- `createdAt`
- `updatedAt`
- 모집분야 요약 정보

### 11.2 관리자 공고 상세 응답

대상:

- `JobPostingDetailResponse`
- `JobPositionResponse`

포함 후보:

- 목록 응답 필드 전체
- `contentHtml`
- 확장된 `jobPositions`
- `applicationFormConfig`

### 11.3 지원자 공개 공고 목록 응답

대상:

- `JobPostingPublicListResponse`

포함 후보:

- `id`
- `title`
- `postingType`
- `summary`
- `receptionStartDateTime`
- `receptionEndDateTime`
- `receptionStatus`
- `accepting`
- `pinned`
- `displayOrder`
- 모집분야 요약 또는 `positions`

주의:

- 지원자 목록에서는 `status`, `visible`, 내부 운영일시는 노출하지 않는 방향을 우선 검토하라.
- `contentHtml`은 목록 응답에 포함하지 않는 방향을 우선 검토하라.

### 11.4 지원자 공개 공고 상세 응답

대상:

- `JobPostingPublicDetailResponse`
- `JobPositionPublicResponse`

포함 후보:

- 공개 목록 응답 필드 전체
- `contentHtml`
- 확장된 `jobPositions`
- `applicationFormConfig`

## 12. Repository/Query 설계

현재 `JobPostingPublicListProjection`은 목록 응답 필드가 너무 적다.

설계에서 다음 중 어느 방향을 쓸지 판단하라.

1. Projection 확장
2. EntityGraph 기반 Entity 조회로 전환
3. QueryDSL 또는 Specification 도입 후보로만 기록

주의:

- 이번 구현 Phase에서 QueryDSL을 새로 도입하는 방향은 과하다. 기존 Spring Data JPA 방식 안에서 해결하는 방향을 우선 검토하라.
- 공개 목록에 positions까지 포함하면 N+1 문제가 생길 수 있으므로, 목록 응답에 positions를 어느 수준까지 포함할지 판단하라.
- 목록에서 모집분야 전체를 내려줄 경우 `@EntityGraph` 또는 별도 조회 전략을 설계하라.

## 13. Validation 설계

다음 validation 정책을 설계하라.

### 13.1 `JobPosting` validation

- `title` 필수
- `contentHtml` 필수
- `postingType` 필수 여부
- `summary` 길이 제한
- `receptionStartDateTime < receptionEndDateTime`
- `displayStartDateTime <= displayEndDateTime`
- `displayEndDateTime`이 접수 종료일보다 먼저 끝날 수 있는지 여부
- `visible` 기본값
- `pinned` 기본값
- `displayOrder` 최소값
- 마감된 공고 수정 금지 기존 정책 유지 여부

### 13.2 `JobPosition` validation

- 모집분야 최소 1개
- `positionName` 필수
- `applicationType` 필수 여부
- `jobGroup` 필수 여부
- `workLocation` 필수 여부
- `employmentType` 필수 여부
- `headcount` nullable 허용 여부
- `headcount` 최소값
- `sortOrder` 필수 및 최소값
- `sortOrder` 중복 허용 여부

권장:

- `sortOrder`는 같은 공고 내 중복을 금지하는 방향을 검토하라.
- `headcount`는 실제 채용 화면에서 비공개/미정 표현이 필요한지 판단하고, nullable 또는 별도 표시 필드를 설계하라.

## 14. Migration/호환성 설계

현재 프로젝트에 명시적 DB migration 체계가 약하거나 없을 수 있다. 그래도 설계 문서에는 아래 내용을 반드시 포함하라.

- 추가될 컬럼 목록
- nullable/default 전략
- 기존 데이터 보정 전략
- enum 컬럼 길이
- 운영 DB 반영 시 주의사항
- H2 테스트 영향

권장:

- 기존 데이터가 깨지지 않도록 신규 컬럼은 nullable 또는 default 값을 우선 검토한다.
- boolean 필드는 DB default와 Java default를 함께 고려한다.
- enum은 `@Enumerated(EnumType.STRING)` 방향을 유지한다.
- 기존 API 테스트가 깨지는 필드 추가는 응답 필드 추가 중심으로 설계하고, 필수 request 추가 시 테스트 보강 범위를 명시한다.

## 15. 테스트 설계

구현 Phase에서 작성해야 할 테스트 항목을 설계 문서에 포함하라.

### 15.1 Service Test 후보

- 공고 생성 시 확장 필드 저장
- 공고 수정 시 확장 필드 변경
- 노출 기간 validation
- 접수 기간 validation 유지
- 모집분야 확장 필드 저장
- 모집분야 sortOrder 중복 검증
- 공개 목록에서 `visible=false` 제외
- 공개 목록에서 display 기간 밖 공고 제외
- 공개 목록에서 접수마감 공고라도 display 기간 내이면 노출되는 정책 검증
- 공개 목록 정렬 검증
- `receptionStatus` 파생값 검증

### 15.2 Controller Test 후보

- 관리자 생성 Request 확장 필드 binding
- 관리자 상세 Response 확장 필드 포함
- 관리자 목록 Response 확장 필드 포함
- 지원자 공개 목록 Response 확장 필드 포함
- 지원자 공개 상세 Response 확장 필드 포함
- validation 실패 시 400 응답

## 16. 산출물

이번 작업에서 생성/수정할 문서는 아래로 제한한다.

### 16.1 설계 문서

새 파일 생성:

- `docs/codex/design/phase-03j-job-posting-domain-expansion-design.md`

문서에는 반드시 아래 목차를 포함하라.

1. 개요
2. 현재 구현 상태
3. 문제점
4. 설계 목표
5. 제외 범위
6. `JobPosting` 확장 설계
7. `JobPosition` 확장 설계
8. 신규/재사용 enum 설계
9. 접수 상태 파생값 설계
10. 공개 공고 필터링/정렬 설계
11. 관리자 API Request/Response 변경안
12. 지원자 공개 API Response 변경안
13. Repository/조회 전략
14. Validation 정책
15. Migration/호환성 고려사항
16. 테스트 계획
17. 다음 구현 Phase 분리안
18. 최종 결정 요약

### 16.2 사람이 보는 HTML 현황 문서

새 파일 생성:

- `docs/codex/reports/phase-03j-job-posting-domain-expansion-design.html`

HTML 문서는 기존 `docs/codex/reports/*.html` 스타일과 `docs/codex/templates/human-report-template.md`의 방향을 참고해 작성하라.

HTML 문서에는 최소한 아래 섹션을 포함하라.

- Phase 제목
- 이번 설계의 목적
- 현재 문제 요약
- 도메인 변경 요약
- API 영향 요약
- 화면 영향 요약
- DB 영향 요약
- 제외 범위
- 다음 구현 단계

### 16.3 구현 이력 문서

가능하면 아래 파일에 설계 완료 이력을 짧게 추가하라.

- `docs/codex/07-implementation-history.md`

단, 실제 구현 완료처럼 쓰지 말고 **설계 산출물 작성 완료**로만 기록하라.

## 17. 작성 톤과 품질 기준

- 구현자가 바로 다음 Phase 지시문을 작성할 수 있을 정도로 구체적으로 작성한다.
- 단순 아이디어 나열이 아니라, 결정/근거/대안/제외 범위를 분리한다.
- 현재 소스와 맞지 않는 클래스명/파일명을 임의로 만들지 않는다.
- 기존 구조를 대규모로 갈아엎는 설계는 금지한다.
- 기존 Phase 01a/01b 공고 기능과 Phase 03 지원서 기능을 깨지 않는 점진적 확장안을 우선한다.
- 화면 표시를 위해 필요한 값과 내부 운영 상태값을 혼동하지 않는다.
- 접수 가능 여부와 화면 노출 여부를 반드시 분리한다.
- 공통코드 관리 기능은 이번 Phase에서 만들지 않는다.

## 18. 최종 응답 형식

작업 완료 후 응답에는 아래만 간결하게 포함하라.

1. 생성/수정한 파일 목록
2. 설계상 핵심 결정 5개 이내
3. 다음 구현 Phase에서 해야 할 일 5개 이내
4. 범위 밖으로 제외한 항목 요약

소스 구현을 하지 않았다는 점을 명확히 밝혀라.
