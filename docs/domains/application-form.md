# 지원서 양식 설정 (`application-form`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [job-posting](job-posting.md)(공고·발행·첨부 요건·공고 상세 API) · [question](question.md)(질문 탭) · [application](application.md)(지원자 form-page 엔드포인트·제출 검증) · [application-sections](application-sections.md)(섹션 저장) · [attachment](attachment.md) · [admin-application](admin-application.md)(레이아웃 GET 사용) · [stage-result](stage-result.md)(현황판 "전형 단계" 버튼)

## 요약

- 관리자가 공고별 지원서 **양식**(섹션 사용/필수, `ApplicationFormConfig`)과 **폼 구성**(섹션을 페이지에 배치한 레이아웃, `ApplicationFormPage`·`ApplicationFormPageItem`)을 설정한다.
- **지원서 설정 현황판**(`/admin/application-forms`)은 공고별 `configState`를 계산해 보여 준다. 존재 이유: `RELAYOUT_REQUIRED`(저장 레이아웃의 배치 섹션 ≠ 현재 활성 섹션 → 지원자 form-page 조회 400) 공고 찾기.
- 레이아웃을 저장한 적이 없으면 읽을 때마다 기본 레이아웃을 **계산**한다(DB 저장 안 함).
- 지원자 form-page 조립 서비스 `ApplicationFormPageService`는 이 카드 소유다. 엔드포인트 `GET /applications/{id}/form-page`와 응답 계약은 [application](application.md) 카드.
- 같은 폴더의 `{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue`와 `{FE}/views/admin/applicationForm/questionModal/*`는 [question](question.md) 카드 소유다(상세 화면에 탭으로만 붙는다).
- 화면 섹션 "지원서 설정 현황"·"공고별 지원서 설정"·"변경: 공고 등록/수정에서 지원서 양식 분리"는 2026-09-19 코드 기준으로 확정됐다(옛 계약 문구와 달랐으나 2026-09-19 코드 기준으로 확정). 엔드포인트별 상태는 API 표에 있다.

## 용어

| 용어 | 뜻 |
|---|---|
| 양식(form config) | `ApplicationFormConfig`: 7개 섹션(학력·경력·자격증·어학·병역·포상·공백기간)의 `use*`/`require*` + `useAttachment`. 공고당 1행 |
| 폼 구성(layout) | 페이지(`pageNo`·`title`·`description`·`sortOrder`) + 항목(`sectionType`·`sortOrder`) |
| 레이아웃 섹션 | `ApplicationSectionType.isLayoutSection()` 10종: `BASIC_INFO` `MILITARY` `EDUCATION` `CAREER` `CERTIFICATE` `LANGUAGE` `AWARD` `GAP_PERIOD` `QUESTION_ANSWER` `ATTACHMENT`. `APPLICATION`·`ETC`는 배치 불가 |
| 활성/필수/배치 섹션 | 활성·필수 = 양식·질문·첨부 요건에서 계산한 집합(레이아웃 API로 못 바꿈). 배치 = 레이아웃 항목에 든 집합 |
| `configState` | `MISSING`(양식 없음) · `RELAYOUT_REQUIRED`(배치≠활성) · `DEFAULT`(저장 레이아웃 없음) · `OK` |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/AdminApplicationFormController.java` | 현황판 목록 |
| controller | `{BE}/controller/AdminApplicationFormConfigController.java` | 양식 단독 저장 |
| controller | `{BE}/controller/AdminApplicationFormLayoutController.java` | 레이아웃 조회·저장·미리보기 |
| service | `{BE}/service/AdminApplicationFormSummaryService.java` | 현황판: 집계 쿼리 + 메모리 필터·정렬·페이징, `configState` 판정 |
| service | `{BE}/service/ApplicationFormConfigService.java` | 양식 저장(병합·검증), 공고 등록 시 양식 생성(`createFrom`) |
| service | `{BE}/service/ApplicationFormLayoutService.java` | 레이아웃 조회·치환 저장·미리보기, 발행 전 검증, 섹션 라벨·source |
| service | `{BE}/service/ApplicationFormLayoutSectionPolicy.java` | 활성·필수 섹션 계산 |
| service | `{BE}/service/ApplicationFormLayoutValidator.java` | 레이아웃 불변식 검증 |
| service | `{BE}/service/ApplicationFormLayoutDefaultFactory.java` | 기본 레이아웃 생성 |
| service | `{BE}/service/ApplicationFormEditWindow.java` | 편집 가능 구간 판정 |
| service | `{BE}/service/ApplicationFormPageService.java` | 지원자 form-page 조립(컨트롤러는 application 카드) |
| entity | `{BE}/domain/entity/ApplicationFormConfig.java` | 양식(use=false & require=true면 `IllegalArgumentException`) |
| entity | `{BE}/domain/entity/ApplicationFormPage.java` | 페이지(items cascade·orphanRemoval) |
| entity | `{BE}/domain/entity/ApplicationFormPageItem.java` | 페이지 항목(레이아웃 섹션만) |
| repository | `{BE}/domain/repository/ApplicationFormPageRepository.java` | 페이지 조회·삭제, 현황판용 `findLayoutItemsByJobPostingIds` |
| repository | `{BE}/domain/repository/ApplicationFormLayoutItemView.java` | 현황판 배치 projection `(jobPostingId, pageNo, sectionType)` |
| repository | `{BE}/domain/repository/ApplicationFormPageItemRepository.java` | 항목 리포지토리(main 미사용) |
| dto | `{BE}/dto/request/AdminApplicationFormSummarySearchRequest.java` | 현황판 검색 조건 |
| dto | `{BE}/dto/request/ApplicationFormConfigRequest.java` | 양식 요청(`require*` null 허용). 공고 등록 요청에도 포함 |
| dto | `{BE}/dto/request/ApplicationFormLayoutSaveRequest.java` | 레이아웃 저장 요청 |
| dto | `{BE}/dto/response/AdminApplicationFormSummaryResponse.java` | 현황판 행 |
| dto | `{BE}/dto/response/AdminApplicationFormLayoutResponse.java` | 레이아웃 조회·저장 응답 |
| dto | `{BE}/dto/response/ApplicationFormLayoutPreviewResponse.java` | 미리보기 응답 |
| dto | `{BE}/dto/response/ApplicationFormConfigResponse.java` | 양식 응답(공고 상세·form-page에도 포함). `from(null)` 미지원 |
| dto | `{BE}/dto/response/ApplicationFormConfigPublicResponse.java` | 공개 공고 상세용 양식(null이면 전부 false) |
| dto | `{BE}/dto/response/ApplicationFormRequiredPolicyResponse.java` | 공개 공고 목록·상세용 섹션 필수/선택 요약 |
| dto | `{BE}/dto/response/ApplicationFormSectionPolicyResponse.java` | 위 요약의 섹션 한 줄 |
| enum | `{BE}/enumeration/ApplicationSectionType.java` | 섹션 종류, 레이아웃 섹션 집합, `acceptsApplicantAttachment()` |
| enum | `{BE}/enumeration/ApplicationFormConfigState.java` | 선언 순서 = 현황판 정렬 순서 |
| enum | `{BE}/enumeration/ApplicationFormRequirementType.java` | `REQUIRED` `OPTIONAL` `DISABLED` `DEFERRED`(미사용) |
| exception | `{BE}/exception/InvalidApplicationFormLayoutException.java` | 레이아웃 오류 → 400 |
| test | `{BT}/controller/AdminApplicationForm*ControllerTest.java` | 컨트롤러 3종 |
| test | `{BT}/service/*ApplicationForm*Test.java` | 서비스 7종 |
| test | `{BT}/domain/entity/ApplicationFormPage*Test.java` | 페이지·항목 엔티티 |
| test | `{BT}/domain/repository/ApplicationFormPageRepositoryTest.java` | 페이지 리포지토리 |
| test | `{BT}/dto/response/ApplicationFormPolicyResponseTest.java` | 공개 정책 DTO |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminApplicationFormList`, `AdminApplicationFormDetail`(`…/:jobPostingId`, 메뉴 미등록이라 `meta.activeMenuPath`로 현황 메뉴 활성) — 공유 |
| view | `{FE}/views/admin/applicationForm/AdminApplicationFormListView.vue` | 현황판(행 클릭 → 상세, "전형 단계" → `AdminStageResult`) |
| view | `{FE}/views/admin/applicationForm/AdminApplicationFormDetailView.vue` | 탭 호스트(양식·폼 구성·질문), 편집 가능 판정, 미저장 이탈 확인 |
| view | `{FE}/views/admin/applicationForm/ApplicationFormConfigTab.vue` | 양식 탭(섹션 사용/필수) |
| view | `{FE}/views/admin/applicationForm/ApplicationFormLayoutTab.vue` | 폼 구성 탭(드래그 배치·저장 차단 사유·미리보기) |
| api | `{FE}/api/admin/adminApplicationFormApi.ts` | `getSummaries` `saveFormConfig` `getLayout` `saveLayout` `getLayoutPreview` |
| types | `{FE}/types/admin/application.ts` | `sectionType`, 레이아웃·미리보기·현황판 타입 — 공유 |
| types | `{FE}/types/jobPosting.ts` | `AdminApplicationFormConfig` — 공유 |
| types | `{FE}/common/applicationSection.ts` | `SECTION_LABELS`(한글), `SECTION_SOURCE_LABELS` |

## API 계약

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /admin/application-forms | query `{ status?, receptionStatus?, configState?, editableOnly?, keyword?, excludeClosed?, page=0, size=20 }` | `PageResponse<{ jobPostingId, title, postingType, status, receptionStatus, receptionStartDateTime, receptionEndDateTime, sectionSummary{enabledCount, requiredCount}, activeQuestionCount, requiredQuestionCount, layoutStored, pageCount, configState, editable, updatedAt }>` | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/application-form-config | `{ useEducation, requireEducation?, useCareer, requireCareer?, useCertificate, requireCertificate?, useLanguage, requireLanguage?, useMilitary, requireMilitary?, useAward, requireAward?, useGapPeriod, requireGapPeriod?, useAttachment }` | `ApplicationFormConfigResponse`(같은 15필드, 모두 boolean) | 관리자 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/application-form-layout | — | `{ jobPostingId, layoutStored, editable, pages[{ pageNo, title, description, sortOrder, items[{ sectionType, sectionName, sortOrder, enabled, required, placed }] }], availableSections[{ sectionType, sectionName, enabled, required, placed, source }] }` | 관리자 |
| 🟢 | POST | /admin/job-postings/{jobPostingId}/application-form-layout | `{ pages[{ pageNo, title, description?, sortOrder, items[{ sectionType, sortOrder }] }] }` — 전체 치환 | GET과 같은 형태(`layoutStored=true`, `editable=true`) | 관리자 |
| 🟢 | GET | /admin/job-postings/{jobPostingId}/application-form-layout/preview | — | `{ jobPostingId, jobPostingTitle, pages[{ pageNo, title, description, sortOrder, items[{ sectionType, sectionName, required, sortOrder }] }] }` | 관리자 |

- 권한 "관리자" = `{BE}/config/SecurityConfig.java`의 `/api/admin/**` → `ROLE_ADMIN`·`ROLE_RECRUIT_ADMIN`. 메서드 보안 애너테이션 없음.
- 상세 화면은 양식 값을 [job-posting](job-posting.md) 카드의 `adminJobPostingApi.getJobPosting()` 응답 `applicationFormConfig`에서 읽는다(양식 전용 GET 없음).

### 엔드포인트 상세

**GET /admin/application-forms** — 🟢 확정(2026-09-19 코드 기준, `excludeClosed` 2026-09-18). 코드 기준 동작(옛 계약 문구와 달랐으나 2026-09-19 코드 기준으로 확정):
- 정렬: `configState` 선언 순서(`MISSING → RELAYOUT_REQUIRED → DEFAULT → OK`) → `receptionStartDateTime` 오름차순 → `jobPostingId` 오름차순(`AdminApplicationFormSummaryService.summaryComparator`). `MISSING`이 맨 앞이다.
- `editableOnly`: 응답 `editable`(= `ApplicationFormEditWindow.isEditable`)로 거른다. `CLOSED`는 항상 제외, `PUBLISHED`는 접수 시작 전만 포함 — `DRAFT`는 접수 시작일이 지나도 편집 가능해 그대로 남는다.
- `excludeClosed`: 서버 조건(`status==CLOSED` 제외)이다. 프론트는 기본값 `excludeClosed=true`를 보낸다.
- 공고 목록 API(`GET /admin/job-postings`)와 별개다.
- 값: `status` `DRAFT|PUBLISHED|CLOSED`, `receptionStatus` `UPCOMING|ACCEPTING|CLOSED`, `configState` `OK|DEFAULT|RELAYOUT_REQUIRED|MISSING`.
- SQL 조건은 `status`, `keyword`(제목, 대소문자 무시 부분일치, 공백 trim)뿐이다(`JobPostingRepository.findAllForApplicationFormSummary`, job-posting 카드 파일). 나머지 파생값 필터는 계산 후 **메모리에서 거르고 페이징**한다. 공고 테이블이 작다는 전제다. 커지면 파생값을 컬럼으로 승격해야 한다.
- `configState` 판정은 서버 계산이 단일 출처다(규칙 12). `editable`은 `configState`와 직교하는 별도 필드이고, 화면은 🔒로 덧붙인다.
- 성능: 질문 수 `countQuestionPolicyByJobPostingIds`, 첨부 요건 수 `countPolicyByJobPostingIds`, 레이아웃 `findLayoutItemsByJobPostingIds`로 한 번에 읽는다. 공고당 개별 조회는 금지.
- `pageCount` = 서로 다른 `pageNo` 수. `layoutStored` = 항목 있는 페이지 존재.
- `size`가 1~100 밖이거나 `page < 0`이면 400.
- 행 클릭으로 이동하는 상세 화면은 탭 3개(양식·폼 구성·질문 설정)다.
- 매핑: front `adminApplicationFormApi.getSummaries()` ↔ back `AdminApplicationFormController.getSummaries()`.

**POST …/application-form-config** — 🟢 확정(2026-09-02)
- 공고 등록/수정 API에서 분리한 양식 전용 저장이다. 경로 체계에 맞춰 현황판용 `AdminApplicationFormController`와 다른 컨트롤러로 둔다.
- 편집 가능 조건과 그 이유는 규칙 1.
- `require*`가 null이면 기존 값을 유지하되, 그 섹션을 끄면 필수도 해제된다. 값을 명시하면 그 값을 쓴다(규칙 2).
- 양식이 없던 공고(`MISSING`)면 등록 규칙(`createFrom`)으로 새로 만든다.
- 오류: 400 "접수가 시작된 채용공고의 지원서 양식은 수정할 수 없습니다." / "마감된 …", 400 `"<section> section cannot be required when disabled."`, 404 공고 없음.
- 매핑: front `adminApplicationFormApi.saveFormConfig()` ↔ back `AdminApplicationFormConfigController.saveConfig()`.

**GET·POST …/application-form-layout** — 🟢 확정(2026-09-02)
- POST는 **전체 치환**(삭제 후 재삽입)이다. 부분 저장·낙관적 잠금은 없다(동시 편집 시 마지막 저장이 이긴다).
- 화면이 지켜야 할 서버 불변식은 규칙 7(`ApplicationFormLayoutValidator`). 특히 배치 == 활성(미배치 0)이어야 저장된다.
- 섹션 사용/필수는 이 API로 못 바꾼다(양식 탭·질문·첨부 요건에서 파생).
- GET: 저장 레이아웃이 없으면 기본 레이아웃을 `layoutStored=false`로 준다. 저장 레이아웃에 지금 꺼진 섹션이 있으면 그 항목은 `enabled=false, placed=true`로 온다. 화면은 이 항목을 빼고 안내 배너를 띄운다. `availableSections`는 레이아웃 섹션 10종 전부(enum 순서)다.
- `sectionName`은 영문("Basic Info", "Questions")이다. 프론트는 `SECTION_LABELS`로 한글을 쓰고, `source`(`ALWAYS`·`APPLICATION_FORM_CONFIG`·`QUESTION`·`ATTACHMENT_REQUIREMENT`)는 `SECTION_SOURCE_LABELS`로 "어디서 켜지는 섹션인지" 안내한다.
- 오류: 양식 없음 400 "지원서 항목 설정이 없는 채용공고입니다.", 편집 불가 400("접수가 시작된/마감된 채용공고의 레이아웃은 수정할 수 없습니다."), 검증 실패 400(영문 메시지), 요청 형식 오류 400, 공고 없음 404.
- 다른 사용처: 관리자 지원서 상세(`{FE}/views/admin/application/Application.vue`, [admin-application](admin-application.md))가 `adminApplicationApi.getApplicationFormLayout()`으로 같은 GET을 부른다. 용도가 달라 그대로 둔다.
- 매핑: front `adminApplicationFormApi.getLayout()` / `saveLayout()` ↔ back `AdminApplicationFormLayoutController.getLayout()` / `saveLayout()`.

**GET …/application-form-layout/preview** — 🟢 확정(2026-09-02)
- 지원자 관점 미리보기: 저장(없으면 기본) 레이아웃에서 활성 섹션 항목만 남기고 빈 페이지를 뺀다.
- 검증하지 않는다. `RELAYOUT_REQUIRED`여도 200이고, 활성인데 미배치인 섹션은 보이지 않는다.
- 매핑: front `adminApplicationFormApi.getLayoutPreview()` ↔ back `AdminApplicationFormLayoutController.getPreview()`.

**공고 등록/수정에서 양식 분리** (옛 계약 🟡 초안 2026-09-02. 엔드포인트는 [job-posting](job-posting.md) 소유)
- 배경: 양식이 공고 등록/수정 요청에 필수로 묶여 있어, 공고 수정 화면이 낡은 값으로 저장하면 설정 화면에서 바꾼 값을 되돌렸다. 요청에서 떼어 구조적으로 막았다.
- `POST /admin/job-postings`(등록) 🟢: `applicationFormConfig`는 **생략 가능**(`@NotNull`만 제거, 필드 유지 — 테스트 호출부가 많아서)하다. 생략하면 `createFrom(null)`이 기본 양식을 만든다(규칙 4). null로 두지 않는 이유: 양식이 없으면 레이아웃 조회·저장·미리보기, 게시, 지원자 form-page가 모두 막힌다. 프론트는 이 필드를 보내지 않는다.
- `POST /admin/job-postings/{id}`(수정) 🟢: 요청에서 `applicationFormConfig`를 **제거**했다. 옛 클라이언트가 보내도 무시된다(400 아님). `GET /admin/job-postings/{id}` 응답의 `applicationFormConfig`는 유지한다.
- 운영: 기존 DB에 `application_form_config`가 없는 공고는 현황판에 `MISSING`으로 뜬다. 백필 여부는 미확인.

## 규칙·불변식

1. **편집 가능 구간**: `CLOSED`면 불가, `DRAFT`면 항상 가능, `PUBLISHED`면 `now < receptionStartDateTime`일 때만 가능 (`{BE}/service/ApplicationFormEditWindow.java` — isEditable). 양식 저장·레이아웃 저장·현황판 `editable`이 같은 판정을 쓴다. 다른 곳에서 다시 계산하지 않는다.
   - 이유: 제출된 지원서와 어긋나지 않게 잠근다. 지원서는 게시 후 접수가 시작돼야 생기므로 `DRAFT`는 잠글 이유가 없다. 발행은 접수 시작일이 지났는지 보지 않으므로, `DRAFT`를 접수일로 잠그면 "발행은 되는데 설정은 못 고치는" 상태가 생긴다. 발행 후 접수 전 구간은 unpublish가 없어서 마지막 수정 기회로 열어 둔다.
   - 프론트 상세 화면은 클라이언트 시계 없이 서버의 `status`와 `receptionStatus === 'UPCOMING'`으로 같은 규칙을 쓴다(`AdminApplicationFormDetailView.vue` — editable).
2. **양식 병합**: `require*`가 null이면 `use && 현재 require`, 값이 있으면 그 값 (`{BE}/service/ApplicationFormConfigService.java` — resolveUpdatedRequired).
3. **use=false인데 require=true면 거부**: 서비스 400, 엔티티 `IllegalArgumentException` (`{BE}/service/ApplicationFormConfigService.java` — validateRequirement, `{BE}/domain/entity/ApplicationFormConfig.java` — validateRequirement).
4. **등록 기본값**: 요청이 null이면 전 섹션 사용, 학력·경력·병역만 필수, `useAttachment=false`(분리 이전 등록 화면과 같은 동작). 요청이 있는데 `require*`가 null이면 학력·경력·병역은 `use` 값, 나머지는 false (`{BE}/service/ApplicationFormConfigService.java` — createFrom).
5. **활성 섹션**: `BASIC_INFO` 항상. 양식 7섹션은 `use*`일 때. `QUESTION_ANSWER`는 공고 질문 ≥1. `ATTACHMENT`는 `useAttachment`이거나 첨부 요건 행 ≥1 (`{BE}/service/ApplicationFormLayoutSectionPolicy.java` — enabledSections).
6. **필수 섹션**: `BASIC_INFO` 항상. 양식 섹션은 `use && require`. `QUESTION_ANSWER`는 필수 질문 ≥1. `ATTACHMENT`는 필수 첨부 요건 ≥1. 필수 ⊆ 활성. 레이아웃 항목에는 필수 플래그를 두지 않는다 (`{BE}/service/ApplicationFormLayoutSectionPolicy.java` — requiredSections).
7. **레이아웃 검증** (`{BE}/service/ApplicationFormLayoutValidator.java` — validate):
   - 활성·필수 집합: null 불가, 레이아웃 섹션만, 둘 다 `BASIC_INFO` 포함, 필수 ⊆ 활성.
   - 페이지 ≥1. `pageNo` > 0, 중복 불가. 페이지 `sortOrder` ≥ 0, 중복 불가. 제목 trim 후 필수·≤100. 설명 ≤500.
   - 페이지당 항목 ≥1. 항목은 레이아웃 섹션만. 항목 `sortOrder` ≥ 0, 페이지 안 중복 불가. 섹션은 전체에서 한 번만. 비활성 섹션 배치 불가.
   - 활성·필수 섹션 모두 배치. 결과적으로 배치 == 활성.
8. **저장은 전체 치환**: 편집 가능 확인 → 검증 → `deleteByJobPostingId` → `flush` → `saveAll` (`{BE}/service/ApplicationFormLayoutService.java` — saveLayout).
9. **기본 레이아웃**: 활성 섹션만으로 만들고 빈 페이지는 뺀다. ① `BASIC_INFO`·`MILITARY` "Basic Info" ② `EDUCATION`·`CAREER` "Education And Career" ③ `CERTIFICATE`·`LANGUAGE`·`AWARD`·`GAP_PERIOD` "Additional Records" ④ `QUESTION_ANSWER` "Question Answer" ⑤ `ATTACHMENT` "Attachment". `pageNo`=순번+1, `sortOrder`=순번, 설명 null. DB에 저장하지 않는다 (`{BE}/service/ApplicationFormLayoutDefaultFactory.java` — createDefaultLayout).
10. **검증 시점 세 곳**(저장 레이아웃이 없으면 기본 레이아웃을 검증):
    - 레이아웃 저장 (`{BE}/service/ApplicationFormLayoutService.java` — saveLayout).
    - 공고 발행: 양식 없음이면 "지원서 항목 설정이 없는 채용공고는 게시할 수 없습니다.", 검증 실패는 발행 쪽이 "레이아웃 검증 실패: …" 400으로 감싼다 (`{BE}/service/ApplicationFormLayoutService.java` — validateLayoutForPublish, 호출은 [job-posting](job-posting.md) `JobPostingService.publish`).
    - 지원자 form-page 조회 (`{BE}/service/ApplicationFormPageService.java` — getFormPage).
11. **양식 없음(null)**: 레이아웃 GET·POST·미리보기 400 (`{BE}/service/ApplicationFormLayoutService.java` — requireFormConfig). form-page는 `InvalidJobApplicationException` (`{BE}/service/ApplicationFormPageService.java` — requireFormConfig).
12. **configState 판정**: 양식 null → `MISSING`, 저장 레이아웃 없음 → `DEFAULT`, 배치 ≠ 활성 → `RELAYOUT_REQUIRED`, 그 외 `OK`. 레이아웃의 다른 불변식(중복 등)은 보지 않는다 (`{BE}/service/AdminApplicationFormSummaryService.java` — resolveConfigState).
13. **form-page 조립**(엔드포인트·응답 DTO `ApplicationFormPageResponse`·`ApplicationFormSectionResponse`는 [application](application.md)):
    - 지원서의 모집분야가 그 공고 소속이어야 한다 (`{BE}/service/ApplicationFormPageService.java` — validateSelectedJobPosition).
    - `accepting` = `PUBLISHED`이고 `start ≤ now ≤ end`. `editable` = `WITHDRAWN` 아님 && `accepting`.
    - 섹션 목록은 페이지 `sortOrder` → 항목 `sortOrder` 순으로 평탄화하고 `sortOrder`를 0부터 다시 매긴다 (`{BE}/service/ApplicationFormPageService.java` — toSectionResponses).
14. **공고유형(신입/경력)**: 섹션 정책·기본 레이아웃·검증·편집 구간은 `postingType`과 **무관**하다. 유형별 차이(신입만 학기별 성적)는 form-page 응답의 `postingType`으로 지원자 화면이 분기한다([application-sections](application-sections.md) 소관). 현황판은 `postingType`을 표시만 한다(`PUBLIC_RECRUITMENT`=신입, `EXPERIENCED_RECRUITMENT`=경력).
15. **ATTACHMENT 섹션**(독립 첨부 섹션은 프론트에서 폐지): 백엔드 정책 `enabled = useAttachment || 첨부 요건 행 존재`는 **바꾸지 않았다**. 요건 행의 `sectionType`과 무관하게 행이 하나라도 있으면(예: `CAREER` 경력기술서 요건) `ATTACHMENT`가 활성이다. 첨부 요건을 둘 수 있는 섹션(`BASIC_INFO`·`CAREER`, `acceptsApplicantAttachment()`)은 [job-posting](job-posting.md)·[attachment](attachment.md) 규칙이다.
    - 폼 구성 탭은 **비활성** 목록에서 `ATTACHMENT`를 감춘다(폐지된 섹션을 켜라는 안내가 되므로). 이미 **활성**이면 미배치 서랍에 그대로 보인다(배치하지 않으면 저장이 막히므로) (`ApplicationFormLayoutTab.vue` — disabledSections).
    - 양식 탭에는 `useAttachment` 체크박스가 없다. 불러온 값을 그대로 되돌려 보내고, 값이 없으면 false.
16. **라벨·source**: `labelOf`·`sourceOf`는 enum `switch`라 섹션 추가 시 컴파일 오류로 누락이 드러난다 (`{BE}/service/ApplicationFormLayoutService.java` — labelOf, sourceOf).
17. **공개 공고 섹션 정책**: 양식 없음 → 전 섹션 `DISABLED`. 질문은 0개면 `DISABLED`, 필수 ≥1이면 `REQUIRED`, 아니면 `OPTIONAL`. 첨부는 `!useAttachment && 요건 0`이면 `DISABLED`. `DEFERRED`는 만들어지지 않는다 (`{BE}/dto/response/ApplicationFormRequiredPolicyResponse.java` — from).

## 변경 레시피

### 양식에 새 섹션(use/require 쌍) 추가
1. `{BE}/enumeration/ApplicationSectionType.java`: 값 추가, `LAYOUT_SECTION_TYPES`에 포함.
2. `{BE}/domain/entity/ApplicationFormConfig.java`: 컬럼·생성자·`create`·`update`·`validateRequirement`. 운영 DB DDL 필요.
3. DTO: `ApplicationFormConfigRequest`·`ApplicationFormConfigResponse`·`ApplicationFormConfigPublicResponse`·`ApplicationFormRequiredPolicyResponse`.
4. `ApplicationFormConfigService`(`createFrom`·`merge`·`validateRequirement`), `ApplicationFormLayoutSectionPolicy`, `ApplicationFormLayoutDefaultFactory`, `ApplicationFormLayoutService`(`labelOf`·`sourceOf`).
5. 프론트: `{FE}/types/admin/application.ts`(`sectionType`), `{FE}/types/jobPosting.ts`, `{FE}/common/applicationSection.ts`, `ApplicationFormConfigTab.vue`(`SECTIONS`).
6. 지원자 쪽 섹션 화면은 [application-sections](application-sections.md), 제출 검증·완성도는 [application](application.md) 카드대로.
7. 레이아웃이 저장된 기존 공고는 새 섹션을 켜는 순간 `RELAYOUT_REQUIRED`가 된다. 운영 안내 필요.
8. 테스트 → 카드 표·규칙 갱신 → `node tools/check-docs.mjs`.

### 레이아웃 검증 규칙 변경
1. `ApplicationFormLayoutValidator` 수정. 저장·발행·form-page 세 곳에 적용된다. 이미 저장된 레이아웃이 새 규칙에 어긋나면 지원자 조회가 400이 된다.
2. 현황판이 그 상태를 보여야 하면 `AdminApplicationFormSummaryService.resolveConfigState`도 맞춘다.
3. 프론트 `ApplicationFormLayoutTab.vue`의 `blockingReason`·길이 상수를 맞춘다.
4. 테스트: `ApplicationFormLayoutValidatorTest`·`ApplicationFormLayoutServiceTest`·`ApplicationFormPageServiceTest`·`JobPostingServiceTest`(발행).
5. 규칙 7 갱신 → `node tools/check-docs.mjs`.

### 편집 가능 구간 변경
1. `ApplicationFormEditWindow.isEditable`만 고친다(서비스 3곳이 따라간다).
2. 프론트 `AdminApplicationFormDetailView.vue`의 `editable` computed를 같은 규칙으로. 폼 구성 탭은 서버 `editable`을 쓴다.
3. 테스트: `ApplicationFormConfigServiceTest`·`ApplicationFormLayoutServiceTest`·`AdminApplicationFormSummaryServiceTest`·컨트롤러 테스트. 게시 상태가 필요하면 `{BT}/support/JobPostingImageTestSupport.java`로 본문 이미지를 붙인 뒤 발행한다.
4. 규칙 1·엔드포인트 상세 갱신 → `node tools/check-docs.mjs`.

### 현황판 필터·열 추가
1. `AdminApplicationFormSummarySearchRequest` 수정(테스트가 쓰는 5인자 생성자 유지).
2. 파생값 필터는 `AdminApplicationFormSummaryService.matches`에, SQL로 되는 조건은 `JobPostingRepository.findAllForApplicationFormSummary`([job-posting](job-posting.md) 소유)에 넣는다. 공고당 개별 쿼리 금지.
3. 열은 `AdminApplicationFormSummaryResponse`에 추가.
4. 프론트: `{FE}/types/admin/application.ts`, `AdminApplicationFormListView.vue`(`buildSearchRequest`·`columns`). 응답을 프론트에서 다시 거르지 않는다.
5. `AdminApplicationFormSummaryServiceTest`·`AdminApplicationFormControllerTest` → API 표 갱신 → `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서). `*ApplicationForm*`는 이 카드의 테스트 14개(컨트롤러 3, 서비스 7, 엔티티 2, 리포지토리 1, DTO 1)와 정확히 일치한다. 발행 연동을 건드렸으면 `JobPostingServiceTest`도 돌린다.

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "*ApplicationForm*" --no-daemon
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPostingServiceTest" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "*ApplicationForm*" --no-daemon
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.service.JobPostingServiceTest" --no-daemon
```

프론트(`recruit_front/`에서, 이 도메인 vitest spec 없음)와 문서(레포 루트에서):

```bash
npm run type-check
node tools/check-docs.mjs
```

## 함정·결정

- `7236421` 작성 중 공고 설정 편집 허용 — `ApplicationFormEditWindow` 신설. 판정을 다른 곳에서 다시 짜면 "발행은 되는데 설정은 못 고치는" 버그가 재발한다.
- `8d7485d` 현황판 "마감 제외"를 서버 조건(`excludeClosed`)으로. 프론트가 응답을 다시 거르면 페이지·총건수가 어긋난다. 같은 커밋의 탭 미저장 이탈 확인 때문에 탭은 `isDirty()`를 `defineExpose`로 계속 노출해야 한다.
- `e96bd5a` 폐지된 첨부 섹션을 비활성 목록에서 숨김. 활성인 레거시 `ATTACHMENT`까지 숨기면 저장 불가 이유를 설명하지 못한다.
- `86d12c9` 양식 저장으로 사용 섹션이 바뀌면 "폼 구성 탭에서 재배치" 경고. 양식 저장은 레이아웃을 바꾸지 않는다.
- `523ab48` 폐지된 `ATTACHMENT` 섹션의 첨부 요건 등록 거부·필수 판정 제외(job-posting·attachment 카드). 레이아웃 섹션 정책은 그대로다(규칙 15).
- 활성 섹션 집합을 바꾸는 변경(양식 `use*`·`useAttachment` 토글, 첫 질문 등록이나 전부 삭제, 첫 첨부 요건 등록이나 전부 삭제)은 저장 레이아웃이 있는 공고를 즉시 `RELAYOUT_REQUIRED`로 만들고 지원자 form-page를 400으로 막는다. 접수 시작 후엔 레이아웃을 못 고치고 unpublish도 없다.
- `MISSING` 공고: `JobPostingDetailResponse`가 `ApplicationFormConfigResponse.from(null)`을 부르므로 `GET /admin/job-postings/{id}`가 NPE(500)로 실패할 수 있다. 그러면 상세 화면이 양식 탭을 못 띄운다(코드 판독, 테스트 미확인).
- 프론트 타입 `availableSectionsItem.sortOrder`는 백엔드 `SectionAvailability`에 없다(항상 undefined). 쓰지 않는다.
- 낡은 코드 주석: `AdminApplicationFormSummaryResponse.editable`·`AdminApplicationFormSummarySearchRequest`의 "접수 시작 전 && 미마감", `adminApplicationFormApi.saveFormConfig`의 "접수 시작 전에만 허용", `AdminApplicationFormDetailView.vue` `TABS` 위 "질문 탭은 후속 slice에서 추가"(이미 추가됨). 기준은 규칙 1.
- 옛 계약은 상세 탭을 2개([지원서 양식] [폼 구성])로 적었다. 현재 코드는 3탭(+ `질문 설정`, question 카드)이다. 질문 구조 편집(추가·삭제·순서)은 `DRAFT`에서만(`structureEditable`).
- 이 도메인 전용 ADR은 없다.
