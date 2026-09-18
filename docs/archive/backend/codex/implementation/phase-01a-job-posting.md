# Phase 01a - JobPosting Admin Management

## Phase 이름

Phase 01a: 관리자 채용공고 관리

## 구현 목적

관리자가 채용공고를 생성, 조회, 수정, 게시, 마감할 수 있는 첫 번째 JobPosting vertical slice를 구현한다. 이 단계는 `JobPosting`, `JobPosition`, `ApplicationFormConfig`만 다루며 `Application`, `Stage`, `Interview`, `Message`, `CommonCode` 도메인은 추가하지 않는다.

## 구현 범위

- 관리자 채용공고 목록/상세 조회
- 관리자 채용공고 생성/일반 수정
- 별도 command API를 통한 게시/마감 상태 전이
- 채용공고, 모집분야, 지원서 항목 설정 엔티티와 저장소
- 관리자용 요청/응답 DTO
- JobPosting 전용 예외와 공통 예외 매핑
- 관리자 서비스 테스트

## 변경 파일 목록

- `src/main/java/com/shinyoung/recruit/controller/JobPostingController.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/JobPosting.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/JobPosition.java`
- `src/main/java/com/shinyoung/recruit/domain/entity/ApplicationFormConfig.java`
- `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingRepository.java`
- `src/main/java/com/shinyoung/recruit/enumeration/JobPostingStatus.java`
- `src/main/java/com/shinyoung/recruit/service/JobPostingService.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPostingCreateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPostingUpdateRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/JobPositionRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/request/ApplicationFormConfigRequest.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingListResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPostingDetailResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/JobPositionResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormConfigResponse.java`
- `src/main/java/com/shinyoung/recruit/dto/response/PageResponse.java`
- `src/main/java/com/shinyoung/recruit/exception/JobPostingNotFoundException.java`
- `src/main/java/com/shinyoung/recruit/exception/InvalidJobPostingException.java`
- `src/main/java/com/shinyoung/recruit/exception/GlobalExceptionHandler.java`
- `src/test/java/com/shinyoung/recruit/service/JobPostingServiceTest.java`

## 신규 클래스 목록

- `JobPosting`
- `JobPosition`
- `ApplicationFormConfig`
- `JobPostingRepository`
- `JobPostingStatus`
- `JobPostingService`
- `JobPostingController`
- `JobPostingCreateRequest`
- `JobPostingUpdateRequest`
- `JobPositionRequest`
- `ApplicationFormConfigRequest`
- `JobPostingListResponse`
- `JobPostingDetailResponse`
- `JobPositionResponse`
- `ApplicationFormConfigResponse`
- `JobPostingNotFoundException`
- `InvalidJobPostingException`
- `JobPostingServiceTest`

## 수정 클래스 목록

- `PageResponse`
- `GlobalExceptionHandler`
- `JobPostingRepository`
- `JobPostingService`
- `JobPostingController`
- `JobPostingServiceTest`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Entity | `com.shinyoung.recruit.domain.entity` | `JobPosting` | 채용공고 aggregate root | `title`, `contentHtml`, `receptionStartDateTime`, `receptionEndDateTime`, `status`, `publishedAt`, `closedAt`, `create`, `updateBasicInfo`, `replaceJobPositions`, `updateApplicationFormConfig`, `publish`, `close` | `JobPosition`, `ApplicationFormConfig`, `JobPostingStatus` | 생성 시 `DRAFT`; 상태 변경은 별도 메서드로만 수행 |
| Entity | `com.shinyoung.recruit.domain.entity` | `JobPosition` | 공고별 모집분야 | `jobPosting`, `positionName`, `sortOrder`, `create`, `assignJobPosting` | `JobPosting` | `JobPosting`과 N:1, LAZY |
| Entity | `com.shinyoung.recruit.domain.entity` | `ApplicationFormConfig` | 공고별 지원서 항목 사용 설정 | `useEducation`, `useCareer`, `useCertificate`, `useLanguage`, `useMilitary`, `useAward`, `useGapPeriod`, `create`, `assignJobPosting` | `JobPosting` | `JobPosting`과 1:1, `job_posting_id` unique |
| Repository | `com.shinyoung.recruit.domain.repository` | `JobPostingRepository` | JobPosting 저장/조회 | `findAllByOrderByCreatedAtDesc(Pageable)`, `findDetailById(Long)` | `JobPosting` | pageable 목록에는 collection fetch 없음; 상세 전용 조회에만 `@EntityGraph` 사용 |
| Enum | `com.shinyoung.recruit.enumeration` | `JobPostingStatus` | 공고 상태 값 | `DRAFT`, `PUBLISHED`, `CLOSED` | `JobPosting`, `JobPostingService` | 엔티티에서는 `EnumType.STRING` 저장 |
| Service | `com.shinyoung.recruit.service` | `JobPostingService` | 관리자 공고 조회/생성/수정/게시/마감 유스케이스 | `getJobPostings`, `getJobPosting`, `create`, `update`, `publish`, `close`, `validatePageRequest` | `JobPostingRepository`, 관리자 DTO, `Clock` | `publish`/`close`는 `LocalDateTime.now(clock)` 사용 |
| Controller | `com.shinyoung.recruit.controller` | `JobPostingController` | 관리자 REST API entrypoint | `getJobPostings`, `getJobPosting`, `create`, `update`, `publish`, `close` | `JobPostingService`, `ApiResponse`, `PageResponse` | 일반 수정은 `POST /admin/job-postings/{id}` |
| Request DTO | `com.shinyoung.recruit.dto.request` | `JobPostingCreateRequest` | 공고 생성 요청 | `title`, `contentHtml`, `receptionStartDateTime`, `receptionEndDateTime`, `jobPositions`, `applicationFormConfig` | `JobPositionRequest`, `ApplicationFormConfigRequest` | status 필드 없음; `jobPositions`는 `@NotEmpty` |
| Request DTO | `com.shinyoung.recruit.dto.request` | `JobPostingUpdateRequest` | 공고 일반 수정 요청 | `title`, `contentHtml`, `receptionStartDateTime`, `receptionEndDateTime`, `jobPositions`, `applicationFormConfig` | `JobPositionRequest`, `ApplicationFormConfigRequest` | status 필드 없음 |
| Request DTO | `com.shinyoung.recruit.dto.request` | `JobPositionRequest` | 모집분야 요청 | `positionName`, `sortOrder` | `JobPostingCreateRequest`, `JobPostingUpdateRequest` | `sortOrder`는 `@NotNull @Min(0)` |
| Request DTO | `com.shinyoung.recruit.dto.request` | `ApplicationFormConfigRequest` | 지원서 항목 설정 요청 | `useEducation`, `useCareer`, `useCertificate`, `useLanguage`, `useMilitary`, `useAward`, `useGapPeriod` | 생성/수정 요청 DTO | boolean flag 범위 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `JobPostingListResponse` | 관리자 목록 응답 | `id`, `title`, reception period, `status`, `publishedAt`, `closedAt`, `from` | `JobPosting` | `PageResponse<JobPostingListResponse>`로 반환 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `JobPostingDetailResponse` | 관리자 상세 응답 | 공고 본문, 상태, 모집분야 목록, 지원서 항목 설정, `from` | `JobPosting`, `JobPositionResponse`, `ApplicationFormConfigResponse` | 모집분야는 `sortOrder` 기준 정렬 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `JobPositionResponse` | 관리자 모집분야 응답 | `id`, `positionName`, `sortOrder`, `from` | `JobPosition` | 관리자 DTO |
| Response DTO | `com.shinyoung.recruit.dto.response` | `ApplicationFormConfigResponse` | 관리자 지원서 항목 설정 응답 | 7개 `use*` flag, `from` | `ApplicationFormConfig` | 관리자 DTO |
| Response DTO | `com.shinyoung.recruit.dto.response` | `PageResponse` | 페이징 응답 공통 모델 | `content`, `page`, `size`, `totalElements`, `totalPages`, `last`, `from` | Spring Data `Page` | 관리자 목록에서 사용 |
| Exception | `com.shinyoung.recruit.exception` | `JobPostingNotFoundException` | 공고 없음 예외 | 생성자 | `JobPostingService`, `GlobalExceptionHandler` | 404로 매핑 |
| Exception | `com.shinyoung.recruit.exception` | `InvalidJobPostingException` | 공고 비즈니스 규칙 위반 예외 | 생성자 | `JobPostingService`, `GlobalExceptionHandler` | 400으로 매핑 |
| Exception | `com.shinyoung.recruit.exception` | `GlobalExceptionHandler` | JobPosting 예외를 HTTP 응답으로 변환 | `handleJobPostingNotFound`, `handleInvalidJobPosting` | `ApiResponse` | 기존 응답 규격 유지 |
| Test | `com.shinyoung.recruit.service` | `JobPostingServiceTest` | 관리자 서비스 규칙 검증 | 생성/수정 검증, 상태 전이, 목록/상세, Clock, page/size 테스트 | `JobPostingService`, `JobPostingRepository` | 고정 `Clock`으로 시간 의존 테스트 안정화 |

## API 목록

| Method | Path | 설명 |
|---|---|---|
| GET | `/admin/job-postings?page={page}&size={size}` | 관리자 채용공고 목록 조회 |
| GET | `/admin/job-postings/{id}` | 관리자 채용공고 상세 조회 |
| POST | `/admin/job-postings` | 관리자 채용공고 생성 |
| POST | `/admin/job-postings/{id}` | 관리자 채용공고 일반 수정 |
| POST | `/admin/job-postings/{id}/publish` | 관리자 채용공고 게시 |
| POST | `/admin/job-postings/{id}/close` | 관리자 채용공고 마감 |

## Entity 관계 요약

- `JobPosting` 1 : N `JobPosition`
- `JobPosting` 1 : 1 `ApplicationFormConfig`
- `JobPosition.jobPosting`은 LAZY N:1 관계다.
- `ApplicationFormConfig.jobPosting`은 LAZY 1:1 관계이며 `job_posting_id`가 unique다.
- `JobPosting` 저장/수정 시 모집분야와 지원서 항목 설정은 cascade/orphanRemoval 정책으로 aggregate 내부에서 관리된다.

## 주요 비즈니스 규칙

1. 공고 제목과 본문은 공백일 수 없다.
2. 접수 종료일시는 접수 시작일시보다 이후여야 한다.
3. 모집분야는 최소 1개 이상 필요하다.
4. 지원서 항목 설정은 생성/수정 요청에 항상 포함되어야 한다.
5. 신규 공고는 항상 `DRAFT` 상태로 생성된다.
6. 일반 수정 API는 status를 받거나 변경하지 않는다.
7. 일반 수정은 `CLOSED` 상태에서 차단된다.
8. `DRAFT -> PUBLISHED` 전환은 가능하다.
9. `PUBLISHED -> CLOSED` 전환은 가능하다.
10. `CLOSED -> PUBLISHED` 재전환은 차단된다.
11. `publish`/`close` 시각은 주입된 `Clock` 기준으로 저장한다.
12. 관리자 목록 `page`는 0 이상, `size`는 1 이상 100 이하만 허용한다.
13. 관리자 pageable 목록 조회에서는 collection 관계를 fetch하지 않는다.
14. 관리자 상세 조회에만 `@EntityGraph`로 `jobPositions`, `applicationFormConfig`를 함께 조회한다.
15. PUT API는 사용하지 않는다.

## 테스트 목록

- `JobPosting_생성_성공`
- `접수종료일시가_시작보다_빠르면_생성실패`
- `모집분야_없이_생성불가`
- `DRAFT에서_PUBLISHED_전환_성공`
- `CLOSED_상태에서_PUBLISHED_재전환_불가`
- `PUBLISHED에서_CLOSED_전환_성공`
- `게시와_마감_시간은_Clock_기준으로_저장된다`
- `존재하지않는_공고_조회시_예외`
- `목록_상세_조회_확인`
- `관리자_목록_페이지_요청값이_잘못되면_예외`
- `모집분야_없이_수정불가`

## 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- 결과: 성공
- 실행 결과: `BUILD SUCCESSFUL`
- 전체 테스트: 41개 통과

## 남은 이슈

- 관리자 API 세부 권한 정책은 아직 적용하지 않았다.
- 관리자 목록 검색/필터 조건은 아직 없다.
- 게시/마감 시 감사 로그나 알림 발송 같은 부가 효과는 아직 없다.

## 다음 Phase 전 확인 사항

- Stage를 `JobPosting` 하위 흐름으로 둘지 확정한다.
- 공고 생성 시 기본 전형단계를 자동 생성할지 별도 Stage 관리 API에서 생성할지 결정한다.
- Application 도메인 도입 시 `ApplicationFormConfig` flag가 실제 입력 필수 여부로 어떻게 연결되는지 정의한다.
