# 공고 입력화면 — 이미지 기반 입력 설계

- 날짜: 2026-08-12
- 상태: 사용자 브레인스토밍 완료, 스펙 리뷰 대기
- 범위: 관리자 공고 등록/수정/목록/상세(미리보기) 화면 + 공고 이미지 도메인/API + 지원자 공고 상세 렌더 변경

## 1. 핵심 결정

| 결정 | 내용 | 근거 |
|---|---|---|
| 입력 방식 | WYSIWYG(HTML 편집기) **미채택**. 이미지 업로드 전용 | 공고 본문은 디자인된 포스터 이미지만 사용(운영 확인). 편집기의 이점(서식·표·텍스트)을 쓰지 않으면서 리스크(base64 인라인으로 인한 `contentHtml` 비대화, XSS sanitize, 라이브러리 유지보수)만 남음 |
| 저장 구조 | 신규 엔티티 `JobPostingImage` (구조화된 이미지 목록) | HTML 원천 배제로 XSS 차단, 순서변경·개별삭제가 자연스러움, `altText` 강제로 웹접근성(KWCAG) 대응 |
| 작성 흐름 | **한 화면 일괄 저장** → draft 생성(이미지 포함) → 관리자 상세에서 미리보기 → publish | 기존 draft→publish→close 라이프사이클 활용. draft는 지원자에게 미노출이므로 별도 임시저장 개념 불필요 |
| 생성 API 형태 | `POST /admin/job-postings`를 multipart로 확장(JSON 파트 + 이미지 파일 파트) | 한 요청으로 공고+이미지 동시 생성 → 임시 업로드/orphan 파일 청소 배치가 아예 불필요 |
| 수정 UX | 화면은 "일괄 저장" UX, 프론트가 내부적으로 diff 적용(신규만 업로드, 제거만 삭제, 순서 반영) | 변경 없는 이미지 재업로드 낭비 방지. 수정 시점엔 공고가 존재하므로 이미지 단위 API 사용 가능 |
| 메뉴 | 대메뉴 "공고 관리"(path 없는 그룹 라벨) + 소메뉴 "공고 목록", "공고 등록" 2개 | 등록 진입점의 발견성 확보(사용자 결정). "수정"은 대상 선택이 필요하므로 메뉴가 될 수 없음 — 목록 행 클릭으로 진입 |

## 2. 도메인

### 2.1 JobPostingImage (신규 엔티티)

`ApplicationAttachment`의 저장 패턴을 따른다.

- `jobPosting` — `@ManyToOne(LAZY)` FK
- `originalFileName` — 원본 파일명
- `storagePath` — 물리 저장 경로(저장 파일명은 UUID 기반, 클라이언트 노출 금지)
- `contentType`, `fileSize`
- `sortOrder` — 세로 나열 순서
- `altText` — 대체 텍스트, **필수** (웹접근성)
- `BaseEntity` 상속

### 2.2 JobPosting.contentHtml

- 공고 입력·렌더 경로에서 **역할 종료(deprecated)**. 필드 자체는 유지하되 신규 화면에서 읽고 쓰지 않는다.
- 공지사항(Notice)의 `contentHtml`은 이번 범위와 무관하며 그대로 유지한다.
- 기존 개발용 공고 데이터의 `contentHtml`은 마이그레이션하지 않는다(무시).

## 3. API 개요 (구현 슬라이스에서 `api-contract.md`에 🟡 초안 기재)

필드 모양은 요약 수준. 정확한 타입·검증은 백엔드 DTO가 단일 출처.

### 관리자

- `POST /admin/job-postings` — **multipart로 확장**. JSON 파트(기존 기본정보 + 이미지 메타: altText·순서) + 이미지 파일 파트들. draft 공고 + 이미지 일괄 생성.
- `POST /admin/job-postings/{id}` — 기본정보 수정(기존 JSON 유지).
- 이미지 단위 API(수정 화면의 diff 적용용): 추가(multipart) / 삭제 / 순서 변경. 기존 컨트롤러 규약에 따라 **삭제·순서변경도 POST**를 사용한다(현 `JobPostingController`는 수정·발행·마감 모두 POST, PUT/DELETE 미사용).
- `GET /admin/job-postings/{id}` 상세 응답에 `images: [{ id, url, altText, sortOrder }]` 추가.
- 관리자용 이미지 바이너리 서빙(미리보기): admin 인증 하에 draft 포함 응답.

### 공개(지원자)

- 공개 공고 상세 응답에 `images: [{ url, altText, sortOrder }]` 추가.
- 공개 이미지 바이너리 서빙: **발행된(published) 공고의 이미지만** 응답. draft 공고 이미지는 URL을 알아도 404/403.

## 4. 화면

### 4.1 관리자 — 공고 등록 (`/admin/job-postings/new`)

- 기본정보 폼(제목, 접수 기간, 공고 유형 등 기존 필드) + **이미지 섹션**을 한 화면에 배치.
- 이미지 섹션: 드래그앤드롭/파일선택 업로드 → 썸네일 목록 → 순서 변경, 삭제, 장별 altText 입력(필수).
- 저장 버튼 1회 → multipart 생성 요청 → draft 상태로 생성 → 상세 화면으로 이동.

### 4.2 관리자 — 공고 목록 (`/admin/job-postings`)

- 기존 목록 API 사용. 상태 뱃지(draft / 발행 / 마감) 표시. draft 포함 전체 노출.
- 행 클릭 → 상세. 우상단 "공고 등록" 버튼 → 등록 화면(메뉴와 이중 진입점, 무방).

### 4.3 관리자 — 공고 상세 (`/admin/job-postings/:id`)

- **지원자 화면과 동일한 미리보기**: 지원자 상세가 쓰는 렌더 컴포넌트(이미지 세로 나열)를 재사용.
- 수정 진입 + publish / close 버튼. "draft 검수 → 그 자리에서 발행" 동선 완성.

### 4.4 지원자 — 공고 상세

- `images`를 `sortOrder` 순으로 세로 나열, `<img :alt="altText">`.
- 공고에 한해 `contentHtml` `v-html` 렌더 제거.

### 4.5 메뉴 등록 (코드 아님 — 운영 작업)

메뉴 관리 화면(`/admin/menus`)에서 DB에 등록한다.

- 대메뉴: "공고 관리" — path 없는 그룹 라벨
- 소메뉴: "공고 목록" → `/admin/job-postings` (아이콘: `ADMIN_MENU_ICONS`에서 선택)
- 소메뉴: "공고 등록" → `/admin/job-postings/new`

## 5. 검증·제한

- 형식: `jpg` / `png` / `webp`. Content-Type과 파일 시그니처(매직 바이트) 이중 검증.
- 크기: 장당 최대 **10MB**. 공고당 최대 **10장**.
- `altText`: 장당 필수.
- 저장 파일명: UUID 기반. `storagePath`는 응답에 노출하지 않는다(기존 첨부파일 규약과 동일).

## 6. 보안

- draft 노출 차단은 **2중**: ① 공개 목록/상세 API의 발행 필터, ② 공개 이미지 서빙 엔드포인트의 발행 여부 검사.
- 관리자 이미지 서빙은 admin 인증 경로로 분리.
- 업로드 검증 실패(형식·크기·장수 초과)는 명확한 4xx 에러로 응답.

## 7. 범위 밖

- WYSIWYG/텍스트 본문 입력 (필요해지면 별도 슬라이스로 재논의)
- 이미지 리사이징·썸네일 생성·CDN·캐싱
- 공지사항(Notice) 입력 방식 변경
- 기존 `contentHtml` 데이터 마이그레이션
- 메뉴 뱃지 등 메뉴 시스템 자체의 확장

## 8. 구현 순서 (화면 슬라이스 워크플로우 기준)

1. `api-contract.md`에 위 API 🟡 초안 기재
2. 백엔드: `JobPostingImage` + 생성 multipart 확장 + 이미지 단위 API + 서빙(공개/관리자) + 테스트
3. 프론트: 등록/수정 화면(이미지 업로더), 목록 뱃지, 상세 미리보기, 지원자 렌더 변경, `type-check`
4. 계약 🟢 확정, 메뉴 DB 등록(운영), 보고
