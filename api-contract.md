# API 계약 문서 (recruit)

front-back 동기화의 **단일 기준**. 화면 슬라이스 작업 시 구현 전 🟡 초안으로 기재하고, 구현 후 🟢로 확정한다.

## 상태 표기

- 🟢 구현됨 (front-back 양쪽 구현·검증 완료)
- 🟡 초안 (구현 중)
- 🔴 변경필요 / 불명확 (사용자 확인 필요)

## 운영 규칙

- 화면 단위로 섹션을 만들고, 각 화면 아래 엔드포인트를 나열한다.
- 요청/응답은 **필드 모양 요약** 수준만 적는다. 정확한 타입·검증 규칙은 백엔드 DTO가 단일 출처다.
- 계약을 임의로 발명하지 않는다. 불명확하면 🔴로 표시하고 사용자에게 확인한다.
- 한 엔드포인트가 여러 화면에서 쓰이면 한 곳에 정의하고 다른 화면에서 참조한다.
- 변경 이력은 이 파일의 git 로그로 대체한다(별도 changelog 불필요).

---

## 템플릿 (복사해서 사용 — 아래는 형식 예시이며 실제 계약 아님)

### 화면: <화면명> (<ViewComponent>)

- 프론트: `src/views/.../<View>.vue`, `src/api/<module>.ts`
- 백엔드: `com.shinyoung.recruit.controller.<Controller>`

#### <METHOD> <경로>  🟡

- 설명: <한 줄>
- 요청: `{ ... }`
- 응답(200): `ApiResponse<{ ... }>`
- 오류: <코드/사유>
- 매핑: front `<api 함수>()` ↔ back `<Controller>.<method>()`

> 형식 참고용 예시(실제 검증 전 계약 아님):
> 화면: 로그인 (LoginView) — 프론트 `src/views/auth/LoginView.vue`, `src/api/authApi.ts` / 백엔드 `AuthController`
> `POST /auth/login` → 요청 `{ username, password }`, 응답 `ApiResponse<{ ... }>`, 매핑 front `authApi.login()` ↔ back `AuthController.login()`

---

## 화면 계약

> 실제 화면 계약은 슬라이스 작업 시 위 템플릿을 복사해 이 아래에 추가한다.
