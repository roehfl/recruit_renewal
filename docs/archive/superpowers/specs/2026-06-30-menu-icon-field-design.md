# 메뉴 아이콘 필드 추가 — 설계서

- 날짜: 2026-06-30
- 슬라이스: 메뉴(Menu) — 백엔드 `icon` 필드 지원
- 상태: 구현 완료 (🟢)

## 목적

관리자 좌측 사이드바 메뉴에 아이콘을 표시하기 위해, 메뉴 엔티티에 ant-design-vue
아이콘 컴포넌트명을 문자열로 저장·조회할 수 있게 한다. 지원자(가로 헤더) 메뉴는
아이콘이 필요 없어 nullable로 둔다.

## 범위

- 메뉴 엔티티/DTO/API에 `icon` 문자열 필드 추가.
- 기존 메뉴 엔드포인트(GET tree/단건/breadcrumb, POST 생성/수정)에 필드만 추가. 신규 엔드포인트 없음.
- 프론트 타입(`MenuItem`) 동기화(렌더링 변경 없음).

## 범위 밖 (후속 슬라이스)

- 메뉴 관리 CRUD 화면 + 아이콘 피커(아이콘 후보 목록은 **프론트 하드코딩**).
- 관리자 좌측 사이드바에서 아이콘 실제 렌더링.
- 메뉴 삭제 기능(요청 시 DELETE 대신 POST 사용 — 사용자 규약).
- 관리자 영역 셸(AdminLayout/사이드바/admin 라우트)은 미구현 상태이며 별도 작업.

## 결정 사항

- **저장 형식**: 컴포넌트명 그대로(`"SettingOutlined"`). 추상 키 매핑 없음 → API만으로 완결.
- **아이콘 후보 목록 출처**: 프론트 하드코딩(백엔드 카탈로그 테이블/API 없음).
- **검증**: 백엔드는 유효 아이콘명 검증 안 함(자유 문자열). 잘못된 값 방지는 관리 화면 피커가 담당.
- **컬럼**: `icon VARCHAR(100)`, nullable. 대메뉴/소메뉴 모두 허용.

## 변경 파일

백엔드(`recruit_back/recruit_backend/`):
- `domain/entity/Menu.java` — `icon` 필드 + create/update 시그니처에 추가.
- `dto/request/MenuSaveRequest.java` — `icon`(선택) 추가.
- `dto/response/MenuResponse.java` — `icon` 추가, `from()` 매핑.
- `service/MenuService.java` — create/update에서 `request.icon()` 전달.
- `service/MenuServiceTest.java`(신규) — icon 왕복(생성/수정/단건/트리) 검증.

프론트(`recruit_front/`):
- `src/types/menu.ts` — `MenuItem.icon: string | null` 추가.

계약:
- `api-contract.md` — 메뉴 섹션 신규 추가(🟢).

## 검증

- 백엔드: `./gradlew.bat test --tests "com.shinyoung.recruit.service.MenuServiceTest"` → BUILD SUCCESSFUL.
- 프론트: `npm run type-check` → 통과.
