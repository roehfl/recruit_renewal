# Phase 02a-1 - Stage Basic CRUD

## Phase Name

Phase 02a-1: JobPosting 하위 전형단계(Stage) 기본 CRUD

## 구현 목적

Phase 01a/01b에서 구현된 `JobPosting`을 기준으로 관리자 화면에서 공고별 전형단계를 설정할 수 있는 최소 CRUD 기반을 추가한다. `StageResult`는 `Application` 도메인이 아직 없으므로 구현하지 않고, Phase 02a-1에서는 `Stage` 생성/목록/상세/수정만 제공한다.

## 구현 범위

- `Stage` Entity 추가
- `StageType`, `StageStatus` enum 추가
- `StageRepository` 추가
- `StageService` 생성/목록/상세/수정 로직 추가
- 관리자 Stage CRUD API 추가
- Stage 요청/응답 DTO 추가
- Stage 전용 예외와 전역 예외 처리 추가
- Stage Service 테스트 추가
- `@Valid` 실패 응답을 `ApiResponse.fail()` 형식으로 감싸는 공통 처리 추가
- Stage Controller validation 실패 응답 테스트 추가
- Phase 02 설계 문서의 02a-1 범위 보완

## 구현하지 않은 범위

- reorder API 및 Service 로직
- start/announce/close/delete command
- `StageResult`
- `Application`, `Interview`, `Message`, `CommonCode`
- JobPosting 게시 조건의 Stage 최소 1개 검증
- JobPosting 생성 시 기본 Stage 자동 생성
- 공개 채용공고 상세의 Stage 노출

## 변경 파일 목록

### 코드

- `src/main/java/com/shinyoung/recruit/domain/entity/Stage.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/StageRepository.java`
- `src/main/java/com/shinyoung/recruit/enumeration/StageType.java`
- `src/main/java/com/shinyoung/recruit/enumeration/StageStatus.java`
- `src/main/java/com/shinyoung/recruit/dto/request/StageCreateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/StageUpdateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/StageListResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/StageDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/service/StageService.java`
- `src/main/java/com/shinyoung/recruit/controller/StageController.java`
- `src/main/java/com/shinyoung/recruit/exception/StageNotFoundException.java`
- `src/main/java/com/shinyoung/recruit/exception/InvalidStageException.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`

### 테스트

- `src/test/java/com/shinyoung/recruit/service/StageServiceTest.java`
- `src/test/java/com/shinyoung/recruit/controller/StageControllerTest.java`

### 문서

- `docs/codex/design/phase-02-stage-design.md`
- `docs/codex/implementation/phase-02a-1-stage-basic-crud.md`
- `docs/codex/07-implementation-history.md`

## 신규 클래스 목록

- `Stage`
- `StageRepository`
- `StageType`
- `StageStatus`
- `StageCreateRequest`
- `StageUpdateRequest`
- `StageListResponse`
- `StageDetailResponse`
- `StageService`
- `StageController`
- `StageNotFoundException`
- `InvalidStageException`
- `StageServiceTest`
- `StageControllerTest`

## 수정 클래스 목록

- `GlobalExceptionHandler`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Entity | `domain.entity` | `Stage` | JobPosting 하위 전형단계 | `jobPosting`, `stageName`, `stageType`, `stageOrder`, `status`, `resultAnnouncementDateTime`, `finalStage`, `create()`, `update()` | `JobPosting`, `StageType`, `StageStatus` | `JobPosting`에 컬렉션을 추가하지 않는 단방향 N:1 |
| Repository | `domain.repository` | `StageRepository` | Stage 조회 및 중복 검증 | `findByJobPostingIdOrderByStageOrderAscIdAsc`, `findByIdAndJobPostingId`, order/finalStage exists 메서드 | `Stage` | 목록은 paging 없이 정렬 List 조회 |
| Enum | `enumeration` | `StageType` | 전형 유형 | `DOCUMENT`, `FIRST_INTERVIEW`, `SECOND_INTERVIEW`, `FINAL_INTERVIEW`, `ETC` | `Stage` | `finalStage`와 독립 값 |
| Enum | `enumeration` | `StageStatus` | 전형단계 상태 | `READY`, `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED` | `Stage` | Phase 02a-1에서는 생성 시 `READY` 고정 |
| Request DTO | `dto.request` | `StageCreateRequest` | Stage 생성 요청 | `stageName`, `stageType`, `stageOrder`, `resultAnnouncementDateTime`, `finalStage` | `StageService` | `status` 없음 |
| Request DTO | `dto.request` | `StageUpdateRequest` | Stage 일반 수정 요청 | `stageName`, `stageType`, `stageOrder`, `resultAnnouncementDateTime`, `finalStage` | `StageService` | `status` 없음 |
| Response DTO | `dto.response` | `StageListResponse` | Stage 목록 응답 | `from(Stage)` | `Stage` | `status` 포함 |
| Response DTO | `dto.response` | `StageDetailResponse` | Stage 상세 응답 | `from(Stage)`, `createdAt`, `updatedAt` | `Stage` | 감사 시각 포함 |
| Service | `service` | `StageService` | Stage CRUD 비즈니스 로직 | `getStages`, `getStage`, `create`, `update` | `StageRepository`, `JobPostingRepository` | CLOSED 공고 변경 차단, READY만 수정 허용 |
| Controller | `controller` | `StageController` | 관리자 Stage API | GET 목록/상세, POST 생성/수정 | `StageService`, `ApiResponse` | PUT 없음, command API 없음 |
| Exception | `exception` | `StageNotFoundException` | Stage 미존재 또는 소속 불일치 | 생성자 | `GlobalExceptionHandler` | 404 응답 |
| Exception | `exception` | `InvalidStageException` | Stage 검증 실패 | 생성자 | `GlobalExceptionHandler` | 400 응답 |
| Exception | `exception` | `GlobalExceptionHandler` | 전역 예외 응답 처리 | Stage 예외 핸들러, `MethodArgumentNotValidException` 핸들러 | `ApiResponse` | `@Valid` 실패도 `ApiResponse.fail()`로 응답 |
| Test | `service` | `StageServiceTest` | Stage Service 규칙 검증 | 생성/조회/수정/검증 실패 테스트 | `StageService`, `JobPostingService` | 실 LDAP 의존 없음 |
| Test | `controller` | `StageControllerTest` | Stage Controller validation 응답 검증 | invalid create request 테스트 | `StageController`, `GlobalExceptionHandler` | `@Valid` 실패 응답이 `ApiResponse` 형태인지 확인 |

## API 목록

| Method | Path | 목적 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/admin/job-postings/{jobPostingId}/stages` | 공고별 Stage 목록 조회 | 없음 | `ApiResponse<List<StageListResponse>>` |
| GET | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | Stage 상세 조회 | 없음 | `ApiResponse<StageDetailResponse>` |
| POST | `/admin/job-postings/{jobPostingId}/stages` | Stage 생성 | `StageCreateRequest` | `ApiResponse<Long>` |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | Stage 일반 수정 | `StageUpdateRequest` | `ApiResponse<Long>` |

## Entity 관계 요약

- `Stage` N : 1 `JobPosting`
- `Stage.jobPosting`은 `@ManyToOne(fetch = FetchType.LAZY)`와 `@JoinColumn(name = "job_posting_id", nullable = false)`를 사용한다.
- `JobPosting`에는 `List<Stage>`를 추가하지 않았다.
- cascade/orphanRemoval은 적용하지 않았다.

## 주요 비즈니스 규칙

- Stage 생성 시 `status`는 항상 `READY`다.
- 생성/수정 Request DTO에는 `status`를 받지 않는다.
- `DRAFT`, `PUBLISHED` JobPosting에서는 Stage 조회/생성/수정이 가능하다.
- `CLOSED` JobPosting에서는 Stage 생성/수정이 불가능하다.
- Stage 일반 수정은 `READY` 상태에서만 허용한다.
- `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED` Stage는 일반 수정이 불가능하다.
- `stageName`은 blank일 수 없다.
- `stageType`은 필수다.
- `stageOrder`는 필수이며 0 이상이어야 한다.
- 같은 JobPosting 안에서 `stageOrder`는 중복될 수 없다.
- `finalStage=true`는 같은 JobPosting 안에서 1개 이하만 허용한다.
- `resultAnnouncementDateTime`은 결과 발표 예정일시로만 사용하며 실제 발표 시각은 저장하지 않는다.
- `Stage.update()` 내부에는 READY 상태 검증을 중복으로 넣지 않고, 현재 Phase에서는 `StageService`가 상태 검증 책임을 가진다.
- `@Valid` 실패는 `GlobalExceptionHandler`에서 `MethodArgumentNotValidException`을 처리해 `ApiResponse.fail()`로 응답한다.

## 테스트 목록

- 공고에 Stage 생성 성공
- 생성 시 status가 READY인지 확인
- 존재하지 않는 JobPosting에 Stage 생성 실패
- CLOSED JobPosting의 Stage 생성 실패
- stageName blank 생성 실패
- stageType null 생성 실패
- stageOrder null 생성 실패
- stageOrder 0 미만 생성 실패
- 같은 공고 안 stageOrder 중복 생성 실패
- finalStage=true 중복 생성 실패
- Stage 목록 조회 정렬 확인
- Stage 상세 조회 성공
- 다른 공고의 Stage 상세 조회 실패
- READY Stage 수정 성공
- 수정 시 stageOrder 중복 실패
- 수정 시 finalStage 중복 실패
- CLOSED JobPosting의 Stage 수정 실패
- 존재하지 않는 Stage 수정 실패
- READY가 아닌 Stage 일반 수정 실패
- Stage 생성 요청 validation 실패 시 `ApiResponse` 형식으로 400 응답

## 실행한 테스트 명령

```bash
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.service.StageServiceTest
```

```bash
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- StageServiceTest: 성공
- StageControllerTest: 성공
- 전체 테스트: 성공

## 남은 이슈

- reorder와 상태 command가 없으므로 `IN_PROGRESS`, `RESULT_ANNOUNCED`, `CLOSED` 전이는 아직 API로 수행할 수 없다.
- `stageOrder` DB unique 제약은 아직 추가하지 않았고 Service 검증만 적용했다.
- `finalStage=true` DB 제약은 아직 추가하지 않았고 Service 검증만 적용했다.
- Service 검증만으로는 동시 요청의 중복 생성을 완전히 막을 수 없으므로 운영 안정성 단계에서 DB 제약 또는 트랜잭션 락 정책을 재검토한다.
- `Stage.update()`의 상태 방어를 Entity 내부에도 둘지 여부는 다음 리팩터링 시 검토한다. 현재는 Service 책임으로 유지한다.

## 다음 Phase 02a-2 전 확인 사항

- Phase 02a-2에서 reorder/start/announce/close/delete command를 구현했다.
- DB unique 제약은 Phase 02a-2에서도 추가하지 않고 Service 검증을 유지했다.
- start/announce/close command는 `PUBLISHED` JobPosting에서만 허용한다.
- `DRAFT` JobPosting에서는 start/announce/close를 차단한다.
- `CLOSED` JobPosting에서는 모든 Stage 변경 command를 차단한다.
- delete는 POST command API로 구현했다.

## Phase 02a-3 정합성 메모

- Phase 02a-1 문서의 구현 범위는 Stage 기본 CRUD 시점을 기준으로 유지한다.
- Phase 02a-2에서 reorder/status/delete command가 추가되었고, Phase 02a-3에서 Controller/API 계약 테스트가 보강되었다.
- Phase 02a-3 기준 `StageControllerTest`는 CRUD API 성공 응답, command API 성공 응답, validation/error 응답, PUT/DELETE HTTP method 미지원 정책을 검증한다.
- Stage 공개 노출 API, JobPosting publish Stage 최소 1개 검증, StageResult 구현은 아직 포함하지 않는다.
