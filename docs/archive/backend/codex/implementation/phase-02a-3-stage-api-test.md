# Phase 02a-3 - Stage Controller/API Test

## Phase 이름

Phase 02a-3: Stage Controller/API 테스트 보강 및 문서 정합성 점검

## 목적

Phase 02a-1, Phase 02a-2에서 구현한 관리자 Stage API의 HTTP path, method, `ApiResponse<T>` 응답 포맷, validation/error 응답을 MockMvc 테스트로 고정한다. 이번 Phase는 신규 도메인이나 신규 기능 구현이 아니라 API 계약 검증과 문서 정합성 점검이다.

## 구현 범위

- `StageControllerTest` 보강
- Stage CRUD API 성공 응답 포맷 검증
- Stage reorder/status/delete command API 성공 응답 포맷 검증
- validation 실패, Stage 미존재, 잘못된 status command 실패 응답 포맷 검증
- PUT 및 DELETE HTTP method 미지원 정책 검증
- Phase 02a 문서 정합성 보완

## 변경 파일 목록

### 테스트 변경

- `src/test/java/com/shinyoung/recruit/controller/StageControllerTest.java`

### 문서 변경

- `docs/codex/implementation/phase-02a-3-stage-api-test.md`
- `docs/codex/implementation/phase-02a-1-stage-basic-crud.md`
- `docs/codex/implementation/phase-02a-2-stage-command.md`
- `docs/codex/design/phase-02-stage-design.md`
- `docs/codex/07-implementation-history.md`

### 코드 변경

- 없음

### 설정 변경

- 없음

## 추가/수정 테스트 목록

### CRUD API 테스트

- `GET /admin/job-postings/{jobPostingId}/stages` 목록 조회 성공
- `GET /admin/job-postings/{jobPostingId}/stages/{stageId}` 상세 조회 성공
- `POST /admin/job-postings/{jobPostingId}/stages` 생성 성공
- `POST /admin/job-postings/{jobPostingId}/stages/{stageId}` 수정 성공

### Command API 테스트

- `POST /admin/job-postings/{jobPostingId}/stages/reorder` 성공
- `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/start` 성공
- `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/announce` 성공
- `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/close` 성공
- `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete` 성공

### Validation/Error 테스트

- Stage 생성 요청 validation 실패 시 `400` + `ApiResponse.fail`
- Stage reorder 요청 validation 실패 시 `400` + `ApiResponse.fail`
- 존재하지 않는 Stage 상세 조회 시 `404` + `ApiResponse.fail`
- 잘못된 상태 command 요청 시 `400` + `ApiResponse.fail`
- PUT method 미지원 확인
- DELETE HTTP method 미지원 확인

## 검증한 API 목록

| Method | Path | 검증 내용 |
|---|---|---|
| GET | `/admin/job-postings/{jobPostingId}/stages` | 목록 조회 성공 응답 |
| GET | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | 상세 조회 성공 및 Stage 미존재 실패 응답 |
| POST | `/admin/job-postings/{jobPostingId}/stages` | 생성 성공 및 validation 실패 응답 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}` | 수정 성공 응답 |
| POST | `/admin/job-postings/{jobPostingId}/stages/reorder` | reorder 성공 및 validation 실패 응답 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/start` | start 성공 응답 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/announce` | announce 성공 및 잘못된 상태 실패 응답 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/close` | close 성공 응답 |
| POST | `/admin/job-postings/{jobPostingId}/stages/{stageId}/delete` | delete command 성공 응답 |

## HTTP method 정책

- Stage 수정은 `POST /admin/job-postings/{jobPostingId}/stages/{stageId}`를 사용한다.
- Stage command API는 모두 POST를 사용한다.
- Stage 삭제도 HTTP DELETE가 아니라 `POST /admin/job-postings/{jobPostingId}/stages/{stageId}/delete`를 사용한다.
- `PUT /admin/job-postings/{jobPostingId}/stages/{stageId}`는 지원하지 않는다.
- `DELETE /admin/job-postings/{jobPostingId}/stages/{stageId}`는 지원하지 않는다.

## 응답 포맷 검증 결과

- 성공 응답은 `success=true`, `message`, `data` 구조를 검증했다.
- 실패 응답은 `success=false`, `message` 구조를 검증했다.
- Bean Validation 실패는 `GlobalExceptionHandler`의 `MethodArgumentNotValidException` 처리로 `ApiResponse.fail(...)` 형태의 `400 BAD_REQUEST`를 반환한다.
- Stage 미존재는 `StageNotFoundException` 처리로 `ApiResponse.fail(...)` 형태의 `404 NOT_FOUND`를 반환한다.
- 잘못된 상태 command는 `InvalidStageException` 처리로 `ApiResponse.fail(...)` 형태의 `400 BAD_REQUEST`를 반환한다.

## 테스트 명령

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests com.shinyoung.recruit.controller.StageControllerTest
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat clean test
```

## 테스트 결과

- `StageControllerTest`: 성공
- 전체 `clean test`: 성공

## 남은 이슈

- `stageOrder`, `finalStage` 중복은 아직 Service 검증 중심이며 DB unique 제약은 보류 상태다.
- 동시 요청까지 포함한 최종 중복 방어는 운영 안정성 검토 단계에서 DB 제약 또는 락 정책으로 재검토해야 한다.
- Stage 공개 노출 API는 아직 없다.
- JobPosting publish 조건에 Stage 최소 1개 검증은 아직 추가하지 않았다.
- StageResult는 Application 도메인 이후로 계속 보류한다.

## 다음 Phase 추천

- 다음 Phase는 Stage 공개 노출 API나 publish 조건 보강보다 Application 기본 흐름을 우선 추천한다.
- 이유는 StageResult가 Application FK 없이 정합성 있게 구현되기 어렵고, Stage 공개 노출/게시 조건 강화도 실제 지원서 흐름과 함께 정책을 확정하는 편이 안전하기 때문이다.
- Stage 공개 노출은 지원자 화면에서 전형단계 표시가 실제로 필요한 시점에 별도 Phase로 분리한다.
- JobPosting publish 시 Stage 최소 1개 검증은 기존 Phase 01a/01b 동작과 테스트 영향이 있으므로 Application 기본 흐름 이후 별도 보완 Phase에서 검토한다.
