확인 필요: JobPosition.create 시그니처 정합성

첨부된 InterviewEvaluationTest와 InterviewEvaluationRepositoryTest에서는 아직 아래 형태를 사용한다.

JobPosition.create("본사영업", 1)

그런데 같은 첨부 묶음의 07-implementation-history.md에는 JobPosition headcount 필드 제거 작업이 기록되어 있고, JobPosition.create 인자에서도 headcount를 제거했다고 되어 있다.

즉 둘 중 하나다.

현재 실제 소스에는 아직 JobPosition.create(String, int)가 남아 있다.
→ 그러면 history의 headcount 제거 기록이 실제 코드와 안 맞는다.
현재 실제 소스는 JobPosition.create(String)로 바뀌었다.
→ 그러면 첨부된 06a 테스트 파일은 최신 코드 기준으로 컴파일 실패한다.

이건 문서/테스트 정합성 이슈다. 06a 자체 설계보다 더 현실적인 문제라, 다음 작업 전에 반드시 최신 브랜치에서 아래를 확인해야 한다.

$env:AES_SECRET_KEY='22791194512954214612461221261067'
.\gradlew.bat test --tests "*InterviewEvaluation*" --no-daemon

가능하면 전체 테스트도 한 번 돌리는 게 맞다.

보완 권장 1: 엔티티 검증 테스트가 아직 얇다

엔티티 코드에는 null/동일 interview/role 검증이 들어가 있다. 그런데 테스트는 role 오류와 interview == null 정도만 직접 커버한다.

아래 테스트는 추가하는 게 좋다.

initialize시_candidateParticipant가_null이면_실패한다
initialize시_interviewerParticipant가_null이면_실패한다
initialize시_interview_stage가_null이면_실패한다
initialize시_candidateParticipant의_jobApplication이_null이면_실패한다
initialize시_candidateParticipant가_다른_interview에_속하면_실패한다
initialize시_interviewerParticipant가_다른_interview에_속하면_실패한다
submit시_submittedAt이_null이면_실패한다
comment_2000자는_허용한다
blank_comment는_null로_정규화된다

지금도 코드에는 방어 로직이 있지만, 테스트가 없어서 06b/06c 리팩토링 중 빠져도 빨리 못 잡는다.

보완 권장 2: 06b에서 필요한 Repository 조회가 아직 없다

현재 InterviewEvaluationRepository는 findByInterviewId와 유니크키 존재 조회만 있다.
06a 범위라면 괜찮지만, 06b에서 면접관 평가 목록/상세를 구현하려면 최소한 다음 조회가 필요해질 가능성이 높다.

findByInterviewIdAndInterviewerParticipantId(...)
findByInterviewIdAndInterviewerParticipantEmployeeId(...)
findByIdAndInterviewerParticipantEmployeeId(...)
findByInterviewIdOrderByCandidateParticipantSortOrderAscInterviewerParticipantSortOrderAsc(...)

특히 면접관 API에서는 path/body로 employeeId를 받으면 안 되고, 현재 로그인한 employee 기준으로만 접근해야 한다. 이건 기존 Phase 04d interviewer read 정책과도 맞다.

보완 권장 3: CONFIRMED / ASSIGNED guard는 06b에서 반드시 막아야 한다

현재 InterviewEvaluation.initialize()는 role과 동일 interview 소속은 검증하지만, Interview.status == CONFIRMED나 InterviewParticipant.status == ASSIGNED는 검증하지 않는다.

이건 06a의 결함이라기보다는 06b 서비스 책임으로 남긴 것으로 보인다. 로드맵도 06b 범위에 CONFIRMED/ASSIGNED guard를 명시하고 있다.

따라서 06b에서는 반드시 막아야 한다.

DRAFT interview        → initialize/save/submit 불가
CANCELLED interview    → initialize/save/submit 불가
CANCELLED candidate    → initialize/save/submit 불가
CANCELLED interviewer  → initialize/save/submit 불가
SUBMITTED evaluation   → save/submit 불가
보완 권장 4: 문서 일부가 stale 상태

current-implementation-status.html은 06a domain complete와 남은 06b~06e를 제대로 적고 있다.

그런데 07-implementation-history.md의 Current remaining major work 쪽에는 아직 Phase 06 implementation: interview evaluation (design completed, Java pending)이라고 되어 있다. 06a Java 구현이 이미 추가된 상태와 맞지 않는다.

이 문구는 이렇게 바꾸는 게 맞다.

Phase 06 implementation: 06a domain completed; 06b~06e pending.