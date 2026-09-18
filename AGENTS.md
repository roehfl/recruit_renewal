# AGENTS.md — 신영증권 채용 Renewal

모든 작업의 진입점이다. 읽는 순서: 이 문서 → `docs/domains/_index.md`(대상 카드 찾기) → 도메인 카드 → 수정할 레포의 AGENTS.md.
응답·보고·문서는 한국어로 쓴다. 클래스명·필드명·enum·API 경로·명령은 원문 그대로 쓴다.

## 1. 구성

| 영역 | 경로 | 내용 |
|---|---|---|
| 백엔드 | `recruit_back/recruit_backend/` | Spring Boot 4 · Java 17 · JPA · Spring Security(세션·LDAP) · Gradle. 규칙: `recruit_back/recruit_backend/AGENTS.md` |
| 프론트 | `recruit_front/` | Vue 3 · Vite · TypeScript · Pinia · ant-design-vue · Axios. 규칙: `recruit_front/AGENTS.md` |
| 도메인 카드 | `docs/domains/` | 도메인별 API 계약·파일 지도·규칙·변경 레시피. 색인: `docs/domains/_index.md` |
| ADR | `recruit_back/recruit_backend/docs/adr/` | 아키텍처 결정 기록 |
| 수동 DDL | `recruit_back/recruit_backend/docs/ops/` | 운영 DB에 수동 반영하는 SQL |
| 과거 이력 | `docs/archive/` | 옛 설계·구현 이력·보고서. **작업 근거로 쓰지 않는다** |
| 문서 점검 | `tools/check-docs.mjs` | 카드 경로·소유·크기 검사 |

- 백엔드 API에는 모두 `/api` 접두가 붙는다. 카드의 API 경로는 `/api`를 뺀 형태다.
- 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}`의 정의는 `docs/domains/_index.md`에 있다.

## 2. 작업 원칙

- 가정을 먼저 밝힌다. 모호하면 추측하지 말고 묻는다.
- 요청을 해결하는 최소 코드만 쓴다. 추측성 기능·추상화·설정·의존성을 넣지 않는다.
- 요청 범위의 파일·줄만 고친다. 인접 코드 리팩터링과 무관한 포맷 변경을 하지 않는다. 기존 스타일을 따른다.
- 무관한 결함·죽은 코드를 발견하면 고치지 말고 보고한다.
- 성공 기준을 정하고, 가능하면 테스트로 재현한 뒤 고친다. 무엇을 검증했고 무엇을 못 했는지 보고한다.
- 코드와 문서가 다르면 코드가 기준이다. 문서를 고치고 보고한다.

## 3. 화면 슬라이스 워크플로우

작업 단위는 하나의 화면(기능) 슬라이스다. 지정된 화면·API만 다루고 범위를 넓히지 않는다.

0. 범위 고정: `_index.md`로 카드를 찾고 카드의 API 계약·규칙을 읽는다.
1. 계약 정렬: 바뀔 엔드포인트를 카드 `## API 계약` 표에 🟡로 먼저 적는다.
2. 백엔드 구현: 백엔드 AGENTS.md 규칙을 따른다.
3. 백엔드 검증: 수정한 클래스·패키지 테스트만 실행한다(5절).
4. 프론트 구현: `src/api` 모듈 → 타입 → 화면·store·route 순서. 프론트 AGENTS.md 규칙을 따른다.
5. 프론트 검증: `npm run type-check`.
6. 계약 확정: 표를 구현과 일치시키고 🟢로 바꾼다.
7. 카드 갱신과 문서 점검(6절).
8. 보고: 변경 파일, 테스트 결과, 계약 변경, 남은 이슈.

기본 방향은 백엔드 → 프론트다. API를 바꾸지 않는 한쪽 작업은 해당 단계를 생략하되 "계약 영향 없음"을 확인해 보고한다.

## 4. API 계약 규약

- 계약의 단일 기준은 각 카드의 `## API 계약` 절이다. 옛 `api-contract.md`는 `docs/archive/`로 옮겨졌다.
- 상태: 🟢 확정(front-back 구현·검증 완료) / 🟡 초안(구현 중) / 🔴 불명확(사용자 확인 필요) / ⛔ 폐지.
- 요청·응답은 필드 모양 요약만 적는다. 정확한 타입·검증은 백엔드 DTO가 단일 출처다.
- 계약을 발명하지 않는다. 불명확하면 🔴로 적고 사용자에게 확인한다.
- 여러 화면이 쓰는 엔드포인트는 컨트롤러를 소유한 카드 한 곳에만 정의하고, 다른 카드는 링크한다.
- 변경 이력은 git log로 대신한다.

## 5. 검증

전체 리그레션·전체 빌드는 명시적으로 요청받았을 때만 한다. 평소에는 변경 범위만 검증한다.

백엔드(`recruit_back/recruit_backend/`에서). AES 키는 백엔드 AGENTS.md의 로컬 예시 값만 쓴다.

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.StageResult*" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.service.StageResult*" --no-daemon
```

프론트(`recruit_front/`에서): `npm run type-check`(기본), `npm run build`(필요 시), `npm run test:unit`(필요 시).

문서(레포 루트에서): `node tools/check-docs.mjs` — 오류 0건이어야 한다.

## 6. 문서 갱신 의무 (완료 조건)

코드를 바꾼 변경에 카드 갱신이 빠지면 미완료다.

- 파일 추가·삭제·이름 변경 → 카드 `## 파일 지도`.
- 엔드포인트·요청·응답 변경 → 카드 `## API 계약`.
- 비즈니스 규칙·상태 전이·권한 변경 → 카드 `## 규칙·불변식`.
- 새 `*Controller.java`나 `views/**/*.vue`는 정확히 한 카드의 `## 파일 지도`에 등록한다. 카드가 없는 정적 화면은 `_index.md`의 "카드 없는 파일"에 적는다. 점검 스크립트가 누락·중복을 잡는다.
- `## 파일 지도`에는 그 카드가 소유한 파일만 적는다. 다른 카드의 파일은 다른 절에서 언급하고 그 카드로 링크한다.
- 새 도메인이면 `_index.md`의 카드 템플릿으로 카드를 만들고 색인에 추가한다.
- 카드는 30KB 권장, 40KB 상한이다. 넘으면 하위 도메인으로 나누고 색인을 고친다.
- 완료 전에 `node tools/check-docs.mjs`를 통과시킨다.

## 7. git 규칙

- `recruit/`는 백엔드·프론트·문서를 함께 추적하는 단일 모노레포다.
- 명확한 요청 없이 `git commit`·`git push`·브랜치 조작을 하지 않는다.
- 커밋 메시지는 `feat(<카드명>): ...`, `fix(<카드명>): ...` 형식이다. 스코프는 도메인 카드 이름을 쓴다.
- 파일 이동은 `git mv`로 한다.

## 8. 금지 사항

- 운영 AES 키·LDAP·DB 접속정보를 사용하거나 문서·코드·커밋에 쓰지 않는다. 예시 값만 쓴다.
- 백엔드 디렉터리 안에 프론트 코드·정적 리소스를 만들지 않는다.
- `docs/archive/` 문서를 현행 규칙의 근거로 쓰지 않는다. 과거 경위를 조사할 때만 참고한다.
- 요청 없이 대규모 리팩터링, 패키지 구조 변경, 새 의존성 추가를 하지 않는다.
