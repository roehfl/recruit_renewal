# Phase 02 Stage Design

## Phase 03d-0 StageResult Design Note

- Phase 02 deferred `StageResult` until the Application domain existed.
- Phase 03d-0 revisits that deferred scope now that `JobApplication` and application detail/question-answer domains are implemented.
- `StageResult` is designed as a `Stage + JobApplication` result record with a recommended `stage_id + job_application_id` unique constraint.
- `StageResult` should reference `Stage` and `JobApplication` by N:1 unidirectional relationships.
- `Stage` should not own a `StageResult` collection in the initial implementation.
- Stage status policy recommendation:
  - `READY`: initialize possible.
  - `IN_PROGRESS`: result input/update possible.
  - `RESULT_ANNOUNCED`: read-only except future correction command.
  - `CLOSED`: read-only.
- The first implementation candidate is `POST /admin/stages/{stageId}/results/initialize` plus `GET /admin/stages/{stageId}/results`.
- This design-only phase did not change the existing Stage APIs.

## 1. Summary

Phase 02의 목적은 Phase 01에서 구현된 `JobPosting` 하위에 전형단계(`Stage`) 관리 모델을 추가해, 이후 지원서(`Application`), 전형결과(`StageResult`), 면접(`Interview`) 기능이 연결될 수 있는 기준 축을 만드는 것이다.

구현 대상 추천:

- Phase 02a: `JobPosting` 하위 전형단계(`Stage`) 관리
- Phase 02b: `Application` 도메인 구현 이후 `StageResult` 관리

Phase 02a도 한 번에 구현하지 않고 더 작게 나눈다.

- Phase 02a-1: Stage 기본 CRUD 및 최소 관리자 API 설계/구현 단위
- Phase 02a-2: reorder와 status command 설계/구현 단위
- Phase 02a-3: Controller/API 테스트 보강, 문서화, 통합 테스트 단위

구현하지 않을 대상:

- Phase 02a에서는 `Application`, `StageResult`, `Interview`, `InterviewEvaluation`, `Message`, `CommonCode`를 만들지 않는다.
- Phase 02a에서는 임시 `applicationId`, `applicantId` 숫자 필드만 가진 FK 없는 결과 테이블을 만들지 않는다.
- Phase 02a에서는 기존 Phase 01a/01b API 동작을 깨는 `JobPosting` publish 조건 변경을 바로 넣지 않는다.

## 2. Current Context

Phase 01a/01b에서 현재 구현된 구조는 다음과 같다.

- `JobPosting`
  - 채용공고 aggregate root
  - `title`, `contentHtml`, `receptionStartDateTime`, `receptionEndDateTime`, `status`, `publishedAt`, `closedAt`
  - 상태는 `DRAFT`, `PUBLISHED`, `CLOSED`
  - `publish`/`close`는 `Clock` 기반 시각 저장
- `JobPosition`
  - `JobPosting` N:1
  - 공고별 모집분야
  - `positionName`, `sortOrder`
- `ApplicationFormConfig`
  - `JobPosting` 1:1
  - 지원서 항목 사용 여부 flag
- 관리자 API
  - `GET /admin/job-postings`
  - `GET /admin/job-postings/{id}`
  - `POST /admin/job-postings`
  - `POST /admin/job-postings/{id}`
  - `POST /admin/job-postings/{id}/publish`
  - `POST /admin/job-postings/{id}/close`
- 공개 API
  - `GET /job-postings`
  - `GET /job-postings/{id}`
  - 공개 조회는 `PUBLISHED`만 노출

Phase 02와 연결되는 지점:

- `Stage`는 `JobPosting` 하위 설정 정보다.
- 공개 채용공고 상세에 Stage를 노출할지는 별도 정책이다. Phase 02a의 기본 범위는 관리자 Stage 관리로 제한하는 것이 안전하다.
- `StageResult`는 `Application`과 `Stage`의 교차 결과이므로 `Application` 없이 먼저 구현하면 도메인 정합성이 떨어진다.

## 3. Design Decision

추천 결정:

1. Phase 02는 `StageResult`까지 한 번에 구현하지 않는다.
2. Phase 02a에서 `Stage`만 `JobPosting` 하위 관리 기능으로 구현한다.
3. `StageResult`는 `Application` 도메인 구현 이후 Phase 02b 또는 별도 Phase로 넘긴다.

이유:

- `StageResult`의 자연스러운 소유 관계는 `Application N : 1`, `Stage N : 1`이다.
- 현재 `Application` 도메인이 없으므로 `StageResult`를 만들려면 임시 `applicationId` 또는 `applicantId` 필드를 둘 수밖에 없다.
- FK 없는 결과 테이블은 이후 `Application` 도입 시 migration과 데이터 정합성 비용이 커진다.
- 전형결과는 통계, 지원자 결과 조회, 메시지 발송, 면접 평가와 연결되는 핵심 데이터이므로 불완전한 모델로 먼저 만드는 것은 피한다.

## 4. Domain Model

## 4.1 Stage 설계

`Stage`는 공고별 전형단계를 의미한다.

관계 추천:

- `JobPosting` 1 : N `Stage`
- `Stage` N : 1 `JobPosting`
- 초기 구현은 `Stage -> JobPosting` N:1 단방향을 우선 검토한다.
- `JobPosting`에서 Stage 목록을 직접 들고 cascade/orphanRemoval을 적용하는 것은 Phase 02a에서는 권장하지 않는다. Stage는 공고 설정이지만 이후 `StageResult`, `Interview`와 연결될 수 있으므로 명시적 Service 삭제/변경 정책이 더 안전하다.

주요 필드 후보:

| 필드 | 타입 후보 | 설명 | 추천 |
|---|---|---|---|
| `id` | `Long` | PK | 사용 |
| `jobPosting` | `JobPosting` | 소속 공고 | 필수 |
| `stageName` | `String` | 전형명 | 필수, `@NotBlank` |
| `stageType` | `StageType` | 전형 유형 | 필수 enum |
| `stageOrder` | `Integer` | 공고 내 정렬 순서 | 필수, 0 이상 |
| `status` | `StageStatus` | 전형 단계 진행 상태 | 필수, 기본 `READY` |
| `resultAnnouncementDateTime` | `LocalDateTime` | 결과 발표 예정일시 | 선택 |
| `isFinal` 또는 `finalStage` | `boolean` | 마지막 전형 여부 | 선택, 공고당 1개 이하 권장 |
| `createdAt` / `updatedAt` | `BaseEntity` | 감사 필드 | 상속 |

Enum 후보:

| Enum | 값 후보 | 설명 |
|---|---|---|
| `StageType` | `DOCUMENT`, `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, `FINAL_INTERVIEW`, `ETC` | 전형 유형 |
| `StageStatus` | `READY`, `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED` | 전형 운영 상태 |

`resultAnnouncementDateTime` 의미:

- Phase 02a에서는 실제 발표 시각이 아니라 결과 발표 예정일시로만 사용한다.
- `announce` command는 상태만 `RESULT_ANNOUNCED`로 변경한다.
- 실제 발표 시각(`resultAnnouncedAt`)은 StageResult, 발표 이력, 메시지 발송 정책이 생긴 뒤 별도 필드로 분리한다.

`StageType`과 `finalStage` 의미:

- `StageType`은 전형 유형이다.
- `finalStage`는 해당 공고에서 마지막 단계인지 여부다.
- 두 값은 독립 값이다.
- 예: `stageType=SECOND_INTERVIEW`, `finalStage=true`는 2차면접이 마지막인 공고에서 허용될 수 있다.
- 예: `stageType=FINAL_INTERVIEW`, `finalStage=false`는 뒤에 최종 결정 또는 기타 단계가 있는 공고라면 허용될 수 있다.
- `FINAL`은 너무 포괄적이므로 Phase 02a에서는 `FINAL_INTERVIEW`를 후보로 둔다. 최종 합격/불합격 결정 단계가 별도로 필요하면 이후 `FINAL_DECISION` 도입을 검토한다.

Stage 정렬 기준:

1. 기본 정렬은 `stageOrder ASC`, `id ASC`
2. 같은 공고 안에서 `stageOrder` 중복은 금지하는 방향을 추천한다.
3. reorder API는 전체 Stage id/order 목록을 받아 한 번에 정렬을 갱신한다.

공고별 커스텀 전형단계:

- 허용을 추천한다.
- 이유: 채용 형태별로 서류/1차/2차/최종 구성은 달라질 수 있고, `StageType.ETC`로 예외 단계를 수용할 수 있다.

기본 전형단계 자동 생성:

- Phase 02a 최초 구현에서는 자동 생성하지 않는 것을 추천한다.
- 대신 Stage Service에 별도 기본 세트 생성 command 후보를 남긴다.
- 자동 생성이 필요하면 Phase 02a 후반 또는 운영 정책 확정 후 `POST /admin/job-postings/{jobPostingId}/stages/defaults` 같은 명시적 command로 분리한다.

삭제 가능 여부:

- `READY` 상태에서만 삭제 가능하도록 시작하는 것을 추천한다.
- `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED`는 삭제 차단을 기본값으로 둔다.
- 추후 `Application`/`StageResult`가 생기면 결과나 지원서가 연결된 Stage는 삭제 금지 또는 soft delete만 허용한다.

이미 지원자가 있는 경우 수정/삭제 제한:

- 현재는 `Application`이 없으므로 실제 존재 여부를 판단할 수 없다.
- 설계상 `Application` 도입 이후에는 다음 정책을 추천한다.
  - 지원자가 없는 `READY` Stage: 이름, 유형, 순서, 최종 여부 수정 가능
  - 지원자가 있거나 결과가 있는 Stage: 이름 정도만 제한적으로 수정, 순서/유형/삭제는 차단
  - 이미 발표 또는 마감된 Stage: 원칙적으로 수정/삭제 차단

## 4.2 StageResult 설계 방향

Phase 03d-0에서 StageResult 최신 설계를 별도 문서로 확정했다. 아래 Phase 02 후보는 Stage 구현 당시의 보류 메모이며, 구현 기준은 `docs/codex/design/phase-03d-stage-result-design.md`를 따른다.

`StageResult`는 특정 `Application`이 특정 `Stage`에서 받은 결과다.

StageResult 필드 후보:

| 필드 | 타입 후보 | 설명 | Phase 02a 처리 |
|---|---|---|---|
| `id` | `Long` | PK | 보류 |
| `stage` | `Stage` | 전형 단계 FK | 보류 |
| `application` | `Application` | 지원서 FK | `Application` 부재로 보류 |
| `resultStatus` | `StageResultStatus` | 결과 상태 | 보류 |
| `score` | `Integer` 또는 `BigDecimal` | 점수 | 보류 |
| `memo` | `String` | 관리자 메모 | 보류 |
| `evaluatedAt` | `LocalDateTime` | 평가 시각 | 보류 |
| `announcedAt` | `LocalDateTime` | 발표 시각 | 보류 |
| `createdAt` / `updatedAt` | `BaseEntity` | 감사 필드 | 보류 |

StageResultStatus 후보:

- `PASS`
- `FAIL`
- `HOLD`
- `ABSENT`
- `NOT_EVALUATED`

Application 없이 구현할 때의 문제:

- 지원서 FK를 걸 수 없어 실제 결과의 대상이 불명확하다.
- 지원자 한 명이 여러 공고에 지원할 경우 `applicantId`만으로 결과를 식별할 수 없다.
- `Application + Stage` unique 제약을 둘 수 없다.
- 지원서 제출 전/후 결과 생성 가능 여부를 검증할 수 없다.
- 전형결과 조회, 통계, 메시지 발송, 지원자 결과 확인이 모두 불완전해진다.

추천:

- Phase 02a에서는 StageResult 엔티티/Repository/API를 만들지 않는다.
- Phase 02b 또는 Application 이후 Phase에서 `Application + Stage` unique를 포함해 구현한다.

## 5. Entity Candidate

## 5.1 Stage Entity 후보

테이블 후보: `stage`

필드 후보:

- `id`
- `jobPosting`
- `stageName`
- `stageType`
- `stageOrder`
- `status`
- `resultAnnouncementDateTime`
- `finalStage`
- `createdAt`, `updatedAt`, `createdBy`, `updatedBy`

연관관계 후보:

- `@ManyToOne(fetch = FetchType.LAZY)`
- `@JoinColumn(name = "job_posting_id", nullable = false)`
- Stage 쪽 N:1 단방향 우선

제약 후보:

- `job_posting_id` index
- `job_posting_id + stage_order` unique는 최종적으로 권장하지만, Phase 02a-1에서는 Service 검증으로 시작하는 것을 추천한다.
- `job_posting_id + final_stage` unique는 DB unique로 강제하기 어렵다. Service에서 공고당 final stage 1개 이하를 검증하는 편이 현실적이다.

order unique 적용 방침:

- Phase 02a-1: Repository `exists` 쿼리와 Service 검증으로 같은 공고 내 `stageOrder` 중복을 차단한다.
- Phase 02a-2 reorder 구현 전에는 DB unique 제약을 바로 넣지 않는 편이 안전하다.
- 이유: `1,2,3 -> 2,1,3` 같은 순서 교환에서 단순 순차 update가 중간 unique 충돌을 만들 수 있다.
- 최종적으로는 금융권 운영 안정성을 위해 DB unique 제약을 추가하는 것이 맞다.
- DB unique를 추가할 경우 reorder에서는 임시 order로 먼저 밀어놓고 최종 order를 적용하거나, unique 충돌이 나지 않는 갱신 전략을 별도로 설계한다.

finalStage 중복 검증 Repository 후보:

- `boolean existsByJobPostingIdAndFinalStageTrue(Long jobPostingId)`
- `boolean existsByJobPostingIdAndFinalStageTrueAndIdNot(Long jobPostingId, Long stageId)`

cascade/orphanRemoval:

- Phase 02a에서는 `JobPosting`에 `List<Stage>`를 추가하더라도 cascade/orphanRemoval은 권장하지 않는다.
- 삭제는 `StageService`에서 정책 검증 후 명시적으로 처리한다.

## 5.2 StageResult Entity 후보

Phase 03d-0 기준 최신 후보는 `Application` 대신 현재 구현된 `JobApplication`을 참조한다. 관계는 `StageResult -> Stage`, `StageResult -> JobApplication` N:1 단방향이고, unique 후보는 `stage_id + job_application_id`이다.

테이블 후보: `stage_result`

필드 후보:

- `id`
- `stage`
- `application`
- `resultStatus`
- `score`
- `memo`
- `evaluatedAt`
- `announcedAt`
- `createdAt`, `updatedAt`, `createdBy`, `updatedBy`

연관관계 후보:

- `StageResult` N : 1 `Stage`
- `StageResult` N : 1 `Application`

제약 후보:

- `application_id + stage_id` unique
- `stage_id + result_status` index
- `application_id` index

Phase 02a 결론:

- 후보로만 남기고 실제 구현은 보류한다.

## 6. API Candidate

Base path 후보:

```text
/admin/job-postings/{jobPostingId}/stages
```

관리자 Stage API 후보:

| Method | Path | 목적 | 요청 DTO 후보 | 응답 DTO 후보 | Service 메서드 후보 | 주요 검증 | 상태 전이 | 테스트 대상 |
|---|---|---|---|---|---|---|---|---|
| GET | `/admin/job-postings/{jobPostingId}/stages` | 공고별 Stage 목록 조회 | 없음 | `List<StageListResponse>` | `getStages(jobPostingId)` | 공고 존재 확인 | 없음 | 정렬 조회, 공고 없음 |
| GET | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | Stage 상세 조회 | 없음 | `StageDetailResponse` | `getStage(jobPostingId, stageId)` | 공고/Stage 존재 및 소속 공고 일치 | 없음 | 상세 조회, 소속 불일치 |
| POST | `/admin/job-postings/{jobPostingId}/stages` | Stage 생성 | `StageCreateRequest` | `Long` 또는 `StageDetailResponse` | `create(jobPostingId, request)` | 공고 존재, 공고 CLOSED 차단, 이름 필수, order 중복 금지, final 중복 검증 | 기본 `READY` | 생성 성공, 검증 실패 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | Stage 일반 수정 | `StageUpdateRequest` | `Long` 또는 `StageDetailResponse` | `update(jobPostingId, stageId, request)` | 소속 확인, CLOSED 공고 차단, 현재 `READY` Stage만 허용, order 중복 금지, final 중복 검증 | 상태 변경 없음 | 수정 성공, READY 외 수정 실패 |
| POST | `/admin/job-postings/{jobPostingId}/stages/reorder` | Stage 순서 일괄 변경 | `StageReorderRequest` | `List<StageListResponse>` | `reorder(jobPostingId, request)` | 모든 Stage가 같은 공고 소속, 누락/중복 id 차단, order 중복 차단 | 상태 변경 없음 | reorder 성공, 누락/중복 실패 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/start` | Stage 시작 | 없음 또는 `StageStartRequest` | `Long` 또는 `StageDetailResponse` | `start(jobPostingId, stageId)` | 소속 확인, 공고 `PUBLISHED`만 허용, 현재 `READY`만 허용 | `READY -> IN_PROGRESS` | 전이 성공/실패, DRAFT 차단 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/announce` | 결과 발표 상태 전환 | 없음 또는 `StageAnnounceRequest` 후보 | `Long` 또는 `StageDetailResponse` | `announce(jobPostingId, stageId)` | 소속 확인, 공고 `PUBLISHED`만 허용, 현재 `IN_PROGRESS`만 허용 | `IN_PROGRESS -> RESULT_ANNOUNCED` | 전이 성공/실패, DRAFT 차단 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/close` | Stage 마감 | 없음 또는 `StageCloseRequest` | `Long` 또는 `StageDetailResponse` | `close(jobPostingId, stageId)` | 소속 확인, 공고 `PUBLISHED`만 허용, 현재 `RESULT_ANNOUNCED`만 허용 | `RESULT_ANNOUNCED -> CLOSED` | 전이 성공/실패, DRAFT 차단 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/delete` | Stage 삭제 command | 없음 | `Long` 또는 `Void` | `delete(jobPostingId, stageId)` | `READY`만 삭제, 공고 CLOSED 차단, future result/application 연결 차단 | 삭제 | 삭제 성공, 진행/발표/마감 삭제 실패 |

DELETE 메서드 검토:

- HTTP DELETE 자체가 명시적으로 금지된 것은 아니지만, 현재 프로젝트는 수정성 command를 POST로 쓰는 정책이 강하다.
- 삭제도 감사/검증/업무 command 성격이 강하므로 `POST /delete`를 추천한다.
- 물리 삭제인지 soft delete인지는 Application/StageResult 도입 후 한 번 더 확인한다. Phase 02a에서는 `READY` Stage 물리 삭제까지만 허용하는 설계가 단순하다.

요청 DTO 후보:

- `StageCreateRequest`
  - `stageName`
  - `stageType`
  - `stageOrder`
  - `resultAnnouncementDateTime`
  - `finalStage`
- `StageUpdateRequest`
  - `stageName`
  - `stageType`
  - `stageOrder`
  - `resultAnnouncementDateTime`
  - `finalStage`
- `StageReorderRequest`
  - `items: List<StageOrderRequest>`
- `StageOrderRequest`
  - `stageId`
  - `stageOrder`
- `StageAnnounceRequest`
  - Phase 02a에서는 비워두는 것을 우선 추천
  - 실제 발표 시각은 저장하지 않는다.

응답 DTO 후보:

- `StageListResponse`
  - `id`
  - `jobPostingId`
  - `stageName`
  - `stageType`
  - `stageOrder`
  - `status`
  - `resultAnnouncementDateTime`
  - `finalStage`
- `StageDetailResponse`
  - 목록 필드
  - `createdAt`
  - `updatedAt`

## 7. Business Rules

## 7.1 Stage 생성/수정/삭제 규칙

생성:

- `jobPostingId`는 존재해야 한다.
- `JobPosting`이 `CLOSED`이면 생성 불가.
- `stageName`은 필수다.
- `stageType`은 필수다.
- `stageOrder`는 필수이며 0 이상이다.
- 같은 공고 안에서 `stageOrder`는 중복될 수 없다.
- `finalStage=true`는 같은 공고 안에서 1개 이하를 권장한다.
- 생성 시 상태는 항상 `READY`다.

수정:

- Stage는 요청 path의 `jobPostingId`에 소속되어 있어야 한다.
- `JobPosting`이 `CLOSED`이면 수정 불가.
- Phase 02a implementation decision: Stage 일반 수정은 `READY` 상태에서만 허용한다.
- `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED`에서는 일반 수정을 차단한다.
- Phase 02a에서는 `IN_PROGRESS` 이상인 Stage의 `stageName`, `resultAnnouncementDateTime` 부분 수정도 허용하지 않는다.
- 부분 수정 허용은 `Application`/`StageResult` 도입 이후 별도 정책으로 검토한다.
- `stageOrder` 변경 시 같은 공고 안에서 중복 금지.

삭제:

- `READY` 상태에서만 삭제 허용.
- `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED`는 삭제 불가.
- Application/StageResult 도입 이후에는 연결 데이터가 있으면 삭제 불가.

## 7.2 상태 전이 규칙

추천 상태 전이:

```text
READY -> IN_PROGRESS -> RESULT_ANNOUNCED -> CLOSED
```

규칙:

- `READY -> IN_PROGRESS`
  - 전형 운영 시작
  - 공고가 `PUBLISHED`일 때만 허용
- `IN_PROGRESS -> RESULT_ANNOUNCED`
  - 결과 발표 상태
  - 추후 StageResult 발표와 연결
  - 공고가 `PUBLISHED`일 때만 허용
  - Phase 02a에서는 상태만 전환하고 실제 발표 시각은 기록하지 않는다.
- `RESULT_ANNOUNCED -> CLOSED`
  - 해당 단계 마감
  - 공고가 `PUBLISHED`일 때만 허용
  - 이후 수정/삭제 차단
- `CLOSED -> IN_PROGRESS` 되돌림은 금지한다.
- 임의 역전이는 금지한다.

`RESULT_ANNOUNCED` 상태 필요 여부:

- 추천: 필요하다.
- 이유: 진행 중과 발표 완료는 지원자 결과 조회, 메시지 발송, 다음 단계 생성/진행 조건에서 의미가 다르다.
- 날짜와 `finalStage`만으로는 “운영 상태”를 명확히 표현하기 어렵다.

## 7.3 JobPosting 상태와 Stage 조작 가능 여부

추천 정책:

| JobPosting 상태 | Stage 생성/수정/삭제 | Stage 시작/발표/마감 | 설명 |
|---|---|---|---|
| `DRAFT` | 가능 | 차단 | 공고 설정 단계 |
| `PUBLISHED` | 가능 | 가능 | 운영 중 공고 |
| `CLOSED` | 차단 | 차단 | 마감 공고는 전형 설정 변경 금지 |

세부 추천:

- `DRAFT`에서는 Stage 설정 CRUD와 reorder를 허용한다.
- `DRAFT`에서는 `start`, `announce`, `close` command를 차단한다.
- Stage `start`, `announce`, `close` command는 `PUBLISHED` JobPosting에서만 허용한다.
- `PUBLISHED`에서는 Stage 생성/수정까지 허용하되, 이미 Application이 생긴 뒤에는 제한을 강화한다.
- `CLOSED` 공고에서는 생성/수정/삭제/reorder/start/announce/close 등 모든 Stage 조작을 차단한다.

## 7.4 기본 Stage 생성 정책

추천:

- JobPosting 생성 시 기본 Stage 자동 생성은 Phase 02a 최초 구현에서 하지 않는다.
- 이유:
  - 기존 Phase 01a 생성 테스트와 API 계약을 건드리지 않는다.
  - 공고별 커스텀 전형단계가 필요할 수 있다.
  - 어떤 기본 세트가 맞는지 아직 확정되지 않았다.
- 기본 Stage가 필요하면 명시적 command로 분리한다.

후보 기본 세트:

1. 서류전형: `DOCUMENT`, order 0
2. 1차면접: `FIRST_INTERVIEW`, order 1
3. 최종면접: `FINAL_INTERVIEW`, order 2, finalStage true

## 7.5 JobPosting publish 조건과 Stage 최소 개수 연동

추천:

- Phase 02a 구현 직후에는 Phase 01a의 `publish` 검증에 Stage 최소 1개 조건을 바로 추가하지 않는다.
- 이유:
  - 기존 Phase 01a/01b 동작과 테스트를 깨지 않는다.
  - Stage 도입 직후 기존 데이터 또는 테스트 공고가 Stage 없이 게시될 수 있다.
  - publish 조건은 운영 정책 확정 이후 점진적으로 강화하는 것이 안전하다.
- 추후 정책 확정 후 `JobPostingService.publish`에 “Stage 최소 1개” 검증을 추가하는 별도 review-fix Phase를 추천한다.

## 8. Test Plan

Phase 02a 구현 시 필요한 테스트 후보:

Service 테스트:

- 공고에 Stage 생성 성공
- 존재하지 않는 JobPosting에 Stage 생성 실패
- `CLOSED` JobPosting의 Stage 생성 실패
- Stage 이름 필수 검증
- StageType 필수 검증
- stageOrder 0 미만 실패
- 같은 공고 안 stageOrder 중복 실패
- finalStage 중복 실패
- Stage 목록 조회 정렬 확인
- Stage 상세 조회 성공
- 다른 공고의 Stage 상세 조회 실패
- Stage 수정 성공
- `IN_PROGRESS` Stage 일반 수정 실패
- `RESULT_ANNOUNCED` Stage 일반 수정 실패
- `CLOSED` Stage 일반 수정 실패
- Stage reorder 성공
- reorder 요청에 누락된 Stage가 있으면 실패
- reorder 요청에 중복 Stage id 또는 중복 order가 있으면 실패
- `READY -> IN_PROGRESS` 전환 성공
- `IN_PROGRESS -> RESULT_ANNOUNCED` 전환 성공
- `RESULT_ANNOUNCED -> CLOSED` 전환 성공
- 잘못된 상태 전이 실패
- `DRAFT` JobPosting의 Stage 시작/발표/마감 실패
- `CLOSED` JobPosting의 Stage 시작/발표/마감 실패
- `READY` Stage 삭제 성공
- 진행 중/발표/마감 Stage 삭제 실패

Controller 테스트:

- API 응답이 `ApiResponse<T>` 형태인지 확인
- request validation 실패 시 400 계열 응답 확인
- POST 기반 수정/command API path 확인
- PUT이 없는지 확인

Repository 테스트 필요 여부:

- 초기에는 Service 테스트 중심으로 충분하다.
- 단, `jobPostingId + stageOrder` unique 또는 정렬 query를 repository 레벨에서 강하게 검증하려면 `@DataJpaTest`를 추가한다.

Page/size:

- Stage 목록은 공고 하나의 설정 목록이므로 초기에는 paging 없이 `List` 응답을 추천한다.
- 전형결과 목록은 Application 이후 대량 데이터가 될 가능성이 높으므로 StageResult Phase에서 paging을 도입한다.

## 9. Implementation Plan

Phase를 작게 나누는 추천안:

### Phase 02a-1: Stage Entity/Repository/Service 기본 CRUD와 최소 API

- `Stage`, `StageType`, `StageStatus` 후보 구현
- `StageRepository`
- `StageService` 생성/조회/수정 기본 로직
- 관리자 Stage Controller와 목록/상세/생성/수정 API
- `StageCreateRequest`, `StageUpdateRequest`, `StageListResponse`, `StageDetailResponse`
- 공고 존재/상태 검증
- order/finalStage Service 검증
- `READY` 상태에서만 일반 수정 허용
- Service 테스트
- reorder, start/announce/close, delete는 Phase 02a-1 범위에서 제외한다.

### Phase 02a-2: Stage reorder/status command

- reorder command
- start/announce/close command
- delete command 정책 구현
- 상태 전이 검증 테스트
- 구현 결과:
  - `POST /admin/job-postings/{jobPostingId}/stages/reorder`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/start`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/announce`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/close`
  - `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete`
  - DB unique 제약은 추가하지 않고 Service 검증으로 유지
  - READY가 아닌 Stage가 포함된 reorder는 차단

### Phase 02a-3: Controller/API Test/문서화

- 관리자 Stage Controller 보강
- Controller 테스트 또는 최소 API path 검증
- Phase 02a 구현 문서 작성
- `./gradlew clean test`

### Phase 02b: Application 이후 StageResult

- `Application` 도메인 구현 이후 진행
- `StageResult`
- `StageResultStatus`
- `Application + Stage` unique 제약
- 관리자 전형결과 조회/저장 API
- 지원자 결과 조회 API
- 엑셀 업로드/다운로드는 더 뒤로 분리

## 10. Risks and Open Questions

Application 부재:

- `StageResult`는 Application 없이 구현하지 않는 것이 맞다.
- 임시 id 필드로 결과를 저장하면 이후 마이그레이션 비용이 커진다.

CommonCode 도입 시점:

- Phase 02에서는 enum 시작을 추천한다.
- `StageType`, `StageStatus`, `StageResultStatus`는 초기에는 비즈니스 로직 분기 값이며 변경 빈도가 낮다.
- CommonCode는 관리자 코드 관리 화면, 표시명 다국어/정렬/활성 여부 정책이 필요해진 뒤 별도 Phase로 도입한다.

기본 전형단계 자동 생성:

- 운영 기본 세트가 확정되지 않았다.
- 자동 생성을 넣으면 Phase 01a JobPosting 생성 동작과 테스트에 영향을 줄 수 있다.
- 명시적 command 또는 프론트 기본값 제안 방식으로 시작하는 편이 안전하다.

공고 게시 조건과 Stage 최소 개수 연동:

- 최종적으로는 게시 전 Stage 최소 1개 조건이 자연스럽다.
- 그러나 Phase 02a 구현 직후 바로 Phase 01a publish 검증에 넣으면 기존 테스트와 데이터가 흔들릴 수 있다.
- Stage 관리 기능이 안정된 뒤 별도 보완 Phase에서 추가한다.

삭제 정책:

- Phase 02a에서는 `READY` 상태 물리 삭제로 시작할 수 있다.
- Application/StageResult가 도입된 뒤에는 연결 데이터가 있는 Stage 삭제를 차단하거나 soft delete를 검토해야 한다.

Entity 상태 검증 책임:

- Phase 02a-1에서는 `Stage.update()` 내부보다 `StageService`에서 READY 상태 검증을 수행하는 Service 중심 정책으로 시작한다.
- 다른 Service가 Stage를 직접 수정하는 흐름이 생기면 Entity 내부 방어를 추가할지 재검토한다.

Validation 실패 응답:

- Controller `@Valid` 실패도 `ApiResponse.fail()` 형식으로 통일하는 공통 예외 처리를 둔다.

StageType과 finalStage:

- `StageType`과 `finalStage`는 독립 값으로 둔다.
- `StageType.FINAL`은 사용하지 않고 `FINAL_INTERVIEW`를 후보로 둔다.
- 최종 결정 전형이 별도 단계로 필요하면 이후 `FINAL_DECISION`을 추가 검토한다.

## 11. Recommended Next Codex Prompt

아래는 Phase 02a-1 구현을 요청할 때 사용할 수 있는 지시문 초안이다. Phase 02a 전체가 아니라 Stage 기본 CRUD까지만 구현하도록 범위를 의도적으로 줄였다.

```text
AGENTS.md와 docs/codex/*.md를 먼저 읽어라.
특히 docs/codex/design/phase-02-stage-design.md와 Phase 01a/01b 구현 문서를 확인해라.

이번 작업은 Phase 02a-1: JobPosting 하위 전형단계(Stage) 기본 CRUD 구현이다.
StageResult는 Application 도메인이 아직 없으므로 구현하지 마라.

구현 범위:
1. Stage Entity
   - JobPosting N:1
   - stageName, stageType, stageOrder, status, resultAnnouncementDateTime, finalStage
   - resultAnnouncementDateTime은 결과 발표 예정일시로만 사용한다.
   - BaseEntity 상속
2. Enum
   - StageType: DOCUMENT, FIRST_INTERVIEW, SECOND_INTERVIEW, FINAL_INTERVIEW, ETC
   - StageStatus: READY, IN_PROGRESS, RESULT_ANNOUNCED, CLOSED
3. Repository
   - jobPostingId 기준 목록 조회, stageOrder ASC 정렬
   - 같은 공고 내 stageOrder 중복 확인용 exists 메서드
   - finalStage 중복 확인용 메서드
     - existsByJobPostingIdAndFinalStageTrue(Long jobPostingId)
     - existsByJobPostingIdAndFinalStageTrueAndIdNot(Long jobPostingId, Long stageId)
4. Service
   - 생성/목록/상세/수정
   - Stage 기본 검증
   - Controller에 비즈니스 로직을 넣지 마라.
   - Stage 일반 수정은 READY 상태에서만 허용한다.
   - IN_PROGRESS, RESULT_ANNOUNCED, CLOSED 상태에서는 일반 수정을 차단한다.
   - 같은 공고 내 stageOrder 중복은 Service 검증으로 차단한다.
   - finalStage=true는 같은 공고에서 1개 이하로 Service 검증한다.
   - Phase 02a-1에서는 DB unique 제약을 강제하지 말고 Service 검증으로 시작한다.
5. Controller/API
   - GET /admin/job-postings/{jobPostingId}/stages
   - GET /admin/job-postings/{jobPostingId}/stages/{stageId}
   - POST /admin/job-postings/{jobPostingId}/stages
   - POST /admin/job-postings/{jobPostingId}/stages/{stageId}
   - PUT은 만들지 않는다.
   - reorder/status/delete API는 만들지 않는다.
6. Test
   - 공고에 Stage 생성 성공
   - 존재하지 않는 JobPosting에 Stage 생성 실패
   - CLOSED JobPosting에 Stage 생성 실패
   - Stage 이름 필수 검증
   - StageType 필수 검증
   - stageOrder 0 미만 실패
   - 같은 공고 내 stageOrder 중복 실패
   - finalStage 중복 실패
   - Stage 목록 조회 정렬 확인
   - Stage 상세 조회 성공
   - 다른 공고의 Stage 상세 조회 실패
   - READY Stage 수정 성공
   - IN_PROGRESS 이상 Stage 일반 수정 실패
7. 이번 범위에서 제외
   - reorder
   - start
   - announce
   - close
   - delete
   - Controller 통합 테스트
   - StageResult, Application, Interview, Message, CommonCode

정책:
- PUT은 사용하지 마라.
- 기존 Phase 01a/01b API 의미를 바꾸지 마라.
- JobPosting publish 조건에 Stage 최소 1개 검증은 이번 구현에서 추가하지 마라.
- 공고 생성 시 기본 Stage 자동 생성은 이번 구현에 넣지 마라.
- CommonCode를 만들지 말고 enum으로 시작해라.
- StageType과 finalStage는 독립 값이다.
- resultAnnouncementDateTime은 예정일시이며 실제 발표 시각을 저장하지 않는다.

문서화:
- docs/codex/implementation/phase-02a-1-stage-basic-crud.md 생성
- docs/codex/07-implementation-history.md 갱신

검증:
- ./gradlew clean test 실행
- 변경 파일, 테스트 결과, 남은 이슈를 보고해라.
```

아래는 Phase 02a-2 구현 시 별도로 사용할 후속 지시문 방향이다.

```text
Phase 02a-1 구현 결과를 확인한 뒤 Phase 02a-2: Stage reorder/status command를 구현해라.

구현 범위:
1. reorder
2. start
3. announce
4. close
5. delete command

정책:
- Stage start/announce/close command는 PUBLISHED JobPosting에서만 허용한다.
- DRAFT JobPosting에서는 Stage 생성/수정/삭제/reorder만 허용하고, start/announce/close는 차단한다.
- CLOSED JobPosting에서는 모든 Stage 조작을 차단한다.
- announce command는 상태만 RESULT_ANNOUNCED로 바꾸고 실제 발표시각은 저장하지 않는다.
- 삭제는 DELETE가 아니라 POST command API로 구현한다.
- reorder 구현 전 DB unique 제약 여부를 다시 검토한다.
```

## 12. Phase 02a-3 Documentation Note

- Phase 02a-3에서는 신규 도메인이나 신규 API를 만들지 않고 `StageControllerTest`를 보강해 관리자 Stage API 계약을 검증했다.
- 검증 범위는 Stage CRUD API, reorder/status/delete command API, validation/error 응답, PUT/DELETE HTTP method 미지원 정책이다.
- Stage 공개 노출 API는 아직 구현하지 않는다.
- JobPosting publish 조건에 Stage 최소 1개 검증은 아직 추가하지 않는다.
- StageResult는 Application 도메인 구현 이후로 계속 보류한다.
- 다음 구현은 StageResult가 아니라 Application 기본 흐름을 우선 검토하는 것을 추천한다.
## Phase 03d-1 StageResult Implementation Note

- Phase 02 deferred StageResult until Application existed.
- Phase 03d-1 now implements the first StageResult vertical slice.
- `StageResult` is stored as a separate row for one `Stage + JobApplication` pair.
- `Stage` still does not own a StageResult collection.
- Initialize is allowed only when Stage is `READY` or `IN_PROGRESS`.
- `RESULT_ANNOUNCED` and `CLOSED` stages reject initialize.
- `GET /admin/stages/{stageId}/results` lists existing result rows.
- `POST /admin/stages/{stageId}/results/initialize` creates missing `PENDING` rows for `SUBMITTED` applications.
- Result update, pending-result announce guard, correction history, and applicant-facing result read remain deferred.

## Phase 03d-2 StageResult Update and Announce Guard Note

- Phase 03d-2 added StageResult result input commands.
- Admins can update one result through `POST /admin/stages/{stageId}/results/{resultId}`.
- Admins can update multiple results through `POST /admin/stages/{stageId}/results/bulk`.
- General result update is allowed only for `IN_PROGRESS` stages.
- `READY`, `RESULT_ANNOUNCED`, and `CLOSED` stages reject general result update.
- `StageService.announce()` now requires at least one StageResult row and rejects announcement while any `PENDING` row remains.
- Stage start/close policies remain unchanged.
- Correction history, actual admin identity, applicant-facing result read, and audit logging remain deferred.
