# FAQ·공지사항 (`board`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [auth-account](auth-account.md)(`SecurityConfig`·role 상수), [role-menu](role-menu.md)(지원자 breadcrumb·메뉴 등록), [job-posting](job-posting.md)(`boardApi.ts`의 공개 공고 함수)

## 요약

- **FAQ**: 카테고리(`FaqCategory`) 1 : N 질문/답변(`Faq`). 관리자 화면(`/admin/faqs`)에서 카테고리·FAQ를 생성·수정·삭제(soft)하고 ↑↓로 순서를 바꾼다. 지원자 화면(`/applicant/faq`)은 공개 API `GET /faqs` 한 번으로 전체를 받아 좌측 카테고리 목록 + 우측 Q/A 아코디언으로 보여준다. 답변은 **평문**이다.
- **공지사항**: 지원자 화면(`/applicant/noticeList`)에서 목록(페이징·검색) + 상세 모달. 본문은 HTML이며 **서버가 응답 시점에 jsoup으로 정제**하고 FE가 `v-html`로 렌더한다. 등록 API(`POST /board/notices`)는 있지만 관리자 공지 화면이 없어 FE에서 호출하지 않는다. 공지 수정·삭제 API는 없다.
- 페이징은 공지 목록만 있다. FAQ는 관리자·지원자 모두 페이징 없이 전체 조회.
- 수정·삭제·정렬도 POST다(CORS 허용 메서드가 GET/POST뿐 — `{BE}/config/SecurityConfig.java`).

## 용어

| 용어 | 뜻 |
|---|---|
| `FaqCategory` | FAQ 카테고리(예: "지원서 관련"). `name`(unique, ≤100자)·`sortOrder`(전역)·`active` |
| `Faq` | 질문/답변 1건. 반드시 카테고리 하나에 속함. `question`(≤500자)·`answer`(평문, LONGTEXT)·`sortOrder`(카테고리 내부)·`active` |
| 노출(`active`) | 지원자 화면 노출 여부. 화면 라벨 "노출/비노출" |
| soft delete | 삭제 API = `active=false`. row는 남고 관리자 목록에 "비노출"로 계속 보인다 |
| reorder | 정렬 일괄 반영 API. `ids` 배열 순서대로 `sortOrder`를 0..n-1로 다시 매긴다 |
| 공개 조회 | 인증 없이 호출하는 지원자용 API(`GET /faqs`, `GET /board/notices…`) |
| `Notice` | 공지 1건. `title`·`contentHtml`(원본 HTML)·`contentText`(검색용 텍스트)·`pinned` |
| `pinned` | 상단 고정. 목록 정렬 1순위. FE는 제목 앞 "NEW" 배지로 표시 |
| `NoticeSearchType` | `ALL`(제목+내용) · `TITLE` · `CONTENT` |
| 정제(sanitize) | `HtmlTextUtils.sanitize` — jsoup `Safelist.relaxed()`로 허용 태그·속성만 남김 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/FaqController.java` | `GET /faqs` 지원자 공개 조회 |
| controller | `{BE}/controller/AdminFaqController.java` | `/admin/faq-categories/**`·`/admin/faqs/**` 관리자 CRUD·정렬 |
| controller | `{BE}/controller/BoardController.java` | `/board/notices` 공지 목록·상세·등록 |
| service | `{BE}/service/FaqService.java` | 공개 조회 그룹핑, 카테고리/FAQ CRUD, sortOrder 자동 부여, reorder 검증 |
| service | `{BE}/service/NoticeService.java` | 공지 목록(검색·정렬·페이징), 단건 조회, 등록 |
| entity | `{BE}/domain/entity/FaqCategory.java` | 테이블 `faq_category`(unique `uk_faq_category_name`). 이름 필수 검증, `update`는 sortOrder 불변 |
| entity | `{BE}/domain/entity/Faq.java` | 테이블 `faq`(FK `faq_category_id`). 질문·답변 필수/trim, 질문 500자 |
| entity | `{BE}/domain/entity/Notice.java` | 테이블 `notice`. 생성 시 `contentText` 추출. 수정 메서드 없음 |
| repository | `{BE}/domain/repository/FaqCategoryRepository.java` | 정렬 조회, 이름 중복(`existsByName`·`findByName`) |
| repository | `{BE}/domain/repository/FaqRepository.java` | 카테고리별 정렬 조회, 공개용 `findVisibleFaqs`(fetch join), 활성 건수 |
| repository | `{BE}/domain/repository/NoticeRepository.java` | `JpaSpecificationExecutor` |
| repository | `{BE}/domain/repository/NoticeSpecification.java` | 검색 조건(`searchType`+`keyword` LIKE) |
| dto | `{BE}/dto/request/FaqCategorySaveRequest.java` | `{ name, active? }` 생성·수정 공용 |
| dto | `{BE}/dto/request/FaqCategoryReorderRequest.java` | `{ ids }` |
| dto | `{BE}/dto/request/FaqSaveRequest.java` | `{ categoryId, question, answer, active? }` 생성·수정 공용 |
| dto | `{BE}/dto/request/FaqReorderRequest.java` | `{ categoryId, ids }` |
| dto | `{BE}/dto/request/NoticeSaveRequest.java` | `{ title, content, isPinned }` |
| dto | `{BE}/dto/response/FaqCategoryResponse.java` | 관리자 카테고리(`faqCount` = 활성 FAQ 수) |
| dto | `{BE}/dto/response/FaqResponse.java` | 관리자 FAQ 1건 |
| dto | `{BE}/dto/response/PublicFaqCategoryResponse.java` | 공개 카테고리 + `faqs` |
| dto | `{BE}/dto/response/PublicFaqResponse.java` | 공개 FAQ(`sortOrder`·`active` 미노출) |
| dto | `{BE}/dto/response/NoticeListResponse.java` | 목록 행. `createdAt`은 `yyyy-MM-dd` 문자열 |
| dto | `{BE}/dto/response/NoticeDetailResponse.java` | 상세. `from()`에서 `contentHtml` 정제 |
| enum | `{BE}/enumeration/NoticeSearchType.java` | `ALL`, `TITLE`, `CONTENT` |
| exception | `{BE}/exception/FaqNotFoundException.java` | 카테고리·FAQ 없음 → 404 |
| exception | `{BE}/exception/InvalidFaqException.java` | 필수값·이름 중복·reorder 불일치 → 400 |
| exception | `{BE}/exception/NoticeNotFoundException.java` | 공지 없음. **전역 핸들러 매핑 없음 → 500**(함정 참고) |
| test | `{BT}/controller/FaqControllerTest.java` | FAQ API 통합(`@SpringBootTest`+MockMvc): 공개 노출 규칙, CRUD, reorder, 이동, soft delete, 비인증 401 |
| test | `{BT}/domain/repository/FaqRepositoryTest.java` | `findVisibleFaqs`·활성 건수·비활성 포함 정렬(`@DataJpaTest`) |
| test | `{BT}/dto/response/NoticeDetailResponseTest.java` | 정제: script·`onerror`·`javascript:` 제거, 서식 유지, null 통과 |

카드 밖 의존: 본문 처리 유틸 `{BE}/common/util/HtmlTextUtils.java`(공통 기반 `{BE}/common/` 소속, 현재 사용처는 `Notice`·`NoticeDetailResponse`뿐), 페이지 응답 `{BE}/dto/response/PageResponse.java`(공통), 인가 `{BE}/config/SecurityConfig.java`·`{BT}/config/SecurityConfigTest.java`([auth-account](auth-account.md) 소유), 예외 매핑 `{BE}/exception/GlobalExceptionHandler.java`(공통).

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminFaqManage`(`/admin/faqs`) — 부모 `meta.roles = ADMIN_ROLES` 상속(공유 파일) |
| route | `{FE}/routes/applicantRoutes.ts` | `ApplicantFaq`(`/applicant/faq`)·`NoticeList`(`/applicant/noticeList`), 둘 다 `meta.public: true`(공유 파일) |
| view | `{FE}/views/admin/faq/*` | 현재 `AdminFaqManageView.vue` 1개. 좌측 카테고리(↑↓·수정·삭제) + 우측 FAQ 표(↑↓·노출 태그), 카테고리/FAQ 모달 |
| view | `{FE}/views/applicant/FaqView.vue` | 지원자 FAQ. 카테고리 선택 → Q/A 아코디언(다중 펼침), 답변 `white-space: pre-wrap` |
| view | `{FE}/views/applicant/NoticeListView.vue` | 지원자 공지 목록(a-table, 8건/페이지) + 상세 모달(`v-html`), 오류/빈 상태 구분 |
| api | `{FE}/api/faqApi.ts` | `fetchFaqs` |
| api | `{FE}/api/adminFaqApi.ts` | 관리자 10개 호출(카테고리 5 + FAQ 5) |
| api | `{FE}/api/boardApi.ts` | 공지 함수 `fetchNotices`·`fetchNoticeDetail`. 같은 파일의 공고 함수는 [job-posting](job-posting.md) |
| types | `{FE}/types/faq.ts` | `PublicFaq`·`PublicFaqCategory`·`FaqCategory`·`Faq`·저장 요청 |
| types | `{FE}/types/notice.ts` | `NoticeListItem`·`NoticeSearchParams`·`NoticeDetail` |

store 없음. 관련 vitest spec 없음. 두 지원자 화면은 `{FE}/views/applicant/ApplicantBreadcrumb.vue`(카드 없는 파일)를 쓴다. `{FE}/views/common/htmlView.vue`(카드 없는 공용 컴포넌트)는 **공지 화면에서 쓰지 않는다** — 현재 사용처는 `{FE}/views/applicant/ApplicantPrivacy.vue`뿐이고, 공지 상세는 `NoticeListView.vue` 안에서 직접 `v-html`한다.

## API 계약

모든 응답은 `ApiResponse<T>` = `{ success, data, message }`. 권한 열 `ADMIN·RECRUIT_ADMIN` = `hasAnyAuthority(ROLE_ADMIN, ROLE_RECRUIT_ADMIN)`. `active?`는 null 허용(생성 시 null → true, 수정 시 null → 기존값 유지).

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /faqs | 없음 | `[{ id, name, faqs: [{ id, question, answer }] }]` 노출 가능 항목만 | 공개(anyRequest) |
| 🟢 | GET | /admin/faq-categories | 없음 | `[{ id, name, sortOrder, active, faqCount }]` 비활성 포함 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faq-categories | `{ name, active? }` | 카테고리 1건(`faqCount: 0`) | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faq-categories/reorder | `{ ids }` 전체 카테고리 id | `data: null` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faq-categories/{categoryId} | `{ name, active? }` | 카테고리 1건 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faq-categories/{categoryId}/delete | 본문 없음 | `data: null` | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /admin/faqs | query `categoryId`(필수) | `[{ id, categoryId, question, answer, sortOrder, active }]` 비활성 포함 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faqs | `{ categoryId, question, answer, active? }` | FAQ 1건 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faqs/reorder | `{ categoryId, ids }` 그 카테고리 전체 FAQ id | `data: null` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faqs/{faqId} | `{ categoryId, question, answer, active? }` | FAQ 1건 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/faqs/{faqId}/delete | 본문 없음 | `data: null` | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /board/notices | query `page`(0)·`size`(10)·`searchType`(`ALL`)·`keyword?` | `PageResponse<{ id, title, pinned, createdAt }>` | 공개(anyRequest) |
| 🟢 | GET | /board/notices/{noticeId} | path `noticeId` | `{ id, title, contentHtml, pinned, createdAt }` | 공개(anyRequest) |
| 🟢 | POST | /board/notices | `{ title, content, isPinned }` | `data`: 새 공지 id(Long) | ADMIN·RECRUIT_ADMIN(명시 매처) |

### 엔드포인트 상세

**FAQ 공통 — 🟢 확정(2026-08-26)**
- `sortOrder`는 요청 필드에 없다. 생성 시 서버가 부여하고 변경은 reorder 전용이다(FE `adminFaqApi` 주석과 일치).
- 삭제는 `active=false` soft delete, 이미 비활성이면 멱등 200. row 삭제 API는 없다.
- 오류 형식: 400 `InvalidFaqException`·Bean Validation(`message`에 한글 사유), 404 `FaqNotFoundException`. 카테고리명 unique 경합은 DB 제약 → 409 `"이미 처리되었거나 중복된 데이터입니다."`(전역 `DataIntegrityViolationException` 매핑).
- 매핑: FE `faqApi.fetchFaqs()` ↔ `FaqController.getFaqs()`. `adminFaqApi.fetchCategories`/`createCategory`/`updateCategory`/`deleteCategory`/`reorderCategories`/`fetchFaqs`/`createFaq`/`updateFaq`/`deleteFaq`/`reorderFaqs` ↔ `AdminFaqController`의 같은 이름 메서드(`getCategories`·`getFaqs`는 fetch 계열).

**GET /faqs**
- 인증 불필요, 페이징 없음. `active=true` 카테고리 안의 `active=true` FAQ만. 정렬은 카테고리 `sortOrder ASC, id ASC` → FAQ `sortOrder ASC, id ASC`.
- 노출 가능한 FAQ가 0건인 카테고리는 응답에서 빠진다(빈 카테고리 클릭 방지).
- FE는 첫 카테고리를 자동 선택하고, 선택된 카테고리의 FAQ만 우측에 보여준다. 카테고리를 바꾸면 펼친 항목을 모두 닫는다.

**카테고리 API**
- `GET /admin/faq-categories`: 비활성 포함 전체, `sortOrder ASC, id ASC`. `faqCount` = 그 카테고리의 **활성 FAQ 수**(카테고리 자신의 `active`와 무관).
- `POST /admin/faq-categories`: `sortOrder` = 전체 카테고리 최대값 + 1(없으면 0). 오류 400(`name` 공백·100자 초과·이름 중복).
- `POST /admin/faq-categories/{categoryId}`: 이름·노출만 바꾼다(`sortOrder` 불변). 자기 이름 유지는 허용, 다른 카테고리와 이름 충돌은 400. 없는 id 404.
- `POST /admin/faq-categories/{categoryId}/delete`: `active=false`. 하위 FAQ의 `active`는 건드리지 않는다 — 카테고리가 비활성이면 공개 조회에서 통째로 빠진다. 없는 id 404.
- `POST /admin/faq-categories/reorder`: `ids`는 **비활성 포함 전체** 카테고리 id 집합과 정확히 같아야 한다(누락·중복·미존재 id → 400 `"카테고리 정렬 목록이 전체 대상과 일치하지 않습니다."`). 빈 배열은 400(`@NotEmpty`).

**FAQ API**
- `GET /admin/faqs`: `categoryId` 누락 400 `"Invalid request."`, 없는 카테고리 404. 비활성 포함, `sortOrder ASC, id ASC`.
- `POST /admin/faqs`: `sortOrder` = 그 카테고리 FAQ(비활성 포함) 최대값 + 1. 오류 400(`question`/`answer` 공백, `question` 500자 초과), 404(카테고리 없음). 비활성 카테고리에도 생성 가능.
- `POST /admin/faqs/{faqId}`: `categoryId`를 바꾸면 카테고리 이동 — 대상 카테고리 최대값 + 1로 `sortOrder` 재부여(원래 순서 유지 불가). 같은 카테고리면 `sortOrder` 유지. 오류 400(검증), 404(FAQ·카테고리 없음).
- `POST /admin/faqs/{faqId}/delete`: `active=false`, 없는 id 404.
- `POST /admin/faqs/reorder`: `ids`는 그 카테고리의 **비활성 포함 전체** FAQ id 집합과 같아야 한다(누락·중복·다른 카테고리 id → 400). 없는 카테고리 404.
- 화면 동작: ↑↓ 클릭 → 로컬 배열에서 인접 항목과 교환 → 전체 id 순서를 즉시 reorder로 전송(별도 저장 버튼 없음) → 실패 시 이전 배열로 되돌리고 오류 토스트.

**공지 조회 — 🟢 확정(2026-09-18, 백엔드 정제·테스트 / 프론트 무변경)**
- `GET /board/notices`: 정렬 `pinned DESC, createdAt DESC`. 응답 `PageResponse` = `{ content, page, size, totalElements, totalPages, first, last }`(0-base `page`). 목록 행 `createdAt`은 `"yyyy-MM-dd"` 문자열. 본문 필드는 목록에 없다.
- `searchType` 값이 enum 밖이면 400 `"Invalid request."`. `keyword`가 비었거나 공백이면 검색 조건 없음.
- FE `NoticeListView`는 `size=8`로 호출하고, 번호 열은 `total - ((current-1)*pageSize + index)`로 계산한다.
- `GET /board/notices/{noticeId}`: `contentHtml`은 서버가 응답 시 jsoup `Safelist.relaxed()`로 정제한 값(script·이벤트 핸들러 속성·`javascript:` 링크 제거, 서식 유지). FE는 이 값을 `v-html`로 렌더한다. `createdAt`은 `LocalDateTime`(FE는 앞 10자만 표시). 없는 id는 404가 아니라 **500**(함정 참고).
- 매핑: FE `boardApi.fetchNotices()`/`fetchNoticeDetail()` ↔ `BoardController.getNotices()`/`getNotice()`.

**공지 등록 — 🟢 확정(2026-09-18, SecurityConfig 매처·테스트)**
- `POST /board/notices`: **관리자 전용**(`ROLE_ADMIN`·`ROLE_RECRUIT_ADMIN`). 비인증 401, 그 외 권한 403. `BoardController` 기본 경로가 `/board`라 broad `/api/admin/**` 매처에 안 걸려 `POST /api/board/**` 전용 매처로 막는다.
- 요청: `title` `@NotBlank`(위반 400), `content`(원본 HTML, 요청 검증 없음), `isPinned`(boolean, 생략 시 false). 응답 `data` = 새 id.
- **FE 미사용**(관리자 공지 관리 화면 미구현, `recruit_front/src/api`에 호출처 없음).

## 규칙·불변식

**FAQ 정렬(reorder)**
- `sortOrder`는 요청으로 받지 않는다. 생성 시 스코프(카테고리 = 전역, FAQ = 카테고리 내부) 최대값 + 1, 비어 있으면 0. 비활성 항목도 최대값 계산에 포함된다. ({BE}/service/FaqService.java — nextCategorySortOrder/nextFaqSortOrder)
- reorder 요청 `ids`는 중복이 없고 대상 전체(비활성 포함) id 집합과 정확히 같아야 한다. 부분 정렬을 허용하면 남은 항목의 순서가 어긋나기 때문이다. 위반 → `InvalidFaqException` 400. ({BE}/service/FaqService.java — validateReorderIds)
- reorder는 배열 순서대로 `sortOrder`를 0..n-1로 다시 매긴다(정규화). ({BE}/service/FaqService.java — reorderCategories/reorderFaqs)
- 카테고리 수정은 `sortOrder`를 바꾸지 않는다. ({BE}/domain/entity/FaqCategory.java — update)
- FAQ를 다른 카테고리로 옮기면 대상 카테고리 최대값 + 1(맨 뒤)로 재부여하고, 같은 카테고리면 유지한다. ({BE}/service/FaqService.java — updateFaq, {BE}/domain/entity/Faq.java — update)

**FAQ 노출·삭제**
- 지원자 노출 조건 = `faq.active && category.active`. 노출 FAQ가 0건인 카테고리는 응답에서 빠진다(FAQ 목록에서 그룹핑하므로 빈 카테고리는 생기지 않음). ({BE}/domain/repository/FaqRepository.java — findVisibleFaqs, {BE}/service/FaqService.java — getPublicFaqs)
- 관리자 조회는 비활성 포함. `faqCount`는 활성 FAQ만 센다. ({BE}/service/FaqService.java — getCategories)
- 삭제 = `active=false`(멱등). 카테고리 삭제는 하위 FAQ `active`를 바꾸지 않는다. 복구는 수정 API로 `active=true`. ({BE}/service/FaqService.java — deleteCategory/deleteFaq)
- `active`가 null이면 생성 시 true, 수정 시 기존값 유지. ({BE}/domain/entity/FaqCategory.java — create/update, {BE}/domain/entity/Faq.java — create/update)

**FAQ 입력 검증**
- 카테고리명: 필수·100자 이하(DTO), trim 후 저장. **비활성(삭제된) 카테고리를 포함해** 이름이 유일해야 한다. 수정 시 자기 자신은 제외. ({BE}/service/FaqService.java — createCategory/updateCategory, {BE}/domain/entity/FaqCategory.java — requireText)
- 질문: 필수, trim, 500자 이하(DTO + 엔티티 이중 검증). 답변: 필수, trim, 길이 제한 없음. ({BE}/domain/entity/Faq.java — requireQuestion/requireAnswer)
- 답변은 평문이다. HTML을 저장·렌더하지 않고 FE는 텍스트 보간 + `white-space: pre-wrap`으로 줄바꿈만 살린다. 관리자 폼 안내 문구도 "HTML 태그는 사용할 수 없습니다". 답변에 `v-html`을 쓰지 않는다. (`{FE}/views/applicant/FaqView.vue` — `.qa-answer-text`)
- 없는 카테고리·FAQ → `FaqNotFoundException` 404. ({BE}/service/FaqService.java — getCategoryOrThrow/updateFaq/deleteFaq)

**공지 본문 HTML 처리**
- 저장은 원본 HTML 그대로(`contentHtml`), 정제는 **응답 시점**에 한다. ({BE}/dto/response/NoticeDetailResponse.java — from)
- 정제 규칙 = jsoup `Safelist.relaxed()`, base URI `""`, `prettyPrint(false)`. `null`이면 `null` 반환. 실측(jsoup 1.22.2): ({BE}/common/util/HtmlTextUtils.java — sanitize)
  - 남는 태그: `a b blockquote br caption cite code col colgroup dd div dl dt em h1~h6 i img li ol p pre q small span strike strong sub sup table tbody td tfoot th thead tr u ul`.
  - 제거: `script`·`iframe`·`video`·`hr`·`s`·`mark`·`font`·`figure` 등 목록 밖 태그(텍스트는 남고 태그만 빠짐, `script`·`iframe`은 통째로), 모든 `style`·`class`·`on*`·`target` 속성.
  - URL: `a[href]`는 `http`·`https`·`ftp`·`mailto`만, `img[src]`는 `http`·`https`만. **상대 경로와 `data:` 이미지는 속성이 지워진다**(본문 이미지는 절대 URL이어야 함).
- 검색용 `contentText` = `Jsoup.parse(html).text()`를 1000자에서 자름. 생성 시에만 계산한다(수정 API 없음). ({BE}/common/util/HtmlTextUtils.java — extractText, {BE}/domain/entity/Notice.java — Notice 생성자)
- 새로 `v-html`로 렌더할 HTML도 응답 DTO에서 `HtmlTextUtils.sanitize`를 거친다(백엔드 규칙 — `recruit_back/recruit_backend/AGENTS.md`).

**공지 목록·검색·등록**
- 정렬 `pinned DESC, createdAt DESC`. 노출 여부 필드가 없어 등록된 공지는 전부 공개된다(`visible` 조건은 주석 처리돼 있음). ({BE}/service/NoticeService.java — getNotices, {BE}/domain/repository/NoticeSpecification.java — search)
- 검색: `keyword` trim + 소문자 → `LIKE %keyword%`(대소문자 무시). `ALL`(또는 null) = 제목 OR `contentText`, `TITLE` = 제목, `CONTENT` = `contentText`. `%`·`_`는 이스케이프하지 않아 와일드카드로 동작한다. ({BE}/domain/repository/NoticeSpecification.java — search)
- `page`·`size` 범위 검증이 없다. 음수 `page`나 `size` ≤ 0은 `PageRequest.of`의 `IllegalArgumentException` → 500, `size` 상한 없음. ({BE}/service/NoticeService.java — getNotices)
- 없는 공지 → `NoticeNotFoundException`. `GlobalExceptionHandler`에 핸들러가 없고(범용 `Exception` 핸들러도 없음) 예외 클래스에 `@ResponseStatus`도 없어 **HTTP 500**이 나간다. ({BE}/service/NoticeService.java — getNotice)
- 등록: `title`만 요청 단계 검증(`@NotBlank`). `content`가 null이면 요청 검증을 통과해 DB `NOT NULL` 제약에서 실패한다(전용 400 없음). `title` 길이 제한도 없다(DB 기본 varchar 255). ({BE}/dto/request/NoticeSaveRequest.java, {BE}/service/NoticeService.java — create)

**권한**
- FAQ 관리자 API 10개는 broad `/api/admin/**` 매처(ADMIN·RECRUIT_ADMIN)로 막는다(전용 매처 없음). `GET /faqs`는 `anyRequest().permitAll()`. (`{BE}/config/SecurityConfig.java` — [auth-account](auth-account.md) 소유)
- `/board` 아래는 **POST만** 관리자 전용(`POST /api/board/**`), GET은 전부 공개. `/board` 아래에 새 POST를 만들면 자동으로 막히지만, 관리자용 GET을 `/board` 아래에 만들면 공개된다. (`{BE}/config/SecurityConfig.java`)
- FE 라우트: `/admin/faqs`는 부모 `ADMIN_ROLES` 가드, `/applicant/faq`·`/applicant/noticeList`는 `meta.public: true`.

**FE 상태 처리**
- 공지 목록은 오류와 빈 목록을 구분한다: 실패 시 `loadFailed=true` → 표 `emptyText` 슬롯에 "공지사항을 불러오지 못했습니다." + "다시 시도" 버튼, 총 건수 숨김. 성공한 빈 목록만 기본 빈 표시. (`{FE}/views/applicant/NoticeListView.vue` — loadNotices)
- 관리자 FAQ 목록은 응답이 오기 전에 다른 카테고리를 고르면 늦게 온 응답을 버린다(다른 카테고리 FAQ id로 reorder가 나가 400 나는 것 방지). (`{FE}/views/admin/faq/AdminFaqManageView.vue` — loadFaqs)
- 관리자 화면 클라이언트 검증은 서버 규칙 미러(카테고리명 필수, 질문 필수·500자, 답변 필수, 입력 trim 후 전송). 서버가 단일 출처.

## 변경 레시피

### FAQ 필드·검증 추가(예: 답변 길이 제한)
1. `{BE}/domain/entity/Faq.java`(또는 `FaqCategory.java`) 필드·검증 → `{BE}/dto/request/FaqSaveRequest.java` 어노테이션 → 응답 DTO(`FaqResponse`, 지원자에게 보일 필드면 `PublicFaqResponse`) → `{BE}/service/FaqService.java`.
2. 400으로 내려야 하는 검증은 `InvalidFaqException`을 쓴다(핸들러 매핑됨). 운영 DB가 ddl-auto `update`가 아니면 수동 SQL을 `recruit_back/recruit_backend/docs/ops/`에 추가한다(FAQ·공지용 DDL 파일은 현재 없음).
3. `{BT}/controller/FaqControllerTest.java`에 케이스 추가(쿼리 변경이면 `{BT}/domain/repository/FaqRepositoryTest.java`도) → 검증 명령 실행.
4. `{FE}/types/faq.ts` → `{FE}/api/adminFaqApi.ts`(시그니처 변경 시) → `{FE}/views/admin/faq/AdminFaqManageView.vue`(폼·`validateFaqForm`) → 지원자 노출이면 `{FE}/views/applicant/FaqView.vue`.
5. `npm run type-check`, 카드 `## API 계약`·`## 규칙·불변식` 갱신, `node tools/check-docs.mjs`.

### 관리자 공지 관리 화면 추가(등록·수정·삭제)
1. 카드 API 표에 새 엔드포인트를 🟡로 먼저 적는다. 쓰기는 레포 관례대로 POST(`/board/notices/{id}`, `/board/notices/{id}/delete`) — `POST /api/board/**` 매처로 자동 보호된다. **관리자 전용 조회(비공개 포함 목록 등)는 `/board` 아래 GET으로 만들지 않는다**(공개됨). `/admin/notices`처럼 `/admin` 아래에 두거나 `SecurityConfig`에 GET 매처를 추가하고 `{BT}/config/SecurityConfigTest.java`에 401/403/통과 케이스를 넣는다.
2. `{BE}/domain/entity/Notice.java`에 `update`를 추가할 때 `contentText`도 다시 계산한다(`HtmlTextUtils.extractText`). 삭제를 soft로 할지 정하고, 노출 필드를 넣으면 `NoticeSpecification`의 주석 처리된 `visible` 조건을 함께 정리한다.
3. 수정·삭제가 없는 id를 받으면 404가 되도록 `GlobalExceptionHandler`에 `NoticeNotFoundException` 핸들러를 추가한다(현재 500).
4. 서비스·컨트롤러 테스트 추가(현재 `NoticeService`·`BoardController` 전용 테스트 없음) → 백엔드 검증.
5. FE: `{FE}/api/boardApi.ts`(또는 관리자 전용 api 모듈) → `{FE}/types/notice.ts` → 새 화면(`{FE}/views/admin/` 아래) → `{FE}/routes/adminRoutes.ts` children. 새 `.vue`는 이 카드 `## 파일 지도`에 등록한다. 메뉴 노출은 배포 후 `/admin/menus`에서 등록([role-menu](role-menu.md)).
6. `npm run type-check`, 카드 🟢 확정, `node tools/check-docs.mjs`.

### 공지 본문 허용 태그·속성 변경
1. `{BE}/common/util/HtmlTextUtils.java` — `sanitize`의 `Safelist`를 바꾼다(예: `Safelist.relaxed().addAttributes(...)`). 공통 유틸이므로 다른 사용처가 생겼는지 먼저 grep한다. `style`·`on*`·`javascript:`를 다시 허용하지 않는다.
2. `{BT}/dto/response/NoticeDetailResponseTest.java`에 허용/제거 케이스 추가 → 검증 명령 실행.
3. FE 스타일이 필요하면 `{FE}/views/applicant/NoticeListView.vue`의 `.notice-detail-content :deep(...)` 규칙을 추가한다(서버가 `class`·`style`을 지우므로 태그 선택자로만 꾸민다).
4. 카드 `## 규칙·불변식` "공지 본문 HTML 처리" 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.FaqControllerTest" --tests "com.shinyoung.recruit.domain.repository.FaqRepositoryTest" --tests "com.shinyoung.recruit.dto.response.NoticeDetailResponseTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.FaqControllerTest" --tests "com.shinyoung.recruit.domain.repository.FaqRepositoryTest" --tests "com.shinyoung.recruit.dto.response.NoticeDetailResponseTest" --no-daemon
```

공지 권한(`/board` 매처)을 건드렸으면 [auth-account](auth-account.md) 소유 `--tests "com.shinyoung.recruit.config.SecurityConfigTest"`도 함께 돌린다(공지 4건: 목록 공개 200, 등록 비인증 401, 지원자 403, 관리자 통과).

프론트(`recruit_front/`에서). 이 도메인 전용 vitest spec은 없다.

```bash
npm run type-check
```

## 함정·결정

- `8d7485d` 공지 등록 `POST /board/**`가 매처 누락으로 비인증 허용 → 저장형 XSS 경로였다. 전용 매처 + 응답 시 jsoup 정제 두 겹으로 막았다. **둘 중 하나도 제거하지 않는다**(FE가 `v-html`로 렌더).
- `8d7485d` 관리자 FAQ 화면 카테고리 전환 경합: 늦게 온 FAQ 응답이 다른 카테고리 목록을 덮어 reorder가 400 나던 문제. `loadFaqs`의 `categoryId` 비교 가드를 지우지 않는다.
- `86d12c9` 공지 목록 조회 실패가 "공지 없음"으로 보이던 문제 → `loadFailed`로 오류/빈 상태 구분. 반면 **지원자 FAQ 화면은 여전히 구분하지 않는다**: 조회 실패 시 토스트와 함께 "등록된 FAQ가 없습니다."가 표시된다(`FaqView.vue` `loadFaqs`).
- 없는 공지 id는 **500**이다(`NoticeNotFoundException` 매핑 없음, 코드로 확인). FE 상세 모달(`openNoticeDetail`)은 `catch`가 없어 실패 시 빈 모달만 남고 오류 안내가 없다(`client.ts` 인터셉터는 401·403만 처리).
- 삭제된(비활성) FAQ 카테고리도 이름을 계속 점유한다 → 같은 이름으로 새로 만들면 400. 다시 쓰려면 기존 카테고리를 수정 API로 `active=true` 복구한다.
- soft delete된 카테고리·FAQ는 관리자 목록과 reorder 대상 집합에 영원히 남는다. reorder를 직접 호출할 때 비활성 id를 빼면 400이다.
- FE `{FE}/types/notice.ts`의 `NoticeListItem.content`는 백엔드 `NoticeListResponse`에 없는 필드다(현재 화면에서 안 씀, 항상 undefined). 목록 본문 미리보기를 만들려면 백엔드 DTO부터 바꾼다.
- `pinned` 공지는 FE에서 "NEW" 배지로 보인다(고정/공지 표시가 아님). 표시 의미를 바꾸려면 화면만 고치면 된다(계약 영향 없음).
- 지원자 FAQ·공지 화면은 `ApplicantBreadcrumb`를 쓰므로 `/applicant/faq`·`/applicant/noticeList`가 `APPLICANT` 메뉴에 등록돼 있어야 한다(없으면 breadcrumb 조회 500 — [role-menu](role-menu.md)).
- `POST /admin/faq-categories/reorder`와 `POST /admin/faq-categories/{categoryId}`(FAQ도 동일)가 공존한다. Spring은 리터럴 경로를 우선하므로 동작하지만, 새 리터럴 하위 경로를 추가할 때 `{id}` 패턴과 겹치지 않게 한다.
- 공지 검색 키워드의 `%`·`_`는 와일드카드로 동작하고, `CONTENT` 검색은 본문 텍스트 앞 1000자만 대상이다.
- 이 도메인 ADR·수동 DDL은 없다. `faq`·`faq_category`·`notice` 테이블은 ddl-auto(`{BR}/application.yaml`, 기본 `update`)로 생성된다.
