# 모노레포 통합 설계 (2026-08-14)

## 목표

프론트(`recruit_front/`, GitHub `roehfl/recruit`)와 백엔드(`recruit_back/recruit_backend/`, GitHub `roehfl/recruit_backend`)를 하네스 저장소(`recruit/`, 로컬 전용)로 흡수하여 **하나의 private GitHub 모노레포**로 통합 관리한다.

## 결정 사항

- 형태: **모노레포** (서브모듈/개별 저장소 유지 안 함)
- 히스토리: **세 저장소 모두 보존** (git subtree 병합)
- 대상: **새 private 저장소** 생성 (기존 두 저장소는 아카이브로 보존)
- 폴더 배치: 현재 구조 유지 (`recruit_front/`, `recruit_back/recruit_backend/`) — CLAUDE.md·계약 문서가 경로를 참조하므로 변경하지 않는다. 평탄화는 필요 시 후속 작업.

## 작업 순서

1. 정리 커밋: 미추적 설계 HTML을 `docs/design/`에 커밋, `bash.exe.stackdump`·`dist/`는 `.gitignore`에 추가.
2. `.gitignore` 개편: `recruit_back/`, `recruit_front/` 제외 규칙 삭제. 각 하위 저장소의 `.gitignore`가 파일로 들어와 그대로 적용됨.
3. subtree 병합: 폴더를 옆으로 옮긴 뒤 `git subtree add --prefix=<경로> <로컬경로> main`으로 히스토리 흡수 → 트리 해시 비교로 내용 무손실 검증 → node_modules 등 로컬 산출물 복원 → 임시 폴더 제거(원본은 GitHub 원격에 보존).
4. 브랜치 정리: `master` → `main` 개명 (GitHub 기본값과 일치).
5. `CLAUDE.md` §6 git 규칙을 모노레포 체제로 갱신.
6. GitHub 웹에서 새 private 저장소 생성(사용자) → `origin` 등록 → 푸시.
7. (선택) 기존 두 저장소 GitHub에서 Archive.

## 검증

- 병합 후 `git rev-parse HEAD:<prefix>` 와 원본 `HEAD^{tree}` 해시 일치 확인.
- `git log -- <prefix>`로 과거 히스토리 조회 확인.
- 프론트 `npm run type-check` 정상.

## 리스크 및 롤백

기존 GitHub 원격 두 개가 최신 상태로 남아 있어 어느 시점에서든 clone으로 복원 가능. 로컬 중첩 `.git` 제거는 원격 동기화 확인 후에만 수행.
