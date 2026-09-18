# form-page 응답에 채용유형(postingType) 추가 — 설계서

- 날짜: 2026-07-06
- 슬라이스: 지원서 작성 폼 (ApplicationFormView / `form-page`)
- 방향: 백엔드 → 프론트 (프론트 성적 분기 렌더링은 후속 슬라이스)

## 1. 배경 / 문제

학력 성적 입력을 채용유형에 따라 다르게 하려 한다.

- 공개채용·인턴채용(신입 계열): 학기별 성적 입력
- 경력채용·수시채용(경력 계열): 평균 성적만 입력

프론트가 이 분기를 하려면 지원서 폼 조회 응답(`GET /applications/{applicationId}/form-page`)에서 **채용유형**을 알아야 한다. 현재 `ApplicationFormPageResponse`에는 채용유형이 없다.

## 2. 목표 / 비목표

### 목표
- `ApplicationFormPageResponse`에 기존 enum `JobPostingType`을 `postingType` 필드로 노출한다.
- `api-contract.md`에 form-page 계약 섹션을 신설하고 `postingType`을 기재한다.

### 비목표 (YAGNI)
- **새 enum(성적 입력 모드 타입) 추가 — 하지 않음.** 기존 `JobPostingType`을 그대로 노출한다.
- **학력 검증(제약) 변경 — 하지 않음.** `ApplicationEducationService` 검증 로직은 현행 유지.
- 유형별 성적 필드 show/hide 렌더링 — 프론트 후속 슬라이스(학력 입력 UI 미존재).
- 유형→모드 매핑을 백엔드에 두는 것 — 이번엔 프론트가 raw `JobPostingType`으로 분기.

## 3. 변경 내용

### 3.1 응답 DTO
`ApplicationFormPageResponse`(record)에 `JobPostingType postingType`를 추가한다.

- `from(...)`에서 `jobPosting.getPostingType()`로 매핑.
- `com.shinyoung.recruit.enumeration.JobPostingType` import 추가.
- 직렬화는 같은 응답의 `jobPostingStatus`(`JobPostingStatus`) 등과 동일하게 enum → 문자열.

서비스(`ApplicationFormPageService`) 코드는 변경 불필요 — 응답 팩토리 `from()`이 `application`/`jobPosting`에 이미 접근한다.

### 3.2 검증 — 변경 없음
`ApplicationEducationService`의 학력 검증은 그대로 둔다. 현행 규칙:

- 비고졸: 평균평점(`overallGradePoint`/`overallMaxGradePoint`) 필수, 학기별 성적 선택.
- 고졸: 학기별 금지, 평균 선택.

이 규칙과 유형별 프론트 분기의 정합성(**중요 전제**):

- **경력·수시("평균만")**: 학기별을 보내지 않아도 됨(학기별이 이미 선택) → 백엔드 무변경으로 성립.
- **공개·인턴("학기별")**: 학기별과 함께 **평균도 계속 받아야** 저장된다(비고졸 평균 필수). 공채 화면에서 평균 입력란을 완전히 숨기면 저장이 400으로 막히므로 숨기지 않는다.

## 4. API 계약 변경

- `GET /applications/{applicationId}/form-page` 응답에 `postingType` 추가. 값: `"PUBLIC_RECRUITMENT" | "EXPERIENCED_RECRUITMENT" | "INTERN_RECRUITMENT" | "ROLLING_RECRUITMENT"`.
- `api-contract.md`에 form-page 섹션 신설(응답 필드 모양 요약 + `postingType` 명시). 🟡 초안 → 구현·검증 후 🟢.
- `POST /educations`, `GET /educations` 계약 불변.

## 5. 변경 파일

### 백엔드 (`recruit_back/recruit_backend/`)
- `dto/response/ApplicationFormPageResponse.java` — `postingType` 필드 + `from()` 매핑 + import.
- `test/.../service/ApplicationFormPageServiceTest.java` — 응답에 `postingType` 포함 및 유형별 값 검증.
- (form-page 컨트롤러 테스트가 있으면) 응답 JSON에 `postingType` 직렬화 확인.

### 계약
- `recruit/api-contract.md` — form-page 섹션 신설.

### 문서(경량)
통합 화면 슬라이스이므로 백엔드 md+html 풀세트 대신 `api-contract.md` 갱신 + 보고 요약으로 갈음한다(하네스 `CLAUDE.md` §7).

## 6. 엣지 케이스 / 마이그레이션

- DB/엔티티 변경 없음 → 마이그레이션·백필 불필요.
- `JobPosting.postingType` 기본값은 `PUBLIC_RECRUITMENT`. 유형 미설정 공고는 공개채용으로 응답한다. 경력·수시 공고는 명시적으로 유형이 설정돼 있어야 한다.
- 검증 무변경이므로 기존 지원서/저장 흐름에 회귀 위험 없음.

## 7. 검증 방법

- 백엔드(수정 범위만):
  ```powershell
  $env:AES_SECRET_KEY='<백엔드 CLAUDE.md 로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationFormPageServiceTest" --no-daemon
  ```
- 프론트: 이번 범위엔 코드 변경 없음. (후속에서 form-page 타입에 `postingType` 추가 시 `npm run type-check`.)

## 8. 남은 이슈 / 다음 슬라이스

- 프론트: form-page 타입에 `postingType` 추가 + 학력 입력 UI에서 유형별 성적 필드 분기(공채·인턴=학기별 표시, 경력·수시=평균만 표시). 유형→모드 매핑은 프론트에 위치.
- 향후 "공채는 학기별만(평균 숨김)"이 필요해지면, 그때 백엔드 검증을 모드별로 분기(평균 필수를 경력·수시 한정)하는 **별도 슬라이스**가 필요하다. 이번 슬라이스는 그 전제(공채도 평균 유지)를 명시적으로 채택한다.
