# CLAUDE.md — 통합 하네스 (채용 front + back)

이 폴더(`recruit/`)는 신영증권 채용 Renewal의 **프론트엔드 + 백엔드를 함께 다루는 작업 루트**다.
이 문서는 얇은 **오케스트레이터**다. 레포별 상세 규칙은 각 저장소의 문서에 위임한다.

## 1. 구성

- 백엔드: `recruit_back/recruit_backend/` — Spring Boot. 권위 문서: `recruit_back/recruit_backend/CLAUDE.md` + `docs/codex/`.
- 프론트: `recruit_front/` — Vue 3 + Vite + TS. 권위 문서: `recruit_front/AGENTS.md`.
- 세 영역 모두 이 폴더(`recruit/`)를 루트로 하는 **단일 git 모노레포**로 관리한다(§6).
- 계약 문서: `recruit/api-contract.md` — front-back API 동기화의 단일 기준.

## 2. 읽기 순서

작업 유형에 따라 먼저 읽는다.

- 모든 작업: 이 문서(`recruit/CLAUDE.md`) → `recruit/api-contract.md`의 대상 화면 섹션.
- 백엔드를 건드리면: `recruit_back/recruit_backend/CLAUDE.md`(및 거기서 지시하는 `docs/codex/01~04`).
- 프론트를 건드리면: `recruit_front/AGENTS.md`.

Claude Code는 부모 디렉토리의 `CLAUDE.md`를 자동 로드하므로, 백엔드 하위에서 작업하면 이 문서와 백엔드 `CLAUDE.md`가 함께 적용된다. 프론트 규칙(`AGENTS.md`)은 자동 로드되지 않으므로 **프론트 작업 시 반드시 `recruit_front/AGENTS.md`를 읽는다.**

## 3. 화면 슬라이스 워크플로우 (핵심)

작업 단위는 **하나의 화면 슬라이스**다. 사용자가 지정한 화면/API만 다루고, 다른 화면으로 범위를 넓히지 않는다.

0. 범위 고정: 대상 화면·연관 엔드포인트 식별. `api-contract.md`에서 기존 계약 확인.
1. 계약 정렬(먼저): 변경될 엔드포인트의 요청/응답 스키마를 `api-contract.md`에 🟡 초안으로 기재.
2. 백엔드 구현: 해당 패키지에 controller/service/DTO 등 추가·수정(백엔드 `CLAUDE.md` 규칙 준수).
3. 백엔드 검증: 수정한 패키지 테스트만 실행(§5).
4. 프론트 구현: `src/api` 모듈을 계약에 맞추고 대상 화면·store·route 구현(`AGENTS.md` 규칙 준수).
5. 프론트 검증: `npm run type-check`(+필요시 `build`).
6. 계약 확정: 실제 구현과 `api-contract.md`를 일치시켜 🟢로 갱신.
7. 보고: 양쪽 변경 파일 / 테스트 결과 / 계약 변경분 / 남은 이슈 요약.

방향: 기본 **백엔드 → 프론트**(프론트가 응답 스키마에 의존). 계약 문서가 기준이므로 순서가 바뀌어도 동기화는 유지된다.
한쪽만 변경: API 무변경 프론트 작업은 2·3 생략하되 "계약 영향 없음"을 확인한다. 백엔드 단독도 대칭으로 처리한다.

## 4. API 계약 문서 동기화 규약

- `recruit/api-contract.md`가 front-back 계약의 **단일 기준**이다.
- 슬라이스 시작 시 대상 화면 섹션을 읽고, 없으면 추가한다.
- 구현 전 🟡 초안 → 구현 후 🟢 확정(코드와 일치 검증).
- 요청/응답은 **필드 모양 요약** 수준만 적는다. 정확한 타입·검증은 백엔드 DTO가 단일 출처다.
- 계약을 임의로 발명하지 않는다. 불명확하면 🔴로 표시하고 사용자에게 확인한다.

## 5. 검증 정책

전체 리그레션/전체 빌드는 **명시 요청 시에만**. 평소엔 변경 범위만 검증한다.

백엔드(`recruit_back/recruit_backend/`에서, 수정 패키지만):

```powershell
$env:AES_SECRET_KEY='<백엔드 CLAUDE.md의 로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.<수정패키지>.*" --no-daemon
```

프론트(`recruit_front/`에서):

```bash
npm run type-check     # 기본 (vue-tsc)
npm run build          # 필요시 (type-check 포함)
```

실제 운영 AES 키·LDAP·DB 접속정보는 절대 사용/기재하지 않는다(예시 값만). 프론트 단위 테스트(`npm run test:unit`)는 필요 시에만.

## 6. git 안전 규칙

- `recruit/`는 하네스 문서 + `recruit_front/` + `recruit_back/recruit_backend/`를 모두 추적하는 **단일 모노레포**다(2026-08-14 통합, 히스토리 subtree 병합). 원격은 GitHub private 저장소 `origin` 하나다.
- 과거의 `roehfl/recruit`(프론트)·`roehfl/recruit_backend`(백엔드) 저장소는 **아카이브**다. 그쪽으로 push하지 않는다.
- 프론트/백엔드 변경도 이 모노레포에서 커밋한다. 커밋 메시지 규칙(`feat(job-posting): ...` 등)은 기존 각 레포 관례를 유지한다.
- 명확한 요청 없이 `git commit`/`git push`/브랜치 조작을 하지 않는다.
- 운영 비밀값(AES 키·LDAP·DB 접속정보)은 절대 커밋하지 않는다(예시 값만).
- 홈 디렉토리(`C:/Users/roehf`)에는 별개의 `fit.git`이 있다. **홈에서 직접 git 명령을 실행하지 않는다.**

## 7. 기존 하네스와의 관계 (override/명확화)

- 백엔드 `CLAUDE.md`의 "이 저장소는 백엔드만, Vue/정적 리소스 생성 금지"는 유효하다. 의미는 **"백엔드 저장소 안에 프론트 코드를 만들지 않는다"**이며, 프론트 변경은 `recruit_front/`에서 수행한다.
- 백엔드의 Markdown+HTML 이중 문서화는 **백엔드 단독 Phase 작업**에만 적용한다. 통합 화면 슬라이스는 `api-contract.md` 갱신 + 보고 요약으로 충분하다.
- 충돌 시: 통합/조정 규칙은 이 문서가, 레포 내부 구현 규칙은 각 레포 문서가 우선한다.

## 8. 위임

레포별 상세(패키지 구조, 스타일, 보안 금지사항, 도메인 설계, 프론트 라이브러리 규칙 등)는 각 저장소 문서를 단일 출처로 따른다.

- 백엔드: `recruit_back/recruit_backend/CLAUDE.md`
- 프론트: `recruit_front/AGENTS.md`
