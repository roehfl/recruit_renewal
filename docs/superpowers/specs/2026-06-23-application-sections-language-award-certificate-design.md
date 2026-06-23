# 설계서: 지원서 폼 섹션 3종 (어학 · 수상 · 자격증)

- 작성일: 2026-06-23
- 상태: 설계 확정(사용자 결정 반영) → 구현 계획 단계로 진행
- 범위: 프론트엔드(`recruit_front/`)만. 백엔드는 이미 구현되어 있어 **변경 없음**.

## 1. 배경 / 목표

`dist/`의 3개 HTML 목업(`어학섹션.html`, `수상섹션.html`, `자격증섹션.html`)은 자체 추출형 번들이며, 실제 콘텐츠는 `__bundler/template` 안에 들어 있다. 추출본은 `dist/_extracted/*.rawhtml`(원본 마크업) 및 `*.txt`(가시 텍스트)로 저장해 두었다.

이 목업들은 **독립 페이지가 아니라 지원서 작성 마법사(`ApplicationFormView`)의 섹션**이다. 목업이 보여주는 상단 헤더 / 스텝(`5/8` 등) / 하단 버튼(이전·다음·임시저장·최종제출)은 이미 `ApplicationFormView.vue`가 제공하며, 각 번들은 마법사의 한 섹션 본문에 해당한다(스텝 번호 어학=5, 수상=6, 자격증=4).

목표: 세 섹션을 `BasicInfoSection.vue`와 동일한 **섹션 컴포넌트 패턴**으로 구현하여, 현재 placeholder로 렌더되는 `LANGUAGE / AWARD / CERTIFICATE`를 실제 입력 화면으로 교체한다.

### 비목표 (Out of scope)

- 새 라우트 추가(섹션은 기존 `/applicant/:applicationId/form` 안에서 렌더됨).
- 백엔드 변경(컨트롤러/DTO/서비스 모두 구현 완료, 계약 단일 출처).
- 관리자 조회 화면(`/api/admin/...`).
- 사진 업로드 등 기본정보 고유 위젯.
- `LANGUAGE_CONVERSATION` 공통코드 시드(백엔드/운영에서 별도 등록 — 본 문서 §4-A 권장 코드 참조).

## 2. 아키텍처 (기존 구조 재사용)

`ApplicationFormView.vue`:
- `/applications/{id}/form-page`로 폼 구성을 받아 페이지/섹션을 만들고, 섹션 타입별로 `sectionComponentMap[sectionType]` 컴포넌트를 렌더한다.
- 각 섹션 컴포넌트에 props `{ applicationId, section, page, editable, formPage }`를 내려준다.
- 섹션 컴포넌트가 `defineExpose({ saveDraft, validateBeforeSubmit })`를 노출하면, 하단 **임시저장** 버튼이 `saveDraft()`를, **최종 제출**이 `validateBeforeSubmit()`를 호출한다.
- 현재 `LANGUAGE / AWARD / CERTIFICATE`는 `ApplicationSectionPlaceholder`로 매핑되어 있다 → 실제 컴포넌트로 교체 대상.

기존 참고 구현:
- 컴포넌트: `src/views/applicant/application/sections/BasicInfoSection.vue`
- API 모듈: `src/api/application/sections/basicInfoApi.ts`, `educationApi.ts`
- 타입: `src/types/application/sections/basicInfo.ts`, `education.ts`
- 공통: `apiClient`(`src/api/client.ts`), `commonCodeApi`(`src/api/commonApi.ts`), `logClientEvent`, `getApiErrorMessage`, `ApiResponse<T>`(`src/types/api.ts`)

## 3. 백엔드 계약 (단일 출처 — 이미 구현됨)

세 엔드포인트 모두 **전체 교체(replace-list)** 방식. `GET`으로 현재 목록을 받고, `POST`로 목록 전체를 덮어쓴다. 빈 배열 허용( = "해당 사항 없음").

공통 응답 래퍼: `ApiResponse<T> = { success, data, message?, errors? }` → 실제 페이로드는 `res.data.data`.

### 3.1 어학 LANGUAGE — `/applications/{applicationId}/languages`

- `GET` → `ApiResponse<LanguageResponse[]>`
- `POST` body `LanguageReplaceRequest { languages: LanguageRequest[] }` → `ApiResponse<LanguageResponse[]>`

| 필드 | 타입 | 필수 | 목업 라벨 | 비고 |
|---|---|---|---|---|
| languageName | string | ✓ (NotBlank) | 언어 | 예) 영어 |
| testName | string | ✓ (NotBlank) | 시험명 | 예) TOEIC |
| scoreOrGrade | string | — | 점수/등급 | 예) 950점 / 1급 |
| conversationalAbility | string | — | 회화능력 | 공통코드 `LANGUAGE_CONVERSATION` (§4-A) |
| examDate | LocalDate(`YYYY-MM-DD`) | ✓ (NotNull) | 응시일자 | |
| expiredDate | LocalDate | — | 유효기간 | |
| issuingOrganization | string | — | 주관기관 | 예) ETS |
| sortOrder | int(≥0) | ✓ (NotNull) | — | 배열 index로 자동 부여 |

응답 항목은 위 필드 + `languageId`(화면 미표시).

### 3.2 수상 AWARD — `/applications/{applicationId}/awards`

- `GET` → `ApiResponse<AwardResponse[]>`
- `POST` body `AwardReplaceRequest { awards: AwardRequest[] }` → `ApiResponse<AwardResponse[]>`

| 필드 | 타입 | 필수 | 목업 라벨 | 비고 |
|---|---|---|---|---|
| awardName | string | ✓ (NotBlank) | 수상명 | |
| awardingOrganization | string | ✓ (NotBlank) | 수여기관 | |
| awardDate | LocalDate | ✓ (NotNull) | 수상일자 | |
| description | string | — (Size ≤ 2000) | 수상내용 | textarea + `X / 2,000` 카운터 |
| sortOrder | int(≥0) | ✓ | — | 배열 index |

응답 항목은 위 필드 + `awardId`.

### 3.3 자격증 CERTIFICATE — `/applications/{applicationId}/certificates`

- `GET` → `ApiResponse<CertificateResponse[]>`
- `POST` body `CertificateReplaceRequest { certificates: CertificateRequest[] }` → `ApiResponse<CertificateResponse[]>`

| 필드 | 타입 | 필수 | 목업 라벨 | 비고 |
|---|---|---|---|---|
| certificateName | string | ✓ (NotBlank) | 자격증명 | |
| issuingOrganization | string | ✓ (NotBlank) | 발급기관 | |
| acquiredDate | LocalDate | ✓ (NotNull) | 취득일자 | |
| certificateNumber | string | — | 자격증번호 | |
| expiredDate | LocalDate | — | 유효기간 | |
| scoreOrGrade | string | — | 점수/등급 | |
| sortOrder | int(≥0) | ✓ | — | 배열 index |

응답 항목은 위 필드 + `certificateId`.

## 4. 확정된 설계 결정

### A. 회화능력(conversationalAbility) → 공통코드 `LANGUAGE_CONVERSATION` 사용 (하드코딩 금지)

- `BasicInfoSection`의 `NATIONALITY` 패턴과 동일하게 `commonCodeApi.getCommonCodes('LANGUAGE_CONVERSATION')` 결과로 `<a-select>` 옵션을 렌더한다(`value = code`, `label = displayName`). placeholder `선택`.
- **권장 시드 코드**(목업 `<select>`와 동일): `HIGH`(상) / `MEDIUM`(중) / `LOW`(하). 사용자가 이 코드에 맞춰 공통코드를 등록한다.
- 의존성: 코드 시드 전에는 드롭다운이 비어 있다(빈 옵션). 시드되면 자동 표시. 백엔드는 값 검증을 하지 않고 문자열로 저장한다.

### B. 목업 카드형 레이아웃 재현

- 컴포넌트는 **섹션 본문만** 렌더(패널 헤더/마법사 chrome은 부모 담당).
- 본문 구성(어학 기준, `dist/_extracted/어학섹션_______dc.rawhtml` 132~247행):
  1. **"해당 사항 없음" 체크박스** — `어학 성적 없음 (해당 사항 없음)` / `수상 이력 없음 ...` / `보유 자격증 없음 ...`
  2. NA 선택 시: 점선 박스 안내(`~ 없음으로 표시되었습니다.`)
  3. 항목 카드 목록: 카드마다 좌상단 녹색 번호 pill(`어학 1`)과 우상단 `삭제` 버튼, 그 아래 4열 필드 테이블.
  4. 항목 0개 & NA 아님: 점선 빈 상태 박스(`등록된 ~ 없습니다. 아래 버튼으로 ~ 추가하세요.`)
  5. 하단 점선 풀폭 추가 버튼(`+ 어학 성적 추가` 등).
- 입력 위젯은 **ant-design-vue**로 구현(AGENTS.md 규칙): `a-input`, `a-select`, `a-date-picker`(`value-format="YYYY-MM-DD"`), `a-checkbox`, `a-button`, 수상내용은 `a-textarea :maxlength="2000" show-count`.
- 필드 테이블은 `BasicInfoSection`의 `.apply-table` 스타일(th `#fafafa`, border `#f0f0f0`, 필수 `em` 빨강)을 재사용하고, 카드 래퍼·번호 pill·삭제/추가 버튼·NA 토글·빈 상태를 목업 색상(녹색 계열 `#0f4726`/`#536d2f`/`#f4f8f0`, 점선 `#d9d9d9`)에 맞춰 scoped CSS로 추가한다.

#### 섹션별 필드 그리드(4열: 14% / 36% / 14% / 36%)

- **어학**: (언어\* | 시험명\*) / (점수/등급 | 회화능력) / (응시일자\* | 유효기간) / (주관기관 — colspan)
- **수상**: (수상명\* | 수상일자\*) / (수여기관\* | —) / (수상내용 textarea — colspan, 카운터)
- **자격증**: (자격증명\* | 발급기관\*) / (취득일자\* | 유효기간) / (자격증번호 | 점수/등급)

> 정확한 행/열 배치는 각 `dist/_extracted/*.rawhtml`을 출처로 구현 시 최종 확인한다(어학은 위와 동일 확인됨, 수상/자격증은 구현 단계에서 rawhtml 대조).

### C. 컴포넌트 자체 완결 (공통 컴포저블 추출 안 함)

- 세 컴포넌트는 각각 독립적으로 상태/로직을 보유한다(`BasicInfoSection` 패턴 유지, 리뷰 단순화). 반복 로직 공유 컴포저블(`useSectionList`)은 만들지 않는다.

## 5. 파일 계획

### 신규 (9)

| 경로 | 내용 |
|---|---|
| `src/types/application/sections/language.ts` | `LanguageItem`(폼), `LanguageRequest`, `LanguageResponse`, `LanguageReplaceRequest` |
| `src/types/application/sections/award.ts` | 동일 구조(award) |
| `src/types/application/sections/certificate.ts` | 동일 구조(certificate) |
| `src/api/application/sections/languageApi.ts` | `getApplicationsLanguages(id)`, `replaceApplicationsLanguages(id, payload)` |
| `src/api/application/sections/awardApi.ts` | 동일(awards) |
| `src/api/application/sections/certificateApi.ts` | 동일(certificates) |
| `src/views/applicant/application/sections/LanguageSection.vue` | 어학 섹션 |
| `src/views/applicant/application/sections/AwardSection.vue` | 수상 섹션 |
| `src/views/applicant/application/sections/CertificateSection.vue` | 자격증 섹션 |

### 수정 (2)

| 경로 | 변경 |
|---|---|
| `src/views/applicant/ApplicationFormView.vue` | 3개 컴포넌트 import + `sectionComponentMap`의 `LANGUAGE/AWARD/CERTIFICATE`를 placeholder→실제 컴포넌트로 교체 |
| `api-contract.md`(recruit 루트) | LANGUAGE 🔴→🟢, AWARD·CERTIFICATE 섹션 신규 추가(🟢), 프론트 모듈 경로 기재 |

## 6. 컴포넌트 동작 사양 (3종 공통)

상태:
- `items = reactive<XxxItem[]>([])` — 각 항목은 폼 필드(문자열) 보유.
- `notApplicable = ref(false)` — "해당 사항 없음" 토글.
- `loading = ref(false)`, 어학은 `conversationOptions`(공통코드).

로드(`onMounted`):
1. `GET` 호출 → 응답 배열을 `items`에 매핑.
2. 어학은 추가로 `getCommonCodes('LANGUAGE_CONVERSATION')` 로드.
3. 빈 목록이면 `notApplicable=false`로 두고 빈 상태 표시(백엔드에 NA 전용 플래그가 없으므로 "미입력"과 "해당없음 선언"을 구분 저장하지 않는다 — §8 한계).

조작:
- `addItem()` — 빈 항목 push(`notApplicable`이면 비활성).
- `removeItem(index)` — 해당 항목 제거.
- `toggleNA()` — 체크 시 목록 입력 영역을 **숨김**(메모리의 `items`는 삭제하지 않음)하고 저장 시 `[]`를 전송한다. 체크 해제 시 기존 입력이 다시 보인다. 목업 로직(`showList = !na`)과 동일.

페이로드 빌드:
- `notApplicable`이면 `[]`.
- 아니면 `items`를 매핑하되 `sortOrder = index`(0-based) 부여.

`saveDraft()`(임시저장):
1. 검증(아래 `validate()`) 통과 확인 — 백엔드가 `@NotBlank/@NotNull`을 단일 replace 엔드포인트에서 강제하므로 드래프트도 필수값 충족 필요.
2. `POST` 호출 → 성공 시 응답으로 `items` 갱신(서버 정렬/ID 반영), 실패 시 `logClientEvent`(`APPLICATION_SUBMIT_FAILED` 류) + `getApiErrorMessage` 후 throw(부모 `saveCurrentPage`가 catch).

`validateBeforeSubmit()`(최종 제출 전):
- `notApplicable` → `true`.
- 아니면 각 항목 필수값 검사. 누락 시 `message.warn` + `false`.
- 섹션이 `required`인데 항목 0개 & NA 아님 → `false`.

항목별 필수값:
- 어학: `languageName`, `testName`, `examDate`
- 수상: `awardName`, `awardingOrganization`, `awardDate`
- 자격증: `certificateName`, `issuingOrganization`, `acquiredDate`

`defineExpose({ saveDraft, validateBeforeSubmit })`.

## 7. 계약 문서(api-contract.md) 갱신안

- `### 화면: 지원자 어학` 의 상태를 🔴 → 🟢로, 프론트 경로(`src/api/application/sections/languageApi.ts`, `src/views/applicant/application/sections/LanguageSection.vue`) 기재.
- `### 화면: 지원자 수상 (ApplicationAward)` 신규 섹션 추가(엔드포인트 `/applications/{id}/awards`, 필드 요약, 🟢).
- `### 화면: 지원자 자격증 (ApplicationCertificate)` 신규 섹션 추가(엔드포인트 `/applications/{id}/certificates`, 필드 요약, 🟢).

## 8. 알려진 한계 / 위험

- **NA 영속성 없음**: 백엔드에 "해당없음" 전용 플래그가 없어, "미입력"과 "해당없음 선언" 모두 빈 배열로 저장된다. NA 체크박스는 클라이언트 UX 보조(특히 required 섹션 통과용)다.
- **회화능력 코드 의존**: `LANGUAGE_CONVERSATION` 미시드 시 드롭다운 공백. 운영 시드 필요.
- **드래프트 검증 강제**: 단일 replace 엔드포인트의 Bean Validation 때문에 임시저장도 항목 필수값을 요구한다(빈 카드로 임시저장 불가 → 카드 작성 후 저장).
- 날짜는 `a-date-picker value-format="YYYY-MM-DD"`로 문자열 모델 사용(LocalDate 직렬화와 일치, dayjs 객체 변환 불필요).

## 9. 검증 방법

`recruit_front/`에서:
```bash
npm run type-check     # 기본
npm run build          # 필요 시(type-check 포함)
```
- 타입 정합(요청/응답 타입, props), 컴포넌트 등록, 빌드 통과 확인.
- 단위 테스트는 필요 시에만(`npm run test:unit`).
- 실서버 연동은 백엔드 기동 환경에서 별도 확인(본 슬라이스 범위는 프론트 구현/빌드까지).

## 10. 수용 기준 (Acceptance)

1. 마법사에서 어학/수상/자격증 단계가 placeholder가 아닌 실제 카드형 입력 화면으로 렌더된다.
2. 항목 추가/삭제, "해당 사항 없음" 토글, (어학)회화능력 공통코드 드롭다운이 동작한다.
3. 임시저장 시 해당 엔드포인트로 replace POST가 나가고, 재진입 시 GET 결과가 복원된다.
4. 최종 제출 검증이 누락 필드를 막는다.
5. `npm run type-check` / `npm run build` 통과.
6. `api-contract.md`가 구현과 일치(🟢).
