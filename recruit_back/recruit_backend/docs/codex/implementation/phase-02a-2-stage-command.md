# Phase 02a-2 - Stage Reorder and Commands

## Phase 이름

Phase 02a-2: Stage reorder / status command / delete command

## 구현 목적

Phase 02a-1에서 추가한 `JobPosting` 하위 `Stage` 기본 CRUD를 유지하면서, 관리자 전형단계 운영에 필요한 순서 변경, 상태 전이, 삭제 command를 추가한다.

## 구현 범위

- Stage reorder command
- Stage start/announce/close status command
- Stage delete command
- reorder 요청 DTO 추가
- command API 추가
- `@Valid` 실패 응답의 `ApiResponse.fail()` 통일 보완
- Stage command Service 테스트 추가

## 구현하지 않은 범위

- `Application`
- `StageResult`
- `Interview`
- `Message`
- `CommonCode`
- JobPosting publish 시 Stage 최소 1개 검증
- JobPosting 생성 시 기본 Stage 자동 생성
- 실제 발표 시각 필드 저장
- DB unique 제약 추가
- Stage 공개 조회 API 노출

## 변경 파일 목록

### 코드

- `src/main/java/com/shinyoung/recruit/domain/entity/Stage.java`
- `src/main/java/com/shinyoung/recruit/dto/request/StageOrderRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/StageReorderRequest.java`
- `src/main/java/com/shinyoung/recruit/service/StageService.java`
- `src/main/java/com/shinyoung/recruit/controller/StageController.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`

### 테스트

- `src/test/java/com/shinyoung/recruit/service/StageServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/StageControllerTest.java`

### 문서

- `docs/codex/implementation/phase-02a-2-stage-command.md`
- `docs/codex/implementation/phase-02a-1-stage-basic-crud.md`
- `docs/codex/design/phase-02-stage-design.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `StageOrderRequest`
- `StageReorderRequest`

## 수정 클래스 목록

- `Stage`
- `StageService`
- `StageController`
- `GlobalExceptionHandler`
- `StageServiceTest`
- `StageControllerTest`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Entity | `domain.entity` | `Stage` | Stage 순서/상태 변경 | `reorder`, `start`, `announce`, `close` | `StageStatus` | 상태 검증은 Service에서 수행 |
| Request DTO | `dto.request` | `StageOrderRequest` | reorder 개별 item | `stageId`, `stageOrder` | `StageReorderRequest` | `stageId` 필수, `stageOrder` 0 이상 |
| Request DTO | `dto.request` | `StageReorderRequest` | reorder 요청 | `items` | `StageOrderRequest` | null/empty 불가, nested validation 적용 |
| Service | `service` | `StageService` | Stage command 정책 처리 | `reorder`, `start`, `announce`, `close`, `delete` | `StageRepository`, `JobPostingRepository` | command 검증은 Controller가 아닌 Service에서 수행 |
| Controller | `controller` | `StageController` | Stage command API | `POST /reorder`, `POST /start`, `POST /announce`, `POST /close`, `POST /delete` | `StageService`, `ApiResponse` | PUT/DELETE HTTP method 없음 |
| Exception/Handler | `exception` | `GlobalExceptionHandler` | validation 실패 응답 통일 | `handleMethodArgumentNotValid`, `handleConstraintViolation` | `ApiResponse` | 400 + `ApiResponse.fail()` |
| Test | `service` | `StageServiceTest` | command 정책 검증 | reorder/status/delete 테스트 | `StageService`, `JobPostingService` | 실 LDAP 의존 없음 |
| Test | `controller` | `StageControllerTest` | validation 응답 검증 | invalid reorder request 테스트 | `StageController` | `ApiResponse` 형식 확인 |

## API 목록

| Method | Path | 목적 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/admin/job-postings/{jobPostingId}/stages/reorder` | Stage 순서 일괄 변경 | `StageReorderRequest` | `ApiResponse<List<StageListResponse>>` |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/start` | Stage 시작 | 없음 | `ApiResponse<Long>` |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/announce` | Stage 결과 발표 상태 전환 | 없음 | `ApiResponse<Long>` |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/close` | Stage 마감 | 없음 | `ApiResponse<Long>` |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/delete` | Stage 삭제 command | 없음 | `ApiResponse<Long>` |

## Entity 관계 요약

- `Stage` N : 1 `JobPosting`
- `Stage -> JobPosting` 단방향 관계 유지
- `JobPosting`에 `List<Stage>`를 추가하지 않음
- cascade/orphanRemoval 없음

## 주요 비즈니스 규칙

- command API는 모두 POST를 사용한다.
- PUT과 DELETE HTTP method는 사용하지 않는다.
- `Application`, `StageResult`와의 연결 검증은 아직 구현하지 않는다.
- `resultAnnouncementDateTime`은 계속 결과 발표 예정일시로만 사용한다.
- announce command는 실제 발표 시각을 저장하지 않는다.

## 상태 전이 규칙

```text
READY -> IN_PROGRESS -> RESULT_ANNOUNCED -> CLOSED
```

- start는 `READY` Stage에서만 가능하다.
- announce는 `IN_PROGRESS` Stage에서만 가능하다.
- close는 `RESULT_ANNOUNCED` Stage에서만 가능하다.
- `CLOSED` Stage는 되돌릴 수 없다.
- 역전이는 허용하지 않는다.
- start/announce/close command는 `PUBLISHED` JobPosting에서만 가능하다.
- `DRAFT`, `CLOSED` JobPosting에서는 start/announce/close가 불가능하다.

## reorder 정책

- 요청은 해당 JobPosting의 모든 Stage를 포함해야 한다.
- 누락 Stage가 있으면 실패한다.
- 존재하지 않는 Stage id가 있으면 실패한다.
- 다른 JobPosting 소속 Stage id가 있으면 실패한다.
- 중복 Stage id가 있으면 실패한다.
- 중복 `stageOrder`가 있으면 실패한다.
- `stageOrder`는 null일 수 없고 0 이상이어야 한다.
- `CLOSED` JobPosting에서는 reorder가 불가능하다.
- `DRAFT`, `PUBLISHED` JobPosting에서는 reorder가 가능하다.
- Phase 02a-2에서는 `READY`가 아닌 Stage가 포함되면 reorder를 차단한다.
- DB unique 제약은 아직 추가하지 않고 Service 검증으로 처리한다.

## delete 정책

- 삭제는 `POST /delete` command API로 처리한다.
- `READY` Stage만 삭제 가능하다.
- `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED` Stage는 삭제 불가능하다.
- `CLOSED` JobPosting에서는 삭제 불가능하다.
- `DRAFT`, `PUBLISHED` JobPosting에서는 `READY` Stage 삭제가 가능하다.
- 현재는 물리 삭제로 시작한다.
- Application/StageResult 도입 후 연결 데이터가 있는 Stage 삭제 차단 또는 soft delete를 재검토한다.

## 테스트 목록

### reorder

- reorder 성공
- reorder 후 Stage 목록이 `stageOrder ASC`, `id ASC`로 정렬되는지 확인
- reorder 요청에 누락된 Stage가 있으면 실패
- reorder 요청에 중복 Stage id가 있으면 실패
- reorder 요청에 중복 stageOrder가 있으면 실패
- reorder 요청에 다른 JobPosting 소속 Stage가 있으면 실패
- reorder 요청에 존재하지 않는 Stage id가 있으면 실패
- CLOSED JobPosting의 reorder 실패
- READY가 아닌 Stage가 포함된 reorder 실패

### status command

- PUBLISHED JobPosting에서 `READY -> IN_PROGRESS` start 성공
- DRAFT JobPosting에서 start 실패
- CLOSED JobPosting에서 start 실패
- READY가 아닌 Stage start 실패
- PUBLISHED JobPosting에서 `IN_PROGRESS -> RESULT_ANNOUNCED` announce 성공
- IN_PROGRESS가 아닌 Stage announce 실패
- announce 시 실제 발표 시각 필드가 새로 생기지 않고 `resultAnnouncementDateTime` 유지
- PUBLISHED JobPosting에서 `RESULT_ANNOUNCED -> CLOSED` close 성공
- RESULT_ANNOUNCED가 아닌 Stage close 실패
- CLOSED Stage 재전이 실패

### delete

- DRAFT JobPosting에서 READY Stage 삭제 성공
- PUBLISHED JobPosting에서 READY Stage 삭제 성공
- CLOSED JobPosting에서 삭제 실패
- IN_PROGRESS Stage 삭제 실패
- RESULT_ANNOUNCED Stage 삭제 실패
- CLOSED Stage 삭제 실패
- 존재하지 않는 Stage 삭제 실패
- 다른 JobPosting 소속 Stage 삭제 실패

### validation

- Stage 생성 요청 validation 실패 시 `ApiResponse` 형식으로 400 응답
- Stage reorder 요청 validation 실패 시 `ApiResponse` 형식으로 400 응답

## 실행한 테스트 명령

```bash
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageServiceTest --tests com.shinyoung.recruit.controller.StageControllerTest
```

```bash
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- StageServiceTest: 성공
- StageControllerTest: 성공
- 전체 테스트: 성공

## 남은 이슈

- DB unique 제약은 아직 없다.
- 동시 reorder/create 요청의 최종 중복 방지는 운영 안정성 단계에서 DB 제약 또는 락 정책으로 보완해야 한다.
- Application/StageResult 도입 전이므로 진행 중 Stage reorder/delete 제한은 보수적으로 잡았다.

## 다음 Phase 전 확인 사항

- Phase 02a-3에서 Controller/API 통합 테스트 범위를 확정한다.
- Stage 공개 노출 여부를 결정한다.
- Application 기본 흐름을 먼저 구현할지, Stage Controller 테스트 보강을 먼저 할지 결정한다.
- Stage 최소 1개를 JobPosting publish 조건으로 추가할지 별도 보완 Phase에서 검토한다.

## Phase 02a-3 연계 메모

- Phase 02a-3에서 `StageControllerTest`를 보강해 Phase 02a-2 command API의 path, method, 성공 응답, 실패 응답 포맷을 검증했다.
- command API는 모두 POST로 유지되며, DELETE HTTP method는 사용하지 않는다.
- `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete` delete command 정책은 유지된다.
- Stage 공개 노출 API, JobPosting publish Stage 최소 1개 검증, StageResult 구현은 이번 범위에 포함하지 않았다.
