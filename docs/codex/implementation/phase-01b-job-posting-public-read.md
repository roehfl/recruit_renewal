# Phase 01b - JobPosting Public Read API

## Phase 이름

Phase 01b: 지원자/공개용 채용공고 조회 API

## 구현 목적

관리자가 Phase 01a API로 생성하고 게시한 채용공고를 지원자 또는 공개 화면에서 조회할 수 있도록 공개 조회 전용 API를 구현한다. 신규 핵심 도메인은 추가하지 않고 Phase 01a의 `JobPosting`, `JobPosition`, `ApplicationFormConfig`를 재사용한다.

## 구현 범위

- 공개/지원자용 채용공고 목록 조회
- 공개/지원자용 채용공고 상세 조회
- 관리자용 DTO와 분리된 공개용 응답 DTO
- `PUBLISHED` 상태 노출 정책
- 접수기간 기반 `accepting` flag 계산
- 공개 목록 projection 조회
- 공개 상세 전용 `@EntityGraph` 조회
- 관리자 pageable 목록의 collection fetch 제거 보완
- 관리자 상세 전용 repository 메서드 추가 보완
- 관리자 `publish`/`close` 시간 처리의 `Clock` 기반 통일
- 공개 상세 모집분야 `sortOrder` null-safe 정렬
- 관리자/공개 목록 `page`, `size` 요청값 검증
- Phase 01a 충돌 마커 정리 내용 문서화

## 변경 파일 목록

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

## 신규 클래스 목록

- `TimeConfig`
- `JobPostingPublicController`
- `JobPostingPublicListProjection`
- `JobPostingPublicService`
- `JobPostingPublicListResponse`
- `JobPostingPublicDetailResponse`
- `JobPositionPublicResponse`
- `ApplicationFormConfigPublicResponse`
- `JobPostingPublicServiceTest`

## 수정 클래스 목록

- `JobPostingRepository`
- `JobPostingService`
- `JobPostingController`
- `JobPostingServiceTest`

## 클래스별 설명

| 구분 | 패키지 | 클래스 | 역할 | 주요 필드/메서드 | 연관 클래스 | 비고 |
|---|---|---|---|---|---|---|
| Configuration | `com.shinyoung.recruit.config` | `TimeConfig` | 애플리케이션 공통 `Clock` 빈 제공 | `clock()` | `JobPostingService`, `JobPostingPublicService` | 운영 기본값은 `Clock.systemDefaultZone()` |
| Controller | `com.shinyoung.recruit.controller` | `JobPostingPublicController` | 공개/지원자 채용공고 조회 API entrypoint | `getJobPostings`, `getJobPosting` | `JobPostingPublicService`, `ApiResponse`, `PageResponse` | 관리자 API와 path 분리 |
| Service | `com.shinyoung.recruit.service` | `JobPostingPublicService` | 공개 조회 정책과 `accepting` 계산 처리 | `getJobPostings`, `getJobPosting`, `validatePageRequest` | `JobPostingRepository`, `Clock`, 공개 응답 DTO | `PUBLISHED`만 조회; 숨김/없음은 동일 not-found 예외 |
| Repository | `com.shinyoung.recruit.domain.repository` | `JobPostingRepository` | 관리자/공개 JobPosting 조회 | `findAllByOrderByCreatedAtDesc`, `findDetailById`, `findAllByStatusOrderByCreatedAtDesc`, `findByIdAndStatus` | `JobPosting`, `JobPostingStatus`, `JobPostingPublicListProjection` | pageable 목록에는 collection fetch 없음; 상세에만 `@EntityGraph` |
| Repository Projection | `com.shinyoung.recruit.domain.repository` | `JobPostingPublicListProjection` | 공개 목록에 필요한 필드만 조회 | `getId`, `getTitle`, `getReceptionStartDateTime`, `getReceptionEndDateTime` | `JobPostingRepository`, `JobPostingPublicListResponse` | 공개 목록 pageable 쿼리용 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `JobPostingPublicListResponse` | 공개 목록 응답 | `id`, `title`, reception period, `accepting`, `from`, `isAccepting` | `JobPostingPublicListProjection`, `JobPostingPublicService` | DTO에서 현재 시각을 직접 조회하지 않음 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `JobPostingPublicDetailResponse` | 공개 상세 응답 | `id`, `title`, `contentHtml`, reception period, `accepting`, `jobPositions`, `applicationFormConfig`, `from` | `JobPosting`, `JobPositionPublicResponse`, `ApplicationFormConfigPublicResponse` | `sortOrder` null-safe 정렬 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `JobPositionPublicResponse` | 공개 상세 모집분야 응답 | `id`, `positionName`, `headcount`, `sortOrder`, `from` | `JobPosition` | 관리자 DTO와 분리 |
| Response DTO | `com.shinyoung.recruit.dto.response` | `ApplicationFormConfigPublicResponse` | 공개 상세 지원서 항목 설정 응답 | 7개 `use*` flag, `from` | `ApplicationFormConfig` | 관리자 DTO와 분리 |
| Controller | `com.shinyoung.recruit.controller` | `JobPostingController` | 관리자 채용공고 API | `getJobPostings`, `update` | `JobPostingService` | 충돌 정리 후 `PageResponse`와 POST 수정 정책 유지 |
| Service | `com.shinyoung.recruit.service` | `JobPostingService` | 관리자 채용공고 유스케이스 | `getJobPostings`, `getJobPosting`, `publish`, `close`, `validatePageRequest` | `JobPostingRepository`, `Clock`, 관리자 DTO | `publish`/`close`도 `Clock` 기반 |
| Test | `com.shinyoung.recruit.service` | `JobPostingServiceTest` | 관리자 보완 사항 검증 | Clock 기반 게시/마감, page/size 오류, 목록/상세 | `JobPostingService` | 고정 `Clock` 사용 |
| Test | `com.shinyoung.recruit.service` | `JobPostingPublicServiceTest` | 공개 조회 정책 검증 | 공개 목록/상세 노출, 숨김, `accepting`, nested 응답, page/size 오류 | `JobPostingPublicService`, `JobPostingService` | 고정 `Clock` 사용 |

## API 목록

| Method | Path | 설명 |
|---|---|---|
| GET | `/job-postings?page={page}&size={size}` | 공개/지원자 채용공고 목록 조회 |
| GET | `/job-postings/{id}` | 공개/지원자 채용공고 상세 조회 |

## Entity 관계 요약

- `JobPosting` 1 : N `JobPosition`
- `JobPosting` 1 : 1 `ApplicationFormConfig`
- Phase 01b는 기존 Phase 01a 관계를 조회에만 사용한다.
- 공개 목록 조회는 collection 관계를 fetch하지 않고 projection으로 필요한 목록 필드만 조회한다.
- 공개 상세 조회는 `id + PUBLISHED` 조건과 `@EntityGraph`로 모집분야/지원서 항목 설정을 함께 조회한다.
- `Application`, `Stage`, `Interview`, `Message`, `CommonCode` 도메인은 추가하지 않았다.

## 주요 비즈니스 규칙

1. 공개 목록은 `PUBLISHED` 상태 공고만 노출한다.
2. 공개 상세는 `id + PUBLISHED` 조건으로만 조회한다.
3. `DRAFT`, `CLOSED`, 존재하지 않는 공고는 공개 상세에서 모두 같은 `JobPostingNotFoundException`으로 처리한다.
4. `PUBLISHED` 공고는 접수기간과 무관하게 목록/상세에 노출한다.
5. `accepting`은 현재 시각이 접수 시작일시 이상이고 종료일시 이하일 때 `true`다.
6. 공개 `accepting` 계산은 주입된 `Clock` 기준 현재 시각으로 수행한다.
7. 공개 목록 pageable 쿼리에서는 collection 관계를 fetch하지 않는다.
8. 관리자 목록 pageable 쿼리에서도 collection 관계를 fetch하지 않는다.
9. 관리자 상세와 공개 상세에서만 `@EntityGraph`로 모집분야와 지원서 항목 설정을 함께 조회한다.
10. 목록 `page`는 0 이상, `size`는 1 이상 100 이하만 허용한다.
11. 공개 응답 DTO는 관리자 응답 DTO를 재사용하지 않는다.
12. PUT API는 추가하지 않았다.

## 테스트 목록

- `PUBLISHED_공고는_공개_목록에_노출된다`
- `DRAFT_공고는_공개_목록에_노출되지_않는다`
- `CLOSED_공고는_공개_목록에_노출되지_않는다`
- `PUBLISHED_공고_상세_조회_성공`
- `DRAFT_공고_상세는_조회할_수_없다`
- `CLOSED_공고_상세는_조회할_수_없다`
- `공개_상세_응답에_모집분야와_지원서_항목_설정이_포함된다`
- `접수기간에_따라_accepting_값을_계산한다`
- `공개_목록_페이지_요청값이_잘못되면_예외`
- `게시와_마감_시간은_Clock_기준으로_저장된다`
- `관리자_목록_페이지_요청값이_잘못되면_예외`

## 실행한 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- 결과: 성공
- 실행 결과: `BUILD SUCCESSFUL`
- 전체 테스트: 41개 통과

## 남은 이슈

- 관리자/공개 API의 세부 권한 정책은 아직 적용하지 않았다.
- 공개 목록 검색/필터는 아직 없다.
- 공개 상세의 `contentHtml` XSS 처리 책임 범위는 프론트/백엔드 경계에서 추가 확인이 필요하다.

## 다음 Phase 전 확인 사항

- Stage를 `JobPosting` 하위로 둘지 결정한다.
- 공고 생성 시 기본 전형단계를 자동 생성할지, Stage 관리 API에서 별도 생성할지 결정한다.
- Stage 타입을 enum으로 시작할지 CommonCode를 먼저 도입할지 결정한다.
- `StageResult`는 `Application` 없이 먼저 구현 가능한지 확인한다.
- 다음 Phase를 Stage 기본 관리로 갈지 Application 기본 흐름으로 갈지 결정한다.
