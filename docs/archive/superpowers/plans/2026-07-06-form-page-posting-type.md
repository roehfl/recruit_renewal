# form-page 응답에 채용유형(postingType) 추가 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지원서 폼 조회(`GET /applications/{id}/form-page`) 응답에 채용유형 `postingType`을 노출해, 프론트가 유형별 학력 성적 입력 UI를 분기할 수 있게 한다.

**Architecture:** 기존 enum `JobPostingType`을 `ApplicationFormPageResponse` record에 필드로 추가하고 `from()`에서 `jobPosting.getPostingType()`을 매핑한다. 새 enum·검증 변경 없음. 유형→성적모드 매핑은 프론트 몫.

**Tech Stack:** Spring Boot 4.x, Java 17, JUnit5 + Mockito + AssertJ, Gradle Wrapper, H2(test).

**Spec:** [docs/superpowers/specs/2026-07-06-form-page-posting-type-design.md](../specs/2026-07-06-form-page-posting-type-design.md)

**커밋 정책:** `recruit_back`은 자체 git, `recruit/`(계약 문서)는 로컬 git으로 **분리**돼 있다. 각 커밋은 해당 저장소 안에서 수행한다. `CLAUDE.md` §6 / 백엔드 `CLAUDE.md` §4.4에 따라 **커밋 전 사용자 승인**을 받는다(아래 Commit 스텝은 승인 후 실행).

**문서 정책:** 통합 화면 슬라이스이므로 백엔드 codex md+html 풀세트는 생략하고 `api-contract.md` 갱신 + 최종 보고 요약으로 갈음한다(하네스 `CLAUDE.md` §7).

**테스트 실행 사전 조건:** 백엔드 테스트는 `recruit_back/recruit_backend/`에서 실행하며 로컬 예시 AES 키가 필요하다(백엔드 `CLAUDE.md` §10 예시 값). 아래 명령은 PowerShell 기준.

---

### Task 1: `postingType` 필드 추가 (DTO) + 서비스 테스트 (TDD)

**Files:**
- Modify: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormPageResponse.java`
- Test: `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/service/ApplicationFormPageServiceTest.java`

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`ApplicationFormPageServiceTest.java`의 import 블록에 다음 한 줄을 추가한다 (기존 `import com.shinyoung.recruit.enumeration.JobPostingStatus;` 아래).

```java
import com.shinyoung.recruit.enumeration.JobPostingType;
```

그리고 `owned_application_form_page_returns_summary_and_default_layout_sections()` 테스트 메서드 바로 뒤에 새 테스트를 추가한다.

```java
    @Test
    void form_page_exposes_job_posting_type() {
        JobPosting jobPosting = posting(JobPostingStatus.PUBLISHED, config());
        when(jobPosting.getPostingType()).thenReturn(JobPostingType.EXPERIENCED_RECRUITMENT);
        stubApplication(application(JobApplicationStatus.DRAFT, jobPosting));

        ApplicationFormPageResponse response = formPageService.getFormPage(APPLICANT_ID, APPLICATION_ID);

        assertThat(response.postingType()).isEqualTo(JobPostingType.EXPERIENCED_RECRUITMENT);
    }
```

- [ ] **Step 2: 테스트 실행 → 실패(컴파일 에러) 확인**

`recruit_back/recruit_backend/`에서:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationFormPageServiceTest" --no-daemon
```

Expected: **FAIL** — 컴파일 에러 `cannot find symbol: method postingType()` (아직 DTO에 필드 없음).

- [ ] **Step 3: DTO에 `postingType` 필드 추가**

`ApplicationFormPageResponse.java` import 블록에 다음을 추가한다 (기존 `import com.shinyoung.recruit.enumeration.JobPostingStatus;` 아래).

```java
import com.shinyoung.recruit.enumeration.JobPostingType;
```

record 컴포넌트 목록에서 `JobPostingStatus jobPostingStatus,` 바로 아래에 `JobPostingType postingType,`를 추가한다. 결과:

```java
public record ApplicationFormPageResponse(
        Long applicationId,
        Long jobPostingId,
        String jobPostingTitle,
        JobPostingStatus jobPostingStatus,
        JobPostingType postingType,
        Long jobPositionId,
        String jobPositionName,
        JobApplicationStatus applicationStatus,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        boolean accepting,
        boolean editable,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        ApplicationFormConfigResponse formConfig,
        List<ApplicationFormSectionResponse> sections
) {
```

`from(...)` 안의 `new ApplicationFormPageResponse(...)` 호출에서 `jobPosting.getStatus(),` 바로 아래에 `jobPosting.getPostingType(),`를 추가한다. 결과:

```java
        return new ApplicationFormPageResponse(
                application.getId(),
                jobPosting.getId(),
                resolveText(application.getJobPostingTitleSnapshot(), jobPosting.getTitle()),
                jobPosting.getStatus(),
                jobPosting.getPostingType(),
                jobPosition.getId(),
                resolveText(application.getJobPositionNameSnapshot(), jobPosition.getPositionName()),
                application.getStatus(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                accepting,
                editable,
                application.getSubmittedAt(),
                application.getWithdrawnAt(),
                ApplicationFormConfigResponse.from(formConfig),
                List.copyOf(sections)
        );
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

`recruit_back/recruit_backend/`에서:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ApplicationFormPageServiceTest" --no-daemon
```

Expected: **PASS** (전체 `ApplicationFormPageServiceTest`; 기존 테스트는 `postingType`을 검증하지 않으므로 영향 없음).

- [ ] **Step 5: 커밋 (사용자 승인 후, `recruit_back/recruit_backend/` 저장소)**

```bash
git add src/main/java/com/shinyoung/recruit/dto/response/ApplicationFormPageResponse.java src/test/java/com/shinyoung/recruit/service/ApplicationFormPageServiceTest.java
git commit -m "feat(application): form-page 응답에 postingType 추가" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 컨트롤러 응답 JSON 직렬화 확인

기존 form-page 통합 테스트에 `postingType` 직렬화 검증을 한 줄 추가한다. Task 1에서 필드가 생겼으므로 이 테스트는 통과한다(회귀 가드).

**Files:**
- Test: `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java`

- [ ] **Step 1: JSON 어서션 추가**

`get_application_form_page_returns_api_response()` 테스트에서 `.andExpect(jsonPath("$.data.jobPostingStatus").value("PUBLISHED"))` 바로 아래에 다음을 추가한다.

```java
                .andExpect(jsonPath("$.data.postingType").value("PUBLIC_RECRUITMENT"))
```

(이 테스트의 공고는 `postingType`을 지정하지 않아 백엔드 기본값 `PUBLIC_RECRUITMENT`로 저장된다 — `JobPostingCreateRequest` 6-인자 생성자가 `postingType=null`을 넘기고 엔티티가 기본값을 적용.)

- [ ] **Step 2: 컨트롤러 테스트 실행 → 통과 확인**

`recruit_back/recruit_backend/`에서:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.ApplicationControllerTest.get_application_form_page_returns_api_response" --no-daemon
```

Expected: **PASS** (`$.data.postingType` == `"PUBLISHED"`가 아니라 `"PUBLIC_RECRUITMENT"`; enum이 문자열로 직렬화됨).

- [ ] **Step 3: 커밋 (사용자 승인 후, `recruit_back/recruit_backend/` 저장소)**

```bash
git add src/test/java/com/shinyoung/recruit/controller/ApplicationControllerTest.java
git commit -m "test(application): form-page 응답 postingType 직렬화 검증" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 계약 문서 동기화 (`api-contract.md`)

**Files:**
- Modify: `recruit/api-contract.md`

- [ ] **Step 1: form-page 계약 섹션 추가**

`api-contract.md` 파일 **끝**에 다음 섹션을 추가한다.

```markdown
### 화면: 지원서 작성 폼 로드 (ApplicationFormView — form-page)

- 프론트: `src/views/applicant/ApplicationFormView.vue`, `src/api/application/*` (form-page 로드)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationController`

#### GET `/applications/{applicationId}/form-page`  🟢 확정 (백엔드 구현·검증 완료)

- 용도: 지원서 작성 화면의 폼 메타/레이아웃 로드. 응답 `postingType`으로 프론트가 채용유형별 UI 분기(예: 학력 성적 입력을 공개·인턴=학기별, 경력·수시=평균만 표시)를 판단한다.
- 변경(2026-07-06, 🟢 확정): 응답에 `postingType`(`JobPostingType`) 추가.
- 응답(200): `ApiResponse<{ applicationId, jobPostingId, jobPostingTitle, jobPostingStatus, postingType, jobPositionId, jobPositionName, applicationStatus, receptionStartDateTime, receptionEndDateTime, accepting, editable, submittedAt, withdrawnAt, formConfig, sections:[...] }>`
- `postingType` 값: `"PUBLIC_RECRUITMENT" | "EXPERIENCED_RECRUITMENT" | "INTERN_RECRUITMENT" | "ROLLING_RECRUITMENT"`. 공고 미설정 시 백엔드 기본값 `PUBLIC_RECRUITMENT`.
- 유형→성적모드 매핑은 **프론트**에 위치(백엔드 학력 검증 무변경). 주의: 현재 학력 검증은 비고졸 평균평점 필수·학기별 선택이므로, 공개·인턴이라도 평균 입력란을 숨기면 저장이 400으로 막힌다(평균 유지 필요).
- 매핑: front form-page 로드 ↔ back `ApplicationController.getFormPage()`.
```

- [ ] **Step 2: 커밋 (사용자 승인 후, `recruit/` 로컬 저장소)**

```bash
git add api-contract.md
git commit -m "docs(contract): form-page 응답에 postingType 계약 추가 🟢 확정" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 완료 기준 (Definition of Done)

- `ApplicationFormPageResponse`에 `postingType` 필드 존재, `from()`이 `jobPosting.getPostingType()` 매핑.
- `ApplicationFormPageServiceTest.form_page_exposes_job_posting_type` 통과.
- `ApplicationControllerTest.get_application_form_page_returns_api_response`가 `$.data.postingType == "PUBLIC_RECRUITMENT"` 검증하며 통과.
- `api-contract.md`에 form-page 섹션(🟢) 존재.
- 새 enum 없음 / `ApplicationEducationService` 검증 무변경 확인.

## 최종 보고 요약 (실행 후 작성)

- 변경 파일: (DTO 1, 테스트 2, 계약 1)
- 테스트 결과: 실행 명령 + PASS/FAIL
- 계약 변경: form-page 섹션 추가(🟢)
- 남은 이슈: 프론트 form-page 타입에 `postingType` 추가 + 학력 UI 유형별 분기(후속 슬라이스).
