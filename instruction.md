# Phase 04d - Interviewer Interview Read 구현 지시문

## 목표

Phase 04d - Interviewer Interview Read를 구현하라.

이번 slice의 목표는 Phase 04b에서 확정/취소 가능한 관리자 면접 일정 관리 기능 위에, **면접관이 본인에게 배정된 면접 일정을 조회할 수 있는 read-only API**를 추가하는 것이다.

Phase 04c가 지원자 본인 조회였다면, Phase 04d는 면접관 본인 조회다.

핵심 원칙:

```text
면접관은 본인이 INTERVIEWER participant로 배정된 면접 일정만 볼 수 있다.
DRAFT 일정은 절대 면접관에게 노출하지 않는다.
CONFIRMED / CANCELLED 일정만 노출한다.
Interview Scheduling은 StageResult를 변경하지 않는다.
```

---

## 1. 이번 slice의 책임

이번 slice는 면접관 본인 조회 기능에 한정한다.

구현 범위:

```text
- 면접관 본인 면접 일정 목록 조회
- 면접관 본인 면접 일정 상세 조회
- current employee ownership guard
- DRAFT 미노출 guard
- participant role/status guard
- response DTO 추가
- interviewer read service/controller 추가
- repository read query 추가
- targeted test 추가
- Phase 04d 구현 문서 및 이력 갱신
```

이번 slice는 read-only다.

---

## 2. 이번 slice에서 하지 말 것

절대 구현하지 말 것:

```text
- 관리자 면접 일정 생성/수정/확정/취소 기능 변경
- 지원자 본인 면접 일정 조회 API 변경
- 면접 평가
- InterviewEvaluation
- 점수/등급/평가표 입력
- 면접 결과 합격/불합격 처리
- StageResult 생성
- StageResult 수정
- StageResult 발표 상태 변경
- StageResult 정정 이력 변경
- 알림톡/SMS/Email 발송
- 메시지 큐
- Excel upload/download
- PDF 생성
- Calendar 연동
- frontend/static resource
- Flyway/Liquibase/migration file
- 운영 MariaDB DDL 파일
```

---

## 3. 반드시 먼저 확인할 문서와 코드

작업 전 아래 문서와 코드를 먼저 확인하라.

### 문서

```text
docs/codex/design/phase-04-interview-scheduling-design.md
docs/codex/implementation/phase-04a-interview-scheduling-domain.md
docs/codex/implementation/phase-04b-admin-interview-schedule-management.md
docs/codex/implementation/phase-04c-applicant-interview-read.md
docs/codex/06-implementation-roadmap.md
docs/codex/07-implementation-history.md
docs/codex/01-project-context.md
```

### 코드

```text
Interview
InterviewParticipant
InterviewRepository
InterviewParticipantRepository
InterviewService 또는 AdminInterviewService
ApplicantInterviewService
ApplicantInterviewController
InterviewAdminController
InterviewStatus
InterviewParticipantRole
InterviewParticipantStatus
Employee
EmployeeRepository
User 또는 현재 인증 principal 처리 구조
기존 employee/admin controller/service/test 패턴
기존 Security/Session 사용자 식별 방식
GlobalExceptionHandler
```

---

## 4. 선행 확인 사항

04d 작업 전 04b/04c가 다음 정책을 만족하는지 확인하라.

```text
1. DRAFT interview는 외부 사용자에게 노출되지 않는다.
2. CONFIRMED interview만 실제 면접 일정으로 볼 수 있다.
3. CANCELLED interview도 participant row가 유지된다.
4. cancel 시 participantStatus를 일괄 CANCELLED로 바꾸지 않는다.
5. 04c 지원자 조회 API는 본인 소유권과 DRAFT 미노출을 지킨다.
6. StageResult mutation이 없다.
```

04d에서 04b/04c 기능을 대규모로 수정하지 마라.  
단, interviewer read 안정성을 위해 명백한 결함이 발견되면 최소 수정하고 구현 문서에 사유를 기록하라.

---

## 5. 노출 정책

면접관에게 노출 가능한 면접 일정은 다음 조건을 모두 만족해야 한다.

```text
1. Interview.status in (CONFIRMED, CANCELLED)
2. Interview.status != DRAFT
3. InterviewParticipant.role = INTERVIEWER
4. InterviewParticipant.participantStatus = ASSIGNED
5. InterviewParticipant.employee가 현재 로그인한 employee여야 함
```

`CANCELLED` 일정은 노출한다.

이유:

```text
면접관이 이미 배정받은 면접이 취소되었음을 확인할 수 있어야 한다.
04b 정책상 cancel은 Interview.status만 CANCELLED로 바꾸고 participant row는 유지한다.
```

단, `DRAFT`는 절대 노출하지 않는다.

이유:

```text
DRAFT 면접 일정은 관리자 준비 상태다.
면접관에게 노출되면 확정 전 일정/후보자 정보가 불필요하게 노출된다.
```

---

## 6. 보안/소유권 원칙

면접관 조회 API는 반드시 현재 로그인한 사용자 기준으로 본인에게 배정된 일정만 조회해야 한다.

금지:

```text
- request parameter로 employeeId를 받아서 신뢰
- request body로 userId/employeeId를 받아서 신뢰
- interviewId만으로 조회하고 employee assignment 검증 생략
- INTERVIEWER participant가 아닌 candidate participant를 기준으로 조회
```

필수:

```text
- 현재 인증 principal/session에서 employee 식별
- InterviewParticipant.employee가 현재 employee인지 확인
- interview detail 조회 시 해당 interview에 현재 employee의 INTERVIEWER participant가 있는지 확인
```

기존 프로젝트에 current employee resolver, security util, custom user details가 있으면 반드시 재사용하라.

후보 이름:

```text
CurrentUser
CustomUserDetails
SecurityUtils
CurrentEmployeeService
AuthenticatedEmployee
EmployeeContext
```

정확한 이름은 현재 코드 기준으로 확인하라.

---

## 7. API 범위

기존 employee/admin API URI 패턴을 먼저 확인하고 그 규칙에 맞춰라.

권장 API는 아래와 같다.

### 7.1 면접관 본인 면접 일정 목록 조회

```http
GET /interviewer/interviews
```

또는 기존 프로젝트가 employee 영역을 `/employee/**`로 통일한다면:

```http
GET /employee/interviews
```

선택 기준:

```text
- 면접관 기능이 독립 role이면 /interviewer/interviews 권장
- 직원 기능 하위 메뉴라면 /employee/interviews 허용
- 선택한 URI와 사유를 구현 문서에 기록
```

목적:

```text
현재 로그인한 면접관이 본인에게 배정받은 CONFIRMED/CANCELLED 면접 일정을 조회한다.
```

Query parameter 후보:

```text
status: optional InterviewStatus, 단 CONFIRMED/CANCELLED만 허용
from: optional LocalDateTime
to: optional LocalDateTime
```

정책:

```text
- status 미지정 시 CONFIRMED, CANCELLED 모두 조회
- DRAFT는 status query로 들어와도 거부해야 함
- from/to는 일정 기간 필터로 사용
```

---

### 7.2 면접관 본인 면접 일정 상세 조회

```http
GET /interviewer/interviews/{interviewId}
```

또는:

```http
GET /employee/interviews/{interviewId}
```

목적:

```text
현재 로그인한 면접관이 본인에게 배정된 특정 면접 일정 상세를 조회한다.
```

정책:

```text
- interviewId가 존재해도 현재 employee에게 INTERVIEWER로 배정되지 않았으면 404 또는 403
- DRAFT interview는 404 또는 403
- participantStatus가 ASSIGNED가 아니면 404 또는 403
- CONFIRMED/CANCELLED만 조회 가능
```

권장:

```text
보안상 타인의 interview 존재 여부를 노출하지 않기 위해 404 처리
```

---

## 8. Response DTO

### 8.1 목록 응답 DTO 후보

```java
InterviewerInterviewSummaryResponse
```

필드 후보:

```text
interviewId
jobPostingId
jobPostingTitle
stageId
stageName
stageType
groupName
startDateTime
endDateTime
method
locationName
roomName
onlineMeetingUrl
status
cancelled
candidateCount
```

주의:

```text
- admin memo는 노출하지 않는다.
- 다른 면접관 employeeId/사번/부서 상세는 목록에서 노출하지 않는다.
- StageResult 내부 상태/정정 이력은 노출하지 않는다.
- candidateCount는 노출 가능하다.
```

---

### 8.2 상세 응답 DTO 후보

```java
InterviewerInterviewDetailResponse
InterviewerInterviewCandidateResponse
```

상세 응답 필드 후보:

```text
interviewId
jobPostingId
jobPostingTitle
stageId
stageName
stageType
groupName
startDateTime
endDateTime
method
locationName
roomName
onlineMeetingUrl
status
cancelled
candidates[]
guideMessage
```

candidate 응답 필드 후보:

```text
jobApplicationId
applicantId
applicantName
positionId
positionName
sortOrder
```

주의:

```text
- 면접관은 면접 진행을 위해 candidate 목록을 볼 수 있다.
- candidate 목록은 해당 interview에 ASSIGNED 상태로 배정된 CANDIDATE participant만 포함한다.
- 다른 면접관 목록은 기본적으로 노출하지 않는다.
- admin memo는 노출하지 않는다.
- StageResult 내부 상태/정정 이력은 노출하지 않는다.
```

`guideMessage`는 선택이다.

예:

```text
CONFIRMED: 면접 일정을 확인해 주세요.
CANCELLED: 면접 일정이 취소되었습니다.
```

단, 메시지 로직을 과도하게 만들지 마라.  
필요 없으면 status만 내려주고 프론트에서 처리하게 한다.

---

## 9. 노출 금지 필드

면접관 응답에는 아래를 넣지 마라.

```text
- Interview.memo
- cancelReason 또는 internal memo
- 관리자 내부 메모
- 다른 면접관 employeeId
- 다른 면접관 사번
- 다른 면접관 부서 상세
- StageResult 내부 상태
- StageResult 정정 이력
- result announcement 내부 플래그
- 지원자의 민감한 개인정보 전체
```

면접관에게 필요한 정보만 내려준다.

```text
면접 일시
면접 방식
장소/회의 URL
조/그룹명
공고/전형명
상태
후보자 이름/지원서/모집분야 정도
```

후보자 상세 개인정보는 후속 면접 평가/면접관 자료 제공 phase에서 별도 정책으로 다룬다.

---

## 10. Service 구현

Service 후보:

```java
InterviewerInterviewService
```

기존 프로젝트가 employee read service를 별도로 두는 규칙이면 그 규칙을 따른다.

필수 public method 후보:

```java
List<InterviewerInterviewSummaryResponse> getMyInterviews(
    InterviewStatus status,
    LocalDateTime from,
    LocalDateTime to
)

InterviewerInterviewDetailResponse getMyInterviewDetail(Long interviewId)
```

현재 employee 식별이 method argument로 필요하면, 기존 패턴을 따른다.

예:

```java
List<InterviewerInterviewSummaryResponse> getMyInterviews(
    Long currentEmployeeId,
    InterviewStatus status,
    LocalDateTime from,
    LocalDateTime to
)
```

또는:

```java
List<InterviewerInterviewSummaryResponse> getMyInterviews(
    CustomUserDetails currentUser,
    InterviewStatus status,
    LocalDateTime from,
    LocalDateTime to
)
```

Controller에서 principal을 받고 service에 넘기는 방식은 기존 employee/admin API 패턴을 따른다.

---

## 11. Repository query

기존 04a/04b/04c repository method를 재사용할 수 있으면 재사용하라.

필요하면 `InterviewParticipantRepository`에 interviewer read query를 추가한다.

### 11.1 내 면접 일정 목록 query 후보

```java
@Query(...)
List<InterviewParticipant> findVisibleInterviewerInterviewParticipants(
    Long employeeId,
    InterviewStatus status,
    LocalDateTime from,
    LocalDateTime to
)
```

조건:

```text
p.role = INTERVIEWER
p.participantStatus = ASSIGNED
p.employee.id = :employeeId
p.interview.status in (CONFIRMED, CANCELLED)
optional status filter
optional from/to filter
order by p.interview.startDateTime asc, p.interview.id asc
```

### 11.2 상세 query 후보

```java
@Query(...)
Optional<InterviewParticipant> findVisibleInterviewerInterviewParticipant(
    Long employeeId,
    Long interviewId
)
```

조건:

```text
p.interview.id = :interviewId
p.role = INTERVIEWER
p.participantStatus = ASSIGNED
p.employee.id = :employeeId
p.interview.status in (CONFIRMED, CANCELLED)
```

### 11.3 상세 후보자 목록 query 후보

```java
@Query(...)
List<InterviewParticipant> findAssignedCandidatesByInterviewId(
    Long interviewId
)
```

조건:

```text
p.interview.id = :interviewId
p.role = CANDIDATE
p.participantStatus = ASSIGNED
order by p.sortOrder asc nulls last, p.id asc
```

주의:

```text
- Interview를 직접 조회한 뒤 employee ownership을 나중에 검사하는 방식보다,
  repository query에서 employee assignment까지 같이 제한하는 방식이 안전하다.
- 후보자 목록은 detail ownership 검증이 끝난 뒤 조회하라.
- fetch join이 필요하면 사용하되, 과도한 graph를 만들지 마라.
```

---

## 12. status query 정책

면접관 API에서 status query를 받을 경우 다음만 허용한다.

```text
CONFIRMED
CANCELLED
```

금지:

```text
DRAFT
```

정책:

```text
목록 API: DRAFT 요청 시 400
상세 API: DRAFT 일정은 404
```

---

## 13. from/to 필터 정책

from/to가 있으면 일정 overlap semantics를 사용한다.

```text
existing.startDateTime < requestedTo
and requestedFrom < existing.endDateTime
```

단, from/to 중 하나만 있는 경우:

```text
from only: interview.endDateTime > from
to only: interview.startDateTime < to
both: overlap
```

from이 to보다 같거나 이후면 400 처리한다.

```text
from >= to 이면 잘못된 기간 요청
```

---

## 14. Controller 구현

Controller 후보:

```java
InterviewerInterviewController
```

패키지는 기존 employee/interviewer/admin controller 위치를 따른다.

권장 endpoint:

```http
GET /interviewer/interviews
GET /interviewer/interviews/{interviewId}
```

또는 기존 URI 정책에 따라:

```http
GET /employee/interviews
GET /employee/interviews/{interviewId}
```

응답 wrapper는 기존 프로젝트의 `ApiResponse<T>` 패턴을 따른다.

주의:

```text
- employeeId를 path/query/body로 받지 마라.
- 현재 인증 사용자 기준으로만 조회하라.
- 기존 employee/admin controller security 설정을 따른다.
```

---

## 15. Security 설정

기존 role/authority 구조를 먼저 확인하라.

후보:

```text
ROLE_EMPLOYEE
ROLE_ADMIN
ROLE_INTERVIEWER
```

정책:

```text
- 면접관 조회 API는 최소 직원 인증이 필요하다.
- 실제 면접 일정 노출 여부는 role보다 participant assignment로 최종 결정한다.
- ROLE_ADMIN에게도 이 API를 열지 여부는 기존 정책을 따른다.
```

권장:

```text
/interviewer/** 또는 /employee/** 는 employee 계열 인증 사용자만 접근 가능
```

주의:

```text
- Admin이 아닌 일반 직원도 면접관이 될 수 있다면 ROLE_ADMIN만 허용하면 안 된다.
- 반대로 applicant가 접근할 수 있으면 안 된다.
```

테스트에서 다음을 확인하라.

```text
- employee/interviewer 인증 성공
- applicant 인증 실패
- anonymous 실패
```

---

## 16. 예외 처리

기존 프로젝트 예외 패턴을 따른다.

후보:

```text
InterviewNotFoundException 재사용 가능
InvalidInterviewException 재사용 가능
EmployeeNotFoundException 또는 기존 Employee 관련 예외 재사용 가능
AccessDeniedException 또는 기존 Forbidden exception 재사용 가능
```

정책:

```text
- 존재하지 않는 interview: 404
- 존재하지만 본인에게 배정되지 않음: 404 권장, 기존 정책이 403이면 403 허용
- DRAFT interview 접근: 404 권장
- status=DRAFT query: 400
- from >= to: 400
```

GlobalExceptionHandler에 필요한 매핑이 없으면 최소 추가한다.

---

## 17. 테스트 요구사항

프로젝트 기존 테스트 스타일을 따른다.

추가 후보:

```text
src/test/java/com/shinyoung/recruit/service/InterviewerInterviewServiceTest.java
src/test/java/com/shinyoung/recruit/controller/InterviewerInterviewControllerTest.java
```

repository query를 추가했다면 기존 repository test에 query test를 추가하거나 새 파일을 만든다.

후보:

```text
src/test/java/com/shinyoung/recruit/repository/InterviewParticipantRepositoryTest.java
```

---

## 18. Service test 필수 케이스

### getMyInterviews

```text
- 현재 employee의 CONFIRMED 면접 일정 목록 조회 성공
- 현재 employee의 CANCELLED 면접 일정 목록 조회 성공
- DRAFT 일정은 목록에 나오지 않음
- 타 employee의 일정은 목록에 나오지 않음
- participantStatus=CANCELLED인 interviewer row는 목록에 나오지 않음
- role=CANDIDATE row는 목록에 나오지 않음
- status=CONFIRMED filter 동작
- status=CANCELLED filter 동작
- status=DRAFT 요청 시 실패
- from/to 기간 필터 동작
- from >= to 요청 시 실패
```

### getMyInterviewDetail

```text
- 본인에게 INTERVIEWER로 배정된 CONFIRMED interview 상세 조회 성공
- 본인에게 INTERVIEWER로 배정된 CANCELLED interview 상세 조회 성공
- DRAFT interview 상세 조회 실패
- 타 employee에게 배정된 interview 상세 조회 실패
- participantStatus=CANCELLED면 조회 실패
- candidate participant만 있는 경우 조회 실패
- 상세 응답에 ASSIGNED candidate 목록이 포함됨
- 상세 응답에 participantStatus=CANCELLED candidate는 포함되지 않음
- 응답에 admin memo가 포함되지 않음
- 응답에 StageResult 내부 상태가 포함되지 않음
```

---

## 19. Controller test 필수 케이스

기존 controller test 스타일을 따른다.

후보:

```text
- GET /interviewer/interviews 성공
- GET /interviewer/interviews/{interviewId} 성공
- status=DRAFT 요청 시 400
- from >= to 요청 시 400
- 타 employee interview 접근 시 404 또는 403
- DRAFT interview detail 접근 시 404 또는 403
- applicant 인증 사용자는 접근 실패
- anonymous 사용자는 접근 실패
```

Security 설정이 controller test에 영향을 주면 기존 controller test 패턴을 그대로 따른다.

---

## 20. Repository test 필수 케이스

repository query를 추가한 경우 다음을 테스트한다.

```text
- CONFIRMED interviewer assigned participant 조회
- CANCELLED interview도 조회
- DRAFT interview는 제외
- 다른 employee의 participant는 제외
- participantStatus=CANCELLED는 제외
- role=CANDIDATE는 제외
- interviewId detail query ownership 동작
- assigned candidate list query 동작
- candidate participantStatus=CANCELLED는 candidate list에서 제외
- from/to filter 동작
```

---

## 21. 문서 갱신

구현 완료 후 아래 문서를 갱신하라.

### 21.1 신규 구현 문서 생성

```text
docs/codex/implementation/phase-04d-interviewer-interview-read.md
```

포함할 내용:

```text
- 구현 목표
- 구현 파일 목록
- 추가/수정 DTO 목록
- 추가/수정 service 목록
- 추가/수정 controller 목록
- 추가/수정 repository query 목록
- interviewer API 목록
- current employee ownership guard 설명
- DRAFT 미노출 guard 설명
- CONFIRMED/CANCELLED 노출 정책
- 상세 candidate 목록 노출 정책
- 노출 금지 필드
- StageResult non-mutation 확인
- 테스트 목록
- 실행한 targeted test 명령
- 전체 테스트를 실행하지 않은 사유
  - 개발 PC 성능 문제로 전체 테스트 타임아웃 발생
  - 이번 slice에서는 수정/추가 패키지 중심 targeted test만 실행
- 명시적 제외 범위
- 수동 DDL/운영 DB 반영 메모
  - 이번 slice에서는 migration file을 만들지 않았음을 명시
```

### 21.2 구현 이력 갱신

```text
docs/codex/07-implementation-history.md
```

Phase 04d 구현 이력을 추가하라.

### 21.3 로드맵 갱신

```text
docs/codex/06-implementation-roadmap.md
```

Phase 04에서 04d가 완료되었고, 다음 후보가 04e Interview Scheduling Stabilization 또는 Phase 05로 이동인지 현재 roadmap 기준으로 반영하라.

권장 next:

```text
Phase 04e - Interview Scheduling Stabilization / Test Hardening
```

단, 기존 roadmap에 Phase 04e가 없다면 다음 Phase 후보를 문서 기준으로 정리하라.

### 21.4 HTML report

프로젝트 AGENTS.md에 Phase별 HTML report 생성/갱신 규칙이 있으면 다음 파일도 생성 또는 갱신하라.

```text
docs/codex/reports/phase-04d-interviewer-interview-read.html
docs/codex/reports/current-implementation-status.html
```

HTML 생성 규칙이 명시되어 있지 않으면 Markdown 문서만 갱신하고, final report에 HTML 미생성 사유를 적어라.

---

## 22. 완료 조건

다음 조건을 모두 만족해야 한다.

```text
- 면접관 본인 면접 일정 목록 조회 API가 구현되었다.
- 면접관 본인 면접 일정 상세 조회 API가 구현되었다.
- 현재 로그인한 employee 기준 ownership guard가 구현되었다.
- employeeId를 request에서 신뢰하지 않는다.
- DRAFT interview는 면접관에게 노출되지 않는다.
- CONFIRMED interview는 면접관에게 노출된다.
- CANCELLED interview는 면접관에게 노출된다.
- role=INTERVIEWER participant만 조회된다.
- participantStatus=ASSIGNED인 interviewer participant만 조회된다.
- 타 employee 일정은 조회되지 않는다.
- 상세 응답에는 해당 interview의 ASSIGNED candidate 목록만 포함된다.
- 응답에 admin memo가 포함되지 않는다.
- 응답에 StageResult 내부 상태/정정 이력이 포함되지 않는다.
- StageResult save/update/delete가 추가되지 않았다.
- 관리자 API 동작을 변경하지 않았다.
- 지원자 read API 동작을 변경하지 않았다.
- InterviewEvaluation이 추가되지 않았다.
- message/excel/pdf/calendar/frontend가 추가되지 않았다.
- 전체 테스트를 실행하지 않았다.
- 전체 테스트 미실행 사유를 문서와 최종 보고에 명시했다.
- 수정/추가 패키지 중심 targeted test만 실행했다.
- Phase 04d 구현 문서가 생성되었다.
- 07-implementation-history.md가 갱신되었다.
- 06-implementation-roadmap.md가 갱신되었다.
- 다음 작업 후보가 roadmap/history에 정리되었다.
```

---

## 23. 최종 보고 형식

작업 완료 후 다음 형식으로 보고하라.

```md
## Phase 04d 구현 결과

### 전체 판단
- PASS / PARTIAL / FAIL 중 하나

### 구현 파일
- ...

### API 목록
- ...

### 주요 guard
- Current employee ownership:
- DRAFT 미노출:
- CONFIRMED/CANCELLED 노출:
- Interviewer role:
- Participant status:
- 타 employee 일정 차단:
- Candidate list 노출 정책:
- 노출 금지 필드:

### StageResult 비변경 확인
- 수정 여부:
- save/update/delete 추가 여부:
- 확인 내용:

### 테스트 결과
- 전체 테스트 실행 여부: 실행하지 않음
- 전체 테스트 미실행 사유: 개발 PC 성능 문제로 전체 테스트 타임아웃 발생
- 실행한 targeted test 명령:
  - ...
- 결과:
  - ...

### 의도적으로 제외한 항목
- Admin write API 변경:
- Applicant read API 변경:
- InterviewEvaluation:
- Message/SMS/Email:
- Excel/PDF/Calendar:
- Frontend:
- DB migration:

### 문서 갱신
- ...

### 다음 권장 작업
- Phase 04e - Interview Scheduling Stabilization / Test Hardening
  또는 현재 roadmap 기준 다음 Phase
```

---

## 24. 마지막 주의사항

04d는 면접관 read-only slice다.

가장 위험한 부분은 면접관에게 보여주면 안 되는 내부 정보를 노출하는 것이다.

반드시 다음을 지켜라.

```text
1. DRAFT interview는 절대 노출하지 않는다.
2. 현재 로그인한 employee가 INTERVIEWER participant로 배정된 일정만 조회한다.
3. admin memo, StageResult 내부 상태, 다른 면접관 상세 정보는 응답에 포함하지 않는다.
4. 후보자 목록은 해당 interview의 ASSIGNED CANDIDATE participant만 포함한다.
5. StageResult는 변경하지 않는다.
```

---

## 25. 전체 테스트 관련 필수 문구

현재 개발 PC 성능 문제로 전체 테스트 실행 시 타임아웃이 발생한다.

따라서 이번 Phase 04d 작업에서는 전체 테스트를 실행하지 마라.

금지 명령:

```powershell
.\\gradlew.bat test
.\\gradlew.bat clean test
```

대신 이번에 수정/추가한 패키지와 직접 관련된 targeted test만 실행하라.

권장 targeted test 명령 예시:

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\\gradlew.bat test --tests com.shinyoung.recruit.service.InterviewerInterviewServiceTest --no-daemon
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\\gradlew.bat test --tests com.shinyoung.recruit.controller.InterviewerInterviewControllerTest --no-daemon
```

repository query를 추가 또는 수정했다면 다음도 실행하라.

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\\gradlew.bat test --tests com.shinyoung.recruit.repository.InterviewParticipantRepositoryTest --no-daemon
```

필요 시 직접 충돌 가능성이 있는 기존 regression만 추가로 실행하라.

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\\gradlew.bat test --tests com.shinyoung.recruit.service.ApplicantInterviewServiceTest --no-daemon
```

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\\gradlew.bat test --tests com.shinyoung.recruit.service.InterviewServiceTest --no-daemon
```

최종 보고와 구현 문서에는 반드시 다음을 기록하라.

```text
- 전체 테스트는 실행하지 않았음
- 사유: 개발 PC 성능 문제로 전체 테스트 타임아웃 발생
- 대신 수정/추가 패키지 중심 targeted test만 실행했음
- 실행한 targeted test 명령과 결과
```
