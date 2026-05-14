# Phase 01b - Public JobPosting Read API

## 1. Phase name

Phase 01b: 지원자/공개용 채용공고 조회 API

## 2. 구현 목적

관리자가 Phase 01a API로 생성하고 게시한 채용공고를 지원자 또는 공개 화면에서 조회할 수 있도록 공개 조회 전용 API를 추가했다. 신규 핵심 도메인은 만들지 않고 기존 `JobPosting`, `JobPosition`, `ApplicationFormConfig`를 사용했다.

## 3. 구현 범위

- Phase 01a 충돌 마커 정리
  - `JobPostingRepository`, `JobPostingService`, `JobPostingController`, `JobPostingServiceTest`
  - 관리자 목록은 `PageResponse` 유지
  - 관리자 수정은 `POST /admin/job-postings/{id}` 유지
  - 일반 관리자 수정 API에서 status를 받거나 변경하지 않음
- 공개 채용공고 목록 조회
  - `GET /job-postings?page={page}&size={size}`
  - `PUBLISHED` 상태만 노출
  - 접수기간 기준 `accepting` 포함
- 공개 채용공고 상세 조회
  - `GET /job-postings/{id}`
  - `PUBLISHED` 상태만 조회 가능
  - 모집분야와 지원서 항목 설정 포함
- 공개용 DTO 분리
  - 관리자 DTO를 재사용하지 않고 공개 화면용 응답 DTO를 별도 추가
- 테스트
  - 공개 목록/상세 노출 정책과 `accepting` 계산 검증
- 리뷰 보완
  - 관리자 pageable 목록 조회에서 collection `@EntityGraph` 제거
  - 관리자 상세 조회 전용 `findDetailById` 추가
  - 관리자 `publish`/`close` 시간도 `Clock` 기반으로 통일
  - 공개 상세 모집분야 정렬을 null-safe 처리
  - 관리자/공개 목록의 `page`, `size` 요청값 검증 추가

## 4. 변경 파일 목록

- `src/main/java/com/shinyoung/recruit/config/TimeConfig.java`
- `src/main/java/com/shinyoung/recruit/controller/JobPostingController.java`
- `src/main/java/com/shinyoung/recruit/controller/JobPostingPublicController.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingPublicListProjection.java`
- `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormConfigPublicResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPositionPublicResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingPublicListResponse.java`
- `src/main/java/com/shinyoung/recruit/service/JobPostingService.java`
- `src/main/java/com/shinyoung/recruit/service/JobPostingPublicService.java`
- `src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java`
- `src/test/java/com/shinyoung/recruit/service/JobPostingPublicServiceTest.java`
- `docs/codex/implementation/phase-01b-job-posting-public-read.md`
- `docs/codex/07-implementation-history.md`

## 5. 신규 클래스 목록

- `TimeConfig`
- `JobPostingPublicController`
- `JobPostingPublicListProjection`
- `ApplicationFormConfigPublicResponse`
- `JobPositionPublicResponse`
- `JobPostingPublicDetailResponse`
- `JobPostingPublicListResponse`
- `JobPostingPublicService`
- `JobPostingPublicServiceTest`

## 6. 수정 클래스 목록

- `JobPostingRepository`
- `JobPostingService`
- `JobPostingPublicService`
- `JobPostingPublicDetailResponse`
- `JobPostingController`
- `JobPostingServiceTest`
- `JobPostingPublicServiceTest`

## 7. 클래스별 설명

### `com.shinyoung.recruit.config.TimeConfig`
- class type: Configuration
- responsibility: 애플리케이션 공통 `Clock` 빈 제공
- key fields or methods: `clock()`
- related classes: `JobPostingPublicService`
- implementation notes: 공개 조회의 `accepting` 계산을 테스트 가능하게 하기 위해 `Clock.systemDefaultZone()`을 빈으로 제공한다.

### `com.shinyoung.recruit.controller.JobPostingPublicController`
- class type: Controller
- responsibility: 공개/지원자용 채용공고 조회 API entrypoint
- key fields or methods:
  - `getJobPostings(int page, int size)`
  - `getJobPosting(Long id)`
- related classes: `JobPostingPublicService`, `ApiResponse`, `PageResponse`
- implementation notes: 관리자 API와 path/controller를 분리했다.

### `com.shinyoung.recruit.domain.repository.JobPostingPublicListProjection`
- class type: Repository Projection
- responsibility: 공개 목록 조회에 필요한 `JobPosting` 필드만 조회하기 위한 projection
- key fields or methods:
  - `getId()`
  - `getTitle()`
  - `getReceptionStartDateTime()`
  - `getReceptionEndDateTime()`
- related classes: `JobPostingRepository`, `JobPostingPublicListResponse`
- implementation notes: pageable 목록 쿼리에서 collection 관계를 fetch하지 않도록 별도 projection을 사용한다.

### `com.shinyoung.recruit.domain.repository.JobPostingRepository`
- class type: Repository
- responsibility: 관리자/공개 채용공고 조회 persistence entry
- key fields or methods:
  - `findAllByOrderByCreatedAtDesc(Pageable pageable)`
  - `findDetailById(Long id)`
  - `findAllByStatusOrderByCreatedAtDesc(JobPostingStatus status, Pageable pageable)`
  - `findByIdAndStatus(Long id, JobPostingStatus status)`
- related classes: `JobPosting`, `JobPostingStatus`, `JobPostingPublicListProjection`
- implementation notes:
  - Phase 01a 충돌 마커를 제거하고 관리자 목록 `PageResponse` 흐름을 유지했다.
  - 관리자 pageable 목록 조회에서는 collection `@EntityGraph`를 제거했다.
  - 관리자 상세 조회는 별도 `findDetailById`에서만 `jobPositions`, `applicationFormConfig`를 `@EntityGraph`로 조회한다.
  - 공개 목록은 status 조건과 projection을 사용한다.
  - 공개 상세는 `@EntityGraph`로 `jobPositions`, `applicationFormConfig`를 함께 조회한다.

### `com.shinyoung.recruit.service.JobPostingPublicService`
- class type: Service
- responsibility: 공개 조회 정책과 `accepting` 계산 처리
- key fields or methods:
  - `getJobPostings(int page, int size)`
  - `getJobPosting(Long id)`
- related classes: `JobPostingRepository`, `Clock`, 공개 응답 DTO, `JobPostingNotFoundException`
- implementation notes:
  - `@Transactional(readOnly = true)`를 사용한다.
  - `page`는 0 이상, `size`는 1 이상 100 이하로 검증한다.
  - `LocalDateTime.now(clock)`을 서비스에서 한 번 계산해 DTO factory로 전달한다.
  - 숨김 상태 또는 존재하지 않는 상세 조회는 같은 `JobPostingNotFoundException`으로 처리한다.

### `com.shinyoung.recruit.dto.response.JobPostingPublicListResponse`
- class type: Response DTO
- responsibility: 공개 목록 응답 모델
- key fields or methods:
  - fields: `id`, `title`, `receptionStartDateTime`, `receptionEndDateTime`, `accepting`
  - `from(JobPostingPublicListProjection, LocalDateTime now)`
  - `isAccepting(LocalDateTime start, LocalDateTime end, LocalDateTime now)`
- related classes: `JobPostingPublicListProjection`, `JobPostingPublicService`
- implementation notes: DTO 내부에서 직접 현재 시각을 조회하지 않는다.

### `com.shinyoung.recruit.dto.response.JobPostingPublicDetailResponse`
- class type: Response DTO
- responsibility: 공개 상세 응답 모델
- key fields or methods:
  - fields: `id`, `title`, `contentHtml`, reception period, `accepting`, `jobPositions`, `applicationFormConfig`
  - `from(JobPosting, LocalDateTime now)`
- related classes: `JobPosting`, `JobPositionPublicResponse`, `ApplicationFormConfigPublicResponse`
- implementation notes: 모집분야는 `sortOrder` 기준으로 정렬하며, 혹시 모를 null 값은 마지막으로 보낸다.

### `com.shinyoung.recruit.dto.response.JobPositionPublicResponse`
- class type: Response DTO
- responsibility: 공개 상세의 모집분야 응답 모델
- key fields or methods:
  - fields: `id`, `positionName`, `headcount`, `sortOrder`
  - `from(JobPosition)`
- related classes: `JobPosition`, `JobPostingPublicDetailResponse`
- implementation notes: 관리자 DTO와 분리했다.

### `com.shinyoung.recruit.dto.response.ApplicationFormConfigPublicResponse`
- class type: Response DTO
- responsibility: 공개 상세의 지원서 항목 설정 응답 모델
- key fields or methods:
  - fields: `useEducation`, `useCareer`, `useCertificate`, `useLanguage`, `useMilitary`, `useAward`, `useGapPeriod`
  - `from(ApplicationFormConfig)`
- related classes: `ApplicationFormConfig`, `JobPostingPublicDetailResponse`
- implementation notes: 관리자 DTO와 분리했다.

### `com.shinyoung.recruit.controller.JobPostingController`
- class type: Controller
- responsibility: 관리자용 채용공고 API
- key fields or methods: `getJobPostings(int page, int size)`, `update(Long id, JobPostingUpdateRequest request)`
- related classes: `JobPostingService`
- implementation notes: 충돌 마커 정리 후 관리자 목록 `PageResponse`와 `POST /admin/job-postings/{id}` 수정 정책을 유지했다.

### `com.shinyoung.recruit.service.JobPostingService`
- class type: Service
- responsibility: 관리자용 채용공고 생성/수정/게시/마감 및 관리자 조회
- key fields or methods: `getJobPostings(int page, int size)`, `getJobPosting(Long id)`, `update`, `publish`, `close`
- related classes: `JobPostingRepository`, 관리자 DTO, `JobPostingStatus`
- implementation notes:
  - 일반 수정 API에서 status 변경 경로는 추가하지 않았다.
  - `publish`/`close`는 `LocalDateTime.now(clock)`을 사용한다.
  - `page`는 0 이상, `size`는 1 이상 100 이하로 검증한다.

### `com.shinyoung.recruit.service.JobPostingServiceTest`
- class type: Test
- responsibility: 관리자용 JobPosting 서비스 테스트
- key fields or methods: `목록_상세_조회_확인`, `게시와_마감_시간은_Clock_기준으로_저장된다`, `관리자_목록_페이지_요청값이_잘못되면_예외`
- related classes: `JobPostingService`
- implementation notes: 관리자 목록 `PageResponse`, 관리자 상세 조회, `Clock` 기반 게시/마감 시각, page/size 검증을 확인한다.

### `com.shinyoung.recruit.service.JobPostingPublicServiceTest`
- class type: Test
- responsibility: 공개 조회 정책과 응답 구성을 검증
- key fields or methods:
  - 공개 목록 노출/숨김 테스트
  - 공개 상세 노출/숨김 테스트
  - 모집분야/지원서 설정 포함 테스트
  - `accepting` 계산 테스트
  - page/size 검증 테스트
- related classes: `JobPostingPublicService`, `JobPostingService`, `Clock`
- implementation notes: `@TestConfiguration`으로 고정 `Clock`을 주입해 시간 의존 테스트를 안정화했다.

## 8. API 목록

### 공개/지원자 API

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/job-postings?page={page}&size={size}` | 공개 채용공고 목록 조회 |
| GET | `/job-postings/{id}` | 공개 채용공고 상세 조회 |

### 유지된 관리자 API

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/admin/job-postings?page={page}&size={size}` | 관리자 채용공고 목록 조회 |
| POST | `/admin/job-postings/{id}` | 관리자 채용공고 일반 수정 |

## 9. Entity 관계 요약

- `JobPosting` 1 : N `JobPosition`
- `JobPosting` 1 : 1 `ApplicationFormConfig`
- Phase 01b는 기존 관계를 조회에만 사용한다.
- `Application`, `Stage`, `Interview`, `Message`, `CommonCode` 도메인은 추가하지 않았다.

## 10. 주요 비즈니스 규칙

1. 공개 목록은 `PUBLISHED` 상태 공고만 노출한다.
2. 공개 상세는 `id + PUBLISHED` 조건으로만 조회한다.
3. `DRAFT`, `CLOSED`, 존재하지 않는 공고 상세는 모두 `JobPostingNotFoundException`으로 처리한다.
4. `PUBLISHED` 공고는 접수기간과 무관하게 목록/상세에 노출한다.
5. `accepting`은 현재 시각이 접수 시작일시 이상이고 종료일시 이하일 때 `true`다.
6. 공개 목록 pageable 쿼리는 collection 관계를 fetch하지 않는다.
7. 관리자 목록 pageable 쿼리도 collection 관계를 fetch하지 않는다.
8. 관리자 상세와 공개 상세 쿼리에서만 `@EntityGraph`로 모집분야와 지원서 설정을 함께 조회한다.
9. 관리자 `publish`/`close` 시각은 공통 `Clock`을 기준으로 저장한다.
10. 목록 `page`는 0 이상, `size`는 1 이상 100 이하만 허용한다.
11. 관리자 일반 수정 API는 status를 받거나 변경하지 않는다.
12. PUT API는 추가하지 않았다.

## 11. 테스트 목록

- `JobPostingServiceTest`
  - Phase 01a 충돌 마커 정리 후 관리자 목록 `PageResponse` 흐름 유지 확인
  - `게시와_마감_시간은_Clock_기준으로_저장된다`
  - `관리자_목록_페이지_요청값이_잘못되면_예외`
- `JobPostingPublicServiceTest`
  - `PUBLISHED_공고는_공개_목록에_노출된다`
  - `DRAFT_공고는_공개_목록에_노출되지_않는다`
  - `CLOSED_공고는_공개_목록에_노출되지_않는다`
  - `PUBLISHED_공고_상세_조회_성공`
  - `DRAFT_공고_상세는_조회할_수_없다`
  - `CLOSED_공고_상세는_조회할_수_없다`
  - `공개_상세_응답에_모집분야와_지원서_항목_설정이_포함된다`
  - `접수기간에_따라_accepting_값을_계산한다`
  - `공개_목록_페이지_요청값이_잘못되면_예외`

## 12. 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 13. 테스트 결과

- 결과: 성공
- 실행 결과: `BUILD SUCCESSFUL`
- 전체 테스트: 41개 통과

## 14. 남은 이슈

- 관리자/공개 API에 대한 세부 권한 정책은 아직 적용하지 않았다. 현재 `SecurityConfig`는 개발 단계상 전체 permitAll 상태다.
- 공개 목록 검색/필터는 아직 없다.
- 공개 목록 응답은 현재 최소 필드만 제공한다.
- `build/`, `.gradle/`, `logs/`는 테스트 실행 과정에서 생성될 수 있는 로컬 산출물이다.

## 15. 다음 Phase 전 확인 사항

- Phase 02 또는 Application 기본 흐름 착수 전 `ApplicationFormConfig`의 `use*` 플래그가 필수 입력 검증으로 확장될지 결정한다.
- 지원서 생성 API에서는 `PUBLISHED` 상태와 접수기간 모두를 서버에서 검증해야 한다.
- 공개 공고 상세의 `contentHtml` 노출 정책과 XSS 처리 책임을 프론트/백엔드 경계에서 재확인한다.
