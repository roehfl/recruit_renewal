# 06. Implementation Roadmap

이 문서는 **현재 코드 기준의 갭 분석**과 **안전한 단계별 구현 순서**를 정리한 문서다.

- 기준 문서: `docs/codex/02-domain-design.md`, `docs/codex/03-legacy-feature-map.md`
- 범위: 현재 `src/main/java`에 존재하는 코드 분석 + 미구현 범위 식별
- 원칙: 한 번에 전체 구현하지 않고, 작은 vertical slice 단위로 진행

---

## 1) 현재 코드 구조 점검

## 1.1 패키지 구조

현재 `src/main/java/com/shinyoung/recruit` 기준 주요 패키지는 다음과 같다.

- `common`
  - `crypto`: `AesCryptoUtil`, `AesAttributeConverter`, `CryptoHolder`
  - `hash`: `HashUtil`
  - `util`: `HtmlTextUtils`
- `config`
  - `AuthenticationConfig`, `SecurityConfig`, `CryptoConfig`, `JasyptConfig`, `JpaConfig`, `SwaggerConfig`
- `controller`
  - `AuthController`, `MenuController`, `BoardController`
- `domain.entity`
  - `BaseEntity`, `User`, `Applicant`, `Employee`, `DeptRoleMapping`, `Menu`, `Notice`
- `domain.repository`
  - `UserRepository`, `ApplicantRepository`, `EmployeeRepository`, `DeptRoleMappingRepository`, `MenuRepository`, `NoticeRepository`, `NoticeSpecification`
- `dto.request`
  - `LoginRequest`, `MenuSaveRequest`, `NoticeSaveRequest`
- `dto.response`
  - `ApiResponse`, `PageResponse`, `LoginUserResponse`, `MenuResponse`, `MenuIdResponse`, `NoticeListResponse`, `NoticeDetailResponse`
- `enumeration`
  - `MenuSite`, `MenuType`, `NoticeSearchType`
- `exception`
  - `UsernameNotFoundException`, `NoticeNotFoundException`
- `security.auth`
  - `CustomUserDetails`, `CustomUserDetailsService`, `CustomLdapUserDetailsMapper`, `RoutingAuthenticationProvider`

## 1.2 현재 구현 기능(코드 관점)

### 인증/인가
- Session 기반 로그인/로그아웃/현재 사용자 조회
  - `POST /auth/login`
  - `POST /auth/logout`
  - `GET /auth/me`
- 지원자(DB) / 임직원(LDAP) 라우팅 인증 Provider 존재
- LDAP 사용자 매핑 + 부서 권한 매핑 구조 존재

### 메뉴
- 메뉴 트리, 상세, breadcrumb 조회
- 관리자 메뉴 생성/수정 API 존재

### 공지사항
- 공지사항 목록(검색/페이징), 상세, 등록 API 존재

### 공통/인프라
- `ApiResponse<T>`, `PageResponse<T>`
- JPA Auditing 공통 필드(`BaseEntity`)
- AES/Jasypt/H2 기반 기본 설정

---

## 2) 설계 기준 대비 갭 분석

`02-domain-design.md`, `03-legacy-feature-map.md` 기준으로 아직 구현되지 않은 핵심 범위는 아래와 같다.

## 2.1 이미 구현된 범위

- `User` / `Applicant` / `Employee` 기반 인증 골격
- `DeptRoleMapping` 권한 매핑 골격
- `Menu`, `Notice` 도메인 + API
- 기본 보안/암호화/응답 래핑

## 2.2 미구현 도메인(핵심)

- 채용공고 루트
  - `JobPosting`, `JobPosition`, `ApplicationFormConfig`
- 전형 루트
  - `Stage`, `StageResult`
- 지원서 루트
  - `Application` (임시저장/최종제출 포함)
- 지원서 상세 하위 도메인
  - 학력, 경력, 자격, 어학, 병역, 포상, 공백기간
- 질문/평가 루트
  - `QuestionTemplate`, `QuestionSet`, `Answer`
- 면접 루트
  - `Interview`, `InterviewParticipant`, `InterviewEvaluation`
- 메시지 루트
  - `MessageBatch`, `MessageSendLog`
- 공통 코드/감사
  - `CommonCode`, `ActivityLog`

## 2.3 미구현 기능(레거시 맵 기준)

- 관리자 채용공고 CRUD/게시/마감
- 지원서 항목 설정(노출/필수 여부) + 단계별 검증
- 지원서 작성 현황, 검색, 상세, 임시 생성/삭제
- 전형결과 조회/저장/엑셀 업로드/다운로드
- 면접 스케줄링/면접관 배정/평가
- SMS/Email/알림톡 발송 및 이력 조회
- 통계/엑셀/PDF/개인정보 파기 관리

---

## 3) 구현/미구현 기능 분리 요약

## 3.1 구현 완료(현재 코드)

1. 인증 기본 플로우(세션 기반, DB/LDAP 라우팅)
2. 메뉴 관리 API(조회/생성/수정)
3. 공지사항 API(목록/상세/등록)
4. 공통 보안/암호화/응답 포맷 골격

## 3.2 미구현(우선순위 대상)

1. 채용공고/모집분야/지원서 항목 설정
2. 전형단계/전형결과 관리
3. 지원서 생성~제출~조회 전체 흐름
4. 면접 스케줄링/평가
5. 메시지 발송/이력/통계/파기

---

## 4) Phase 기반 구현 순서(안전한 점진 개발)

아래 Phase는 기존 코드 스타일을 유지하면서 **기능 축 단위 vertical slice**로 설계했다.

## Phase 1: JobPosting Vertical Slice

- 목적
  - 채용공고 기본 CRUD + 게시/마감 상태 전이를 구현해 관리자 운영의 시작점을 확보한다.
- 구현 대상 도메인
  - `JobPosting`, `JobPosition`, `ApplicationFormConfig`(최소 필드)
- 추가/수정될 주요 파일
  - `domain.entity`: `JobPosting`, `JobPosition`, `ApplicationFormConfig`
  - `domain.repository`: 각 Repository
  - `dto.request/response`: create/update/list/detail
  - `service`: `JobPostingService`
  - `controller`: `JobPostingAdminController`
  - `exception`: not found/invalid status
- API 후보
  - `GET /admin/job-postings`
  - `GET /admin/job-postings/{id}`
  - `POST /admin/job-postings`
  - `POST /admin/job-postings/{id}`
- 테스트 대상
  - Repository(`@DataJpaTest`): 상태/기간 조회
  - Service: 상태 전이 검증
  - Controller(가능 시): 유효성/응답 포맷
- 주의사항
  - HTML 소개문 저장 시 sanitize/검색용 text 분리 검토
  - 기간(start/end) 검증을 Service에서 명확히 처리

## Phase 2: Stage + StageResult

- 목적
  - 전형 단계 및 결과 저장의 공통 모델을 확립해 이후 기능(지원서/면접/통계)의 기반을 만든다.
- 구현 대상 도메인
  - `Stage`, `StageResult`
- 추가/수정될 주요 파일
  - `domain.entity/repository`, `StageService`, `StageAdminController`, DTO
- API 후보
  - `GET /admin/stages/{stageId}/results`
  - `POST /admin/stages/{stageId}/results`
- 테스트 대상
  - 단계별 결과 저장/조회/예외 케이스
- 주의사항
  - stage type(enum/code) 기준을 초기에 고정
  - 변경 이력 필요 시 `ActivityLog` 연동 지점 확보

## Phase 3: Application 기본 흐름

- 목적
  - 지원서 생성/조회/임시저장/최종제출까지 사용자 핵심 흐름을 완성한다.
- 구현 대상 도메인
  - `Application` + 최소 하위 섹션
- 추가/수정될 주요 파일
  - `Application` entity/repository/service/controller/DTO
  - 인증 사용자 연계 로직
- API 후보
  - `POST /applications`
  - `GET /applications/{id}`
  - `POST /applications/{id}/draft`
  - `POST /applications/{id}/submit`
- 테스트 대상
  - 임시저장 상태 전이, 제출 시 필수항목 검증
- 주의사항
  - `ApplicationFormConfig` 기반 필수값 검증 규칙 일관성
  - 개인정보 필드 암호화/검색 hash 분리

## Phase 4: Application Detail Domains

- 목적
  - 학력/경력/자격/어학/병역/포상/공백기간 세부 도메인을 확장한다.
- 구현 대상 도메인
  - Education/Career/Certificate/Language/Military/Award/Gap
- 추가/수정될 주요 파일
  - 도메인별 entity/repository/dto/service
- API 후보
  - `/applications/{id}/educations` 등 섹션별 CRUD
- 테스트 대상
  - 섹션별 유효성 + 정렬/중복/기간 검증
- 주의사항
  - 공고/지원분야별 required flag와의 정합성

## Phase 5: Interview + Evaluation

- 목적
  - 면접 일정/참가자 배정/평가 저장까지 구현한다.
- 구현 대상 도메인
  - `Interview`, `InterviewParticipant`, `InterviewEvaluation`
- 추가/수정될 주요 파일
  - 인터뷰 도메인 계층 전반 + 관리자 API
- API 후보
  - `GET /admin/interviews`
  - `POST /admin/interviews`
  - `POST /admin/interviews/{id}/participants`
  - `POST /admin/interviews/{id}/evaluations`
- 테스트 대상
  - 배정 충돌, 일정 중복, 평가 권한 검증
- 주의사항
  - 면접관 권한/가시성 제한 규칙을 초기 명세화

## Phase 6: Messaging + Audit + Ops

- 목적
  - 발송/이력/감사로그/통계/엑셀/PDF/파기 운영 기능을 단계적으로 추가한다.
- 구현 대상 도메인
  - `MessageBatch`, `MessageSendLog`, `ActivityLog`, (필요 시) `CommonCode`
- 추가/수정될 주요 파일
  - 메시지/감사/리포트 계층 + 배치/비동기 구성
- API 후보
  - `POST /admin/messages`
  - `GET /admin/messages/logs`
  - `GET /admin/statistics/...`
- 테스트 대상
  - 발송 요청 검증, 결과 이력 저장, 재시도 정책
- 주의사항
  - 개인정보 마스킹/암호화/보관주기/파기 정책을 우선 반영

---

## 5) 기존 프로젝트 스타일 유지 원칙

1. 패키지 구조 유지
   - `domain.entity`, `domain.repository`, `service`, `controller`, `dto.request/response` 유지
2. 응답 규격 유지
   - `ResponseEntity<ApiResponse<T>>`, 목록은 `ResponseEntity<ApiResponse<PageResponse<T>>>`
3. Entity 원칙
   - `BaseEntity` 상속, `Long id + IDENTITY`, enum은 `STRING`
4. 트랜잭션 원칙
   - Service read는 `readOnly`, 변경은 `@Transactional`
5. 검증 위치
   - Request DTO Bean Validation + Service 비즈니스 검증
6. 인증/보안 방향 유지
   - Session 기반 유지, LDAP 값 하드코딩 금지, 실LDAP 의존 테스트 금지
7. 개인정보 처리
   - 평문 검색 지양, hash 검색 필드 분리, 민감값 암호화
8. 구현 방식
   - 대규모 리팩터링 금지, phase 단위 작은 PR

---

## 6) 1차 착수 권장안

### 권장 1순위: Phase 1 (JobPosting Vertical Slice)

선정 이유:
- 현재 코드와 충돌이 적고 독립적으로 확장 가능
- 이후 `Application`, `Stage`의 상위 기준 데이터가 됨
- API/DB/검증 패턴을 팀 표준으로 먼저 확정하기 좋음

초기 범위(최소):
- 공고 생성/조회/수정 + 게시/마감 상태 전이
- 공고당 모집분야 최소 1개
- 지원서 항목 설정 최소 플래그 세트

---

## 7) 구현 전 설계 쟁점(확인 필요)

1. `CommonCode` 도입 시점
   - Phase 1부터 코드테이블 도입할지, enum으로 시작 후 이관할지
2. Stage 모델링 방식
   - 공고별 고정 단계 vs 공고별 커스텀 단계
3. 지원서 필수 검증 책임
   - `ApplicationFormConfig` + 섹션별 validator 경계
4. 메시지 발송 아키텍처
   - 동기 API + 비동기 worker 분리 시점
5. 개인정보 파기/보관주기
   - 도메인 저장 단계에서 retention metadata를 함께 둘지
6. LDAP/권한 매핑 확장
   - 부서명 기반 단순 매핑 외 예외 규칙 필요 여부
