# 지원서 작성 화면 Page Layout 설계 정리

## 1. 배경

지원서 작성 화면을 한 화면에 모든 입력 항목을 펼치는 방식이 아니라, 상단 네비게이션과 페이지 단위 입력 화면으로 구성한다.

예상 화면 흐름:

```text
1페이지: 기본정보 + 병역
2페이지: 학력 + 경력
3페이지: 자격증 + 어학 + 수상
4페이지: 첨부파일 + 최종확인
```

지원자는 현재 페이지의 항목을 입력한 뒤 저장 버튼을 누르고 다음 입력 화면으로 이동한다.

프론트엔드는 부모 Vue form 하나를 두고, 각 입력 항목을 개별 item/section Vue 컴포넌트로 분리한다.

```text
ApplicationWriteView.vue
 ├─ ApplicationPageNavigator.vue
 ├─ ApplicationPageRenderer.vue
 │   ├─ BasicInfoSection.vue
 │   ├─ MilitarySection.vue
 │   ├─ EducationSection.vue
 │   ├─ CareerSection.vue
 │   ├─ CertificateSection.vue
 │   ├─ LanguageSection.vue
 │   ├─ AwardSection.vue
 │   ├─ GapPeriodSection.vue
 │   └─ AttachmentSection.vue
 └─ ApplicationSaveBar.vue
```

---

## 2. 결론

현재 `ApplicationFormConfig`의 `useXxx`, `requireXxx` 구조는 유지한다.

그 위에 지원서 작성 화면의 페이지 구성을 담당하는 `ApplicationFormPage`, `ApplicationFormPageItem` 개념을 추가한다.

즉, 기존 지원서 데이터 저장 구조를 갈아엎는 것이 아니라 **지원서 양식 설정 계층에 Page Layout 설정을 추가하는 방식**으로 간다.

```text
지원서 항목 사용 여부/필수 여부 = 기존 ApplicationFormConfig
지원서 작성 화면 구성 = 신규 ApplicationFormPage / ApplicationFormPageItem
실제 입력 데이터 = 기존 Application 도메인 유지
프론트 렌더링 = sectionType → Vue component 매핑
저장 = 초기에는 기존 section별 저장 API 재사용
관리자 미리보기 = layout preview부터 구현
```

---

## 3. height 기준 자동 조절은 하지 않는다

각 item마다 height가 다르기 때문에 화면에 몇 개를 보여줄지 자동 계산하는 방식은 비추천한다.

이유:

1. 실제 height는 입력 데이터 개수에 따라 바뀐다.
   - 학력 1개와 학력 5개는 화면 높이가 다르다.
   - 경력 없음과 경력 5개도 화면 높이가 다르다.
2. 반응형 화면에서 height가 달라진다.
   - PC, 노트북, 태블릿, 모바일에서 같은 항목도 높이가 달라진다.
3. 지원서 작성 화면은 픽셀 배치가 아니라 업무 의미 단위로 구성해야 한다.

따라서 페이지 구성 기준은 다음이어야 한다.

```text
이 항목이 같은 페이지에서 같이 작성되는 게 자연스러운가?
저장/검증 단위로 묶어도 되는가?
지원자가 순서대로 작성하기 쉬운가?
```

---

## 4. 권장 도메인 구조

### 4.1 기존 구조 유지

기존 `ApplicationFormConfig`는 유지한다.

예상 필드:

```text
ApplicationFormConfig
- useEducation
- useCareer
- useCertificate
- useLanguage
- useMilitary
- useAward
- useGapPeriod
- requireEducation
- requireCareer
- requireCertificate
- requireLanguage
- requireMilitary
- requireAward
- requireGapPeriod
```

`useXxx`는 해당 섹션을 지원서 양식에서 사용할지 여부다.  
`requireXxx`는 해당 섹션을 최종 제출 시 필수 완료 대상으로 볼지 여부다.

---

### 4.2 신규 Page Layout 구조 추가

신규 개념:

```text
ApplicationFormPage
- id
- jobPostingId 또는 applicationFormConfigId
- pageNo
- pageTitle
- pageDescription
- sortOrder

ApplicationFormPageItem
- id
- pageId
- sectionType
- sortOrder
```

예시:

```json
{
  "pages": [
    {
      "pageNo": 1,
      "title": "기본 정보",
      "items": [
        { "sectionType": "BASIC_INFO", "sortOrder": 1 },
        { "sectionType": "MILITARY", "sortOrder": 2 }
      ]
    },
    {
      "pageNo": 2,
      "title": "학력 정보",
      "items": [
        { "sectionType": "EDUCATION", "sortOrder": 1 }
      ]
    },
    {
      "pageNo": 3,
      "title": "경력 정보",
      "items": [
        { "sectionType": "CAREER", "sortOrder": 1 },
        { "sectionType": "GAP_PERIOD", "sortOrder": 2 }
      ]
    },
    {
      "pageNo": 4,
      "title": "자격 및 어학",
      "items": [
        { "sectionType": "CERTIFICATE", "sortOrder": 1 },
        { "sectionType": "LANGUAGE", "sortOrder": 2 },
        { "sectionType": "AWARD", "sortOrder": 3 }
      ]
    }
  ]
}
```

---

## 5. sectionType 기준

DB에는 Vue 컴포넌트명이나 프론트 파일명을 저장하지 않는다.

나쁜 예:

```text
BasicInfoForm.vue
EducationSection.vue
CareerSection.vue
```

좋은 예:

```text
BASIC_INFO
MILITARY
EDUCATION
CAREER
CERTIFICATE
LANGUAGE
AWARD
GAP_PERIOD
ATTACHMENT
```

백엔드는 `sectionType`만 내려준다.  
프론트엔드는 `sectionType`을 Vue 컴포넌트로 매핑한다.

```js
const sectionComponentMap = {
  BASIC_INFO: BasicInfoSection,
  MILITARY: MilitarySection,
  EDUCATION: EducationSection,
  CAREER: CareerSection,
  CERTIFICATE: CertificateSection,
  LANGUAGE: LanguageSection,
  AWARD: AwardSection,
  GAP_PERIOD: GapPeriodSection,
  ATTACHMENT: AttachmentSection
}
```

---

## 6. use / require / layout 관계

핵심 규칙:

```text
use=true  → 지원서 양식에 사용되는 섹션
require=true → 사용되는 섹션 중 최종 제출 시 필수 완료 섹션
page layout → 사용되는 섹션을 어느 페이지에 보여줄지 결정
```

따라서 다음 정책을 강제한다.

```text
1. use=false인 section은 page layout에 추가 불가
2. require=true인 section은 반드시 use=true여야 함
3. use=true인 section은 page layout 어딘가에 반드시 1번 포함되어야 함
4. 같은 sectionType은 layout 전체에서 중복 포함 불가
5. require=true인 section이 page layout에 없으면 설정 저장 또는 publish 불가
```

최종적으로는 아래 등식이 성립해야 한다.

```text
use=true인 섹션 목록 == page layout에 배치된 섹션 목록
```

`require`는 layout을 결정하는 값이 아니라 최종 제출 검증을 결정하는 값이다.

```text
require=true인 섹션은 최종 제출 시 완료 여부 검사 대상
```

---

## 7. 상태별 의미

### 7.1 use=false

```text
- page layout 추가 불가
- require=true 불가
- 지원자 화면 미노출
- 최종 제출 검증 제외
```

### 7.2 use=true, require=false

```text
- page layout 필수 포함
- 지원자 화면 노출
- 입력은 선택
- 최종 제출 시 미입력이어도 제출 가능
```

### 7.3 use=true, require=true

```text
- page layout 필수 포함
- 지원자 화면 노출
- 최종 제출 시 완료 필수
```

---

## 8. 관리자 화면 정책

관리자 화면에서는 page item 추가 후보를 만들 때 `use=true`인 항목만 추가 가능하게 한다.

UI 표현 방식은 두 가지가 있다.

### 8.1 숨김 처리

`use=false` 항목은 아예 추가 후보에 표시하지 않는다.

장점:

```text
- 화면이 단순함
```

단점:

```text
- 관리자가 왜 해당 항목을 추가할 수 없는지 모를 수 있음
```

### 8.2 disabled 처리

`use=false` 항목은 비활성화 상태로 보여준다.

예시:

```text
병역 정보 - 사용 안 함 상태이므로 페이지에 추가할 수 없습니다.
```

권장 방식은 disabled 처리다.

관리자가 현재 섹션 사용 설정과 페이지 구성의 관계를 이해하기 쉽다.

---

## 9. use 변경 시 layout 처리 정책

예를 들어 기존 설정이 다음과 같다고 가정한다.

```text
useMilitary=true
1페이지: BASIC_INFO + MILITARY
```

이후 관리자가 다음처럼 변경한다.

```text
useMilitary=false
```

이 경우 layout에 남아 있는 `MILITARY`를 처리해야 한다.

### 안 1. 저장 차단

```text
병역 섹션이 페이지에 포함되어 있으므로 useMilitary=false로 변경할 수 없습니다.
먼저 페이지 구성에서 병역 섹션을 제거하세요.
```

장점:

```text
- 명시적
- 데이터가 자동으로 사라지지 않음
```

단점:

```text
- 관리자 입장에서는 번거로움
```

### 안 2. 자동 제거

```text
useMilitary=false로 변경하면 페이지 구성에서 병역 섹션이 자동 제거됩니다.
```

장점:

```text
- 편함
- 설정 정합성 유지 쉬움
```

단점:

```text
- 관리자가 의도치 않게 layout이 바뀔 수 있음
```

권장 방식:

```text
안 2 + 확인 메시지
```

예시:

```text
병역 정보를 사용 안 함으로 변경하면 페이지 구성에서도 제거됩니다. 계속하시겠습니까?
```

백엔드는 최종 저장 결과가 정합성을 만족하는지만 검증한다.

---

## 10. 백엔드 검증 규칙

프론트에서 막더라도 백엔드에서 반드시 검증해야 한다.

검증 항목:

```text
1. page가 최소 1개 이상이어야 한다.
2. 각 page는 최소 1개 이상의 item을 가져야 한다.
3. pageNo 또는 sortOrder가 중복되면 안 된다.
4. page item의 sectionType은 중복될 수 없다.
5. use=false인 sectionType은 page item에 들어갈 수 없다.
6. use=true인 sectionType은 layout 전체에 반드시 포함되어야 한다.
7. require=true인 sectionType은 반드시 use=true여야 한다.
8. require=true인 sectionType은 layout 전체에 반드시 포함되어야 한다.
9. sectionType은 서버가 정의한 enum 값만 허용한다.
```

예상 검증 흐름:

```java
void validateLayout(ApplicationFormConfig config, List<ApplicationFormPageRequest> pages) {
    Set<ApplicationSectionType> enabledSections = config.enabledSections();
    Set<ApplicationSectionType> requiredSections = config.requiredSections();
    Set<ApplicationSectionType> layoutSections = extractLayoutSections(pages);

    validatePageExists(pages);
    validatePageItemsExist(pages);
    validateNoDuplicatePageOrder(pages);
    validateNoDuplicateSection(pages);

    for (ApplicationSectionType section : layoutSections) {
        if (!enabledSections.contains(section)) {
            throw new IllegalArgumentException("사용하지 않는 지원서 섹션은 페이지에 추가할 수 없습니다.");
        }
    }

    for (ApplicationSectionType section : enabledSections) {
        if (!layoutSections.contains(section)) {
            throw new IllegalArgumentException("사용 설정된 지원서 섹션은 반드시 페이지에 포함되어야 합니다.");
        }
    }

    for (ApplicationSectionType section : requiredSections) {
        if (!enabledSections.contains(section)) {
            throw new IllegalArgumentException("필수 섹션은 사용 설정된 섹션이어야 합니다.");
        }
        if (!layoutSections.contains(section)) {
            throw new IllegalArgumentException("필수 지원서 섹션은 반드시 페이지에 포함되어야 합니다.");
        }
    }
}
```

---

## 11. 공고 상태별 수정 제한

공고가 이미 공개되었거나 접수가 시작된 뒤 layout을 바꾸면 지원자 작성 화면, 진행률, 제출 검증이 꼬일 수 있다.

권장 정책:

```text
공고 DRAFT 상태:
- layout 자유 수정 가능

공고 PUBLISHED 상태:
- page title/description 수정 가능
- section 추가/삭제 제한
- sortOrder 변경 제한 또는 경고
- required 변경 제한 또는 경고

접수 시작 후:
- layout 구조 변경 금지
- 또는 관리자 확인 절차 필요
```

초기 구현에서는 단순하게 다음 정책을 추천한다.

```text
접수 시작 이후 layout 구조 변경 금지
```

---

## 12. 프론트엔드 구조

### 12.1 부모 form + child section 구조

기본 구조:

```text
ApplicationWriteView.vue
 ├─ ApplicationPageNavigator.vue
 ├─ ApplicationPageRenderer.vue
 │   ├─ BasicInfoSection.vue
 │   ├─ MilitarySection.vue
 │   ├─ EducationSection.vue
 │   ├─ CareerSection.vue
 │   └─ ...
 └─ ApplicationSaveBar.vue
```

부모가 layout 정보를 조회하고 현재 page에 포함된 sectionType 목록을 기준으로 section component를 렌더링한다.

---

### 12.2 emit만으로 버티는 구조는 한계가 있음

child component에서 parent로 emit해서 데이터를 전달하는 방식은 가능하다.

하지만 지원서 작성 화면은 데이터가 크다.

예:

```text
- 학력 여러 개
- 경력 여러 개
- 자격증 여러 개
- 어학 여러 개
- 수상 여러 개
- 첨부파일 여러 개
```

모든 데이터를 child → parent emit으로만 올리면 parent component가 비대해질 수 있다.

권장 구조는 Pinia store를 사용하는 방식이다.

```text
Pinia Store:
- applicationDraftStore
- applicationFormLayoutStore
```

각 section은 store를 직접 사용하거나 `v-model`로 최소 단위 데이터만 주고받는다.

---

### 12.3 v-model 방식

```vue
<EducationSection v-model="draft.educations" />
<CareerSection v-model="draft.careers" />
```

장점:

```text
- Vue 패턴에 익숙함
- 부모에서 전체 draft를 명시적으로 볼 수 있음
```

단점:

```text
- parent가 커질 수 있음
```

---

### 12.4 Pinia store 방식

```js
const draftStore = useApplicationDraftStore()
draftStore.updateEducation(...)
```

장점:

```text
- 페이지 이동이 많아도 상태 유지가 쉬움
- section component가 독립적으로 동작 가능
- 임시저장/복구/진행률 계산에 유리함
```

단점:

```text
- store 설계가 필요함
```

권장 방식은 Pinia store 중심 구조다.

---

## 13. 저장 방식

화면은 page 단위로 구성하되, 초기 API 저장은 기존 section별 저장 API를 재사용하는 것이 좋다.

### 13.1 방식 A: section별 저장 API 유지

예시:

```text
PUT /applications/{id}/basic-info
PUT /applications/{id}/educations
PUT /applications/{id}/careers
PUT /applications/{id}/certificates
```

페이지 저장 버튼을 누르면 현재 page에 포함된 section API를 순서대로 호출한다.

장점:

```text
- 기존 구조를 덜 건드림
- 섹션별 validation이 명확함
- 구현 범위가 작음
```

단점:

```text
- 한 페이지에 여러 section이 있으면 API 여러 번 호출
```

초기 구현 권장안이다.

---

### 13.2 방식 B: page 단위 저장 API 추가

예시:

```text
PUT /applications/{id}/pages/{pageNo}
```

body:

```json
{
  "sections": {
    "BASIC_INFO": {},
    "MILITARY": {},
    "EDUCATION": []
  }
}
```

장점:

```text
- 프론트 저장 처리 단순
- 페이지 단위 validation 가능
```

단점:

```text
- 백엔드가 복잡해짐
- sectionType별 분기 처리 필요
- 기존 섹션 저장 로직을 한 번 감싸야 함
```

초기에는 비추천한다.  
필요성이 확인되면 후속 phase에서 추가한다.

---

## 14. 관리자 미리보기

관리자가 page layout을 설정했으면 실제 지원자 화면이 어떻게 나오는지 확인할 수 있어야 한다.

미리보기는 반드시 넣는 것이 좋다.

### 14.1 1단계: layout preview

실제 저장 API 없이 설정된 page/item 기준으로 컴포넌트만 보여준다.

예:

```text
1페이지
- 기본정보
- 병역

2페이지
- 학력

3페이지
- 경력
- 공백기간
```

초기 구현은 이 수준이면 충분하다.

---

### 14.2 2단계: mock data preview

각 section에 샘플 데이터를 넣어서 실제 화면 길이를 대략 보여준다.

예:

```text
학력 2개
경력 2개
자격증 3개
```

이건 후속 개선으로 둔다.

---

## 15. 대안 비교

### 15.1 대안 1: 프론트 고정 템플릿

프론트에서 페이지 구성을 고정한다.

```text
1페이지: 기본정보 + 병역
2페이지: 학력
3페이지: 경력 + 공백
4페이지: 자격 + 어학 + 수상
5페이지: 첨부 + 최종확인
```

장점:

```text
- 제일 빠름
- 백엔드 수정 거의 없음
- 안정적
```

단점:

```text
- 공고별로 다르게 구성 불가
- 관리자 설정 요구가 생기면 다시 뜯어야 함
```

---

### 15.2 대안 2: 관리자 page layout 설정

관리자가 section 단위로 page layout을 설정한다.

장점:

```text
- 공고별 유연성 있음
- 미리보기 가능
- 지원서 작성 UX 개선
- 기존 use/require 설정과 조합 가능
```

단점:

```text
- 관리자 UI가 커짐
- validation 필요
- API/테이블 추가 필요
```

권장안이다.

---

### 15.3 대안 3: field-level form builder

관리자가 필드 단위로 지원서 양식을 구성한다.

예:

```text
이름
생년월일
휴대폰
주소
학력 학교명
학력 전공
경력 회사명
경력 직무
...
```

비추천한다.

이 방식은 사실상 채용 시스템 내부에 범용 폼 빌더 엔진을 만드는 것이다.  
현재 단계에서는 과하다.

---

## 16. 권장 구현 단계

### 16.1 1차 구현

목표:

```text
ApplicationFormConfig 유지
ApplicationFormPage / ApplicationFormPageItem 추가
sectionType 단위 page layout 설정
관리자 layout 저장/조회 API
지원자 layout 조회 API
백엔드 정합성 검증
```

제외:

```text
field-level form builder
page 단위 저장 API
mock data preview
복잡한 published 이후 변경 이력 관리
```

---

### 16.2 2차 구현

목표:

```text
관리자 화면
- 페이지 추가
- 페이지명 수정
- 섹션 이동
- 섹션 추가/제거
- 미리보기
- 저장 전 validation
```

---

### 16.3 3차 구현

목표:

```text
지원자 작성 화면
- 상단 step navigation
- 현재 page section 렌더링
- 페이지 저장
- 다음 페이지 이동
- 이전 페이지 이동
- 완료 상태 표시
```

---

### 16.4 4차 구현 후보

필요성이 확인되면 추가한다.

```text
- page 단위 저장 API
- mock data preview
- 접수 시작 이후 layout 변경 이력 관리
- layout versioning
- 지원자별 작성 snapshot
```

---

## 17. 최종 판단

지원서 작성 화면을 page layout 기반으로 구성하는 방안은 타당하다.

다만 height 기준으로 자동 배치하지 말고, 업무 의미 단위로 page를 구성해야 한다.

기존 `use/require` 구조를 유지하면서 `ApplicationFormPage`, `ApplicationFormPageItem`을 추가하면 기존 application 데이터 저장 구조를 크게 흔들지 않고 확장할 수 있다.

최종 권장 구조:

```text
기존 ApplicationFormConfig:
- useXxx
- requireXxx

신규 ApplicationFormPage:
- pageNo
- title
- description
- sortOrder

신규 ApplicationFormPageItem:
- sectionType
- sortOrder
```

최종 핵심 규칙:

```text
use=false:
- page layout 추가 불가
- require=true 불가
- 지원자 화면 미노출
- 최종 제출 검증 제외

use=true, require=false:
- page layout 필수 포함
- 지원자 화면 노출
- 입력은 선택

use=true, require=true:
- page layout 필수 포함
- 지원자 화면 노출
- 최종 제출 시 완료 필수
```

이 방향은 기존 application 도메인을 갈아엎는 대형 변경이 아니라, **지원서 양식 설정 계층에 layout 설정을 추가하는 중간 규모 확장 작업**으로 보는 것이 맞다.
