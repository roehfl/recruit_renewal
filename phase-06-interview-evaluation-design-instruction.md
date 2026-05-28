# Phase 06 - Interview Evaluation 설계 지시문

아래 내용을 그대로 Claude에 붙여넣어 사용하면 된다.

```markdown
/grill-with-docs

# Phase 06 - Interview Evaluation 설계를 진행하라

## 목적

채용 시스템의 다음 단계로 `Phase 06 - Interview Evaluation` 설계를 진행한다.

이번 Phase는 **문서 설계 전용 작업**이다.  
소스코드 구현, 테스트 코드 작성, API 구현, DB 마이그레이션 작성은 하지 않는다.

Phase 06의 핵심 목표는 면접 일정/참가자 모델 위에 면접관 평가 기능을 추가하기 위한 설계다.

반드시 다음 경계를 지켜라.

> InterviewEvaluation is evaluation evidence, not the final stage result.
> StageResult remains the final decision record and must not be mutated automatically by interviewer submission.

즉, 면접관 평가 제출은 평가 원자료 저장/제출일 뿐이며, `StageResult`의 PASS/FAIL/PENDING을 자동 변경하면 안 된다.  
`StageResult` 반영이 필요하다면 반드시 관리자 명시적 command 또는 별도 후속 slice로 분리 설계한다.

---

## 반드시 참고할 문서

다음 문서를 먼저 읽고 현재 구현 상태와 기존 정책을 확인하라.

- `docs/codex/07-implementation-history.md`
- `docs/codex/06-implementation-roadmap.md`
- `docs/codex/reports/current-implementation-status.html`
- `docs/codex/design/phase-04-interview-scheduling-design.md`
- `docs/codex/implementation/phase-04a-interview-scheduling-domain.md`
- `docs/codex/implementation/phase-04b-admin-interview-schedule-management.md`
- `docs/codex/implementation/phase-04c-applicant-interview-read.md`
- `docs/codex/implementation/phase-04d-interviewer-interview-read.md`
- `docs/codex/implementation/phase-04e-interview-scheduling-stabilization.md`
- StageResult 관련 기존 설계/구현 문서가 있으면 함께 확인하라.
  - 예: `phase-03d-*stage-result*`
  - 예: `phase-03c-*admin-application*`
  - 정확한 파일명은 repository에서 검색해서 확인하라.

---

## 현재 전제

기존 Phase 04에서 다음 기반이 이미 있다.

- `Interview` schedule/group entity
- `InterviewParticipant` candidate/interviewer assignment entity
- `InterviewStatus`: DRAFT / CONFIRMED / CANCELLED
- `InterviewParticipantRole`: CANDIDATE / INTERVIEWER
- `InterviewParticipantStatus`: ASSIGNED / CANCELLED
- 관리자 면접 일정 생성/수정/참가자 교체/확정/취소
- 지원자 면접 일정 조회
- 면접관 면접 일정 조회
- 면접 일정 cancel/read flow에서 `StageResult`를 변경하지 않는 정책

Phase 06은 이 위에 `InterviewEvaluation`을 설계한다.

---

## 설계 대상

다음 항목을 반드시 설계하라.

### 1. 도메인 모델

`InterviewEvaluation` 엔티티 후보를 설계하라.

필수로 검토할 필드:

- id
- interview
- candidateParticipant
- interviewerParticipant 또는 interviewer Employee
- jobApplication
- stage
- score
- recommendation
- comment
- status
  - DRAFT
  - SUBMITTED
- submittedAt
- createdAt / updatedAt
- optional admin memo 여부
- optional correction/reopen fields 여부

중요한 설계 판단:

- 평가 row의 기준은 `Interview × candidate × interviewer`로 한다.
- 후보자 참가자 row는 `InterviewParticipant.role = CANDIDATE`여야 한다.
- 면접관 참가자 row는 `InterviewParticipant.role = INTERVIEWER`여야 한다.
- candidate와 interviewer는 같은 `Interview`에 속해야 한다.
- 같은 면접관이 같은 후보자를 같은 면접에서 중복 평가할 수 없어야 한다.
- unique key 후보를 명확히 제시하라.

예상 unique 후보:

```text
unique(interview_id, candidate_participant_id, interviewer_participant_id)
```

또는 interviewer를 employee로 직접 들고 간다면:

```text
unique(interview_id, candidate_participant_id, interviewer_employee_id)
```

둘 중 어떤 설계가 현재 모델과 더 맞는지 판단하고 이유를 적어라.

---

### 2. 평가 생성 정책

다음 두 방식 중 어느 것이 맞는지 검토하고 결론을 내라.

#### 후보 A: 관리자 initialize command

```http
POST /admin/interviews/{interviewId}/evaluations/initialize
```

- CONFIRMED 면접의 CANDIDATE × INTERVIEWER 조합으로 평가 row를 명시적으로 생성한다.
- read API에서 평가 row를 만들지 않는다.
- 재호출 시 idempotent하게 동작하거나 이미 생성된 row는 유지한다.

#### 후보 B: 면접관 조회 시 lazy 생성

- 면접관이 평가 화면을 열 때 row를 생성한다.

기본 방향은 후보 A를 우선 검토하라.  
read side effect를 만들지 않는 설계를 우선한다.

---

### 3. 면접관 평가 API

면접관 본인이 배정된 면접/후보자만 평가할 수 있도록 설계하라.

후보 API:

```http
GET  /interviewer/interviews/{interviewId}/evaluations
GET  /interviewer/interviews/{interviewId}/evaluations/{evaluationId}
POST /interviewer/interviews/{interviewId}/evaluations/{evaluationId}/save
POST /interviewer/interviews/{interviewId}/evaluations/{evaluationId}/submit
```

또는 프로젝트의 기존 command API 스타일에 더 맞는 URI가 있으면 대안을 제시하라.

반드시 설계할 정책:

- 현재 로그인한 employee 기준으로만 접근 가능
- request path/query/body에서 employeeId를 받지 않는다
- 해당 employee가 `INTERVIEWER` participant로 배정되어 있어야 한다
- `DRAFT` 면접은 평가 불가
- `CANCELLED` 면접은 평가 신규 저장/제출 불가
- `CONFIRMED` 면접만 평가 가능
- candidate participant가 `ASSIGNED`여야 한다
- interviewer participant가 `ASSIGNED`여야 한다
- `SUBMITTED` 평가 수정 금지
- reopen이 필요하면 관리자 명시 command로 후속 설계하거나 out-of-scope로 둔다

---

### 4. 관리자 평가 조회 API

관리자는 면접별/전형별/지원자별 평가 현황을 볼 수 있어야 한다.

후보 API:

```http
GET /admin/interviews/{interviewId}/evaluations
GET /admin/stages/{stageId}/interview-evaluations
GET /admin/applications/{applicationId}/interview-evaluations
```

반드시 설계할 응답 정보:

- 면접 정보
- 후보자 정보
- 면접관별 평가 상태
- 점수
- 추천 여부
- 의견
- 제출 여부
- 제출 시각
- 후보자별 평가 집계 summary
  - submittedCount
  - totalEvaluatorCount
  - averageScore
  - scoreSum 또는 scoreAverage 중 어느 것을 기본으로 할지
  - recommendation distribution

단, Excel/PDF/statistics는 Phase 07 후보로 분리되어 있으므로 Phase 06에서는 복잡한 리포트/다운로드 기능을 넣지 않는다.

---

### 5. StageResult 반영 정책

가장 중요하다.

면접관 평가 제출 시 `StageResult`를 자동 변경하지 않는 정책을 기본값으로 설계하라.

설계 문서에 다음을 명시하라.

- `InterviewEvaluation`은 평가 원자료다.
- `StageResult`는 최종 전형 결과다.
- 면접관 `submit`은 `StageResult`를 생성/수정/발표/정정하지 않는다.
- 관리자 조회 화면에서 평가 summary를 보고 기존 StageResult update/bulk update API를 통해 결과를 확정하는 흐름을 기본으로 한다.
- 별도 반영 command를 만들 경우 Phase 06 후반 또는 후속 Phase로 분리한다.

검토 후보:

```http
POST /admin/stages/{stageId}/results/{stageResultId}/reflect-interview-evaluation
POST /admin/stages/{stageId}/results/reflect-interview-evaluations
```

단, 이 command를 Phase 06에 포함할지 여부는 비판적으로 판단하라.  
초기 Phase 06에서는 자동/반자동 반영보다 평가 저장/제출/조회 안정화가 우선이다.

---

## 권장 slice 구성

다음 slice 구성을 기준으로 검토하되, 더 나은 분할이 있으면 제안하라.

```text
Phase 06 - Interview Evaluation

06a - InterviewEvaluation Domain
  - entity
  - enum
  - repository
  - unique constraint policy
  - evaluation status transition
  - score/comment/recommendation validation

06b - Interviewer Evaluation Write
  - interviewer-owned evaluation list/detail
  - draft save
  - submit
  - ownership/assignment guard
  - submitted immutable policy

06c - Admin Evaluation Read
  - interview-level evaluation read
  - stage-level evaluation read
  - application-level evaluation read
  - candidate/evaluator summary
  - no Excel/PDF/statistics

06d - Evaluation Finalization / StageResult Boundary
  - StageResult non-mutation guarantee
  - optional explicit reflection command design
  - interaction with existing StageResult correction/history policy
  - decide whether to defer reflection command

06e - Stabilization / Test Hardening
  - N candidates × M interviewers matrix regression
  - cancelled interview guard
  - draft/confirmed/cancelled visibility
  - submitted evaluation immutable
  - non-assigned interviewer forbidden
  - non-assigned candidate forbidden
  - StageResult non-mutation regression
```

---

## 설계 산출물

다음 파일을 생성/수정하라.

### 생성

- `docs/codex/design/phase-06-interview-evaluation-design.md`
- `docs/codex/reports/phase-06-interview-evaluation-design.html`

### 수정

- `docs/codex/06-implementation-roadmap.md`
- `docs/codex/07-implementation-history.md`
- `docs/codex/reports/current-implementation-status.html`

단, 실제 repository에 없는 파일명은 임의로 만들지 말고, 현재 문서 구조를 검색한 뒤 가장 적절한 파일에 반영하라.

---

## HTML 리포트 요구사항

`phase-06-interview-evaluation-design.html`은 사람이 보는 현황 문서다.

반드시 포함하라.

- Executive Summary
- Scope / Out of Scope
- Domain Model Diagram 또는 구조 표
- API 후보 목록
- State Transition
- StageResult Boundary
- Slice Plan
- Open Questions
- Decision Log

HTML은 기존 `docs/codex/reports/*.html` 스타일을 참고해서 작성하라.

---

## 명확히 제외할 것

이번 설계에서 다음은 구현하지 않는다.

- Java 소스 생성/수정
- 테스트 코드 생성/수정
- 컨트롤러/서비스/리포지토리 구현
- DB 마이그레이션 SQL 생성
- Excel/PDF/statistics
- 평가표 템플릿 관리 기능
- 복잡한 가중치/평가 항목별 세부 배점
- 자동 합격/불합격 판정
- 면접관 제출만으로 StageResult 변경
- 메시지/메일/알림 발송

---

## 설계 품질 기준

설계 완료 후 다음 질문에 명확히 답할 수 있어야 한다.

1. 평가 row는 언제 생성되는가?
2. 평가 row는 어떤 unique key로 중복을 막는가?
3. 면접관은 어떤 조건에서 평가를 저장/제출할 수 있는가?
4. 제출된 평가는 수정 가능한가?
5. 취소된 면접 또는 취소된 참가자의 평가는 어떻게 되는가?
6. 관리자 조회에서 후보자별/면접관별 제출 현황을 어떻게 보여주는가?
7. StageResult는 언제, 누가, 어떤 command로 바뀌는가?
8. 면접 평가 제출이 StageResult를 자동 변경하지 않는다는 보장은 어디에 명시되는가?
9. Phase 06을 어떤 slice 순서로 구현하는가?
10. Phase 07 또는 후속 phase로 넘길 항목은 무엇인가?

---

## 최종 출력 형식

작업 결과를 다음 순서로 보고하라.

1. 생성/수정한 문서 목록
2. 핵심 설계 결정 요약
3. 확정된 slice plan
4. StageResult boundary 요약
5. Open questions
6. 이번 작업에서 하지 않은 것
7. 다음 권장 작업

작업 중 애매한 부분이 있으면 임의로 확장하지 말고, 문서에 `Open Questions`로 남겨라.
```
