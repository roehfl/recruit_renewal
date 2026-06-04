1. High — PhysicalFileStatus.DELETED → SOFT_DELETED는 배포 순서 리스크가 있다

설계는 기존 DELETED를 SOFT_DELETED로 개명하고 DB의 'DELETED' row를 수동 UPDATE하라고 한다.

현재 enum은 아직 DELETED만 있다.

문제는 운영 DB에 'DELETED' 값이 남아 있는데 코드에서 enum 값을 제거하고 SOFT_DELETED만 두면, JPA가 기존 row를 읽는 순간 enum 매핑 오류가 날 수 있다.

수정 지시:

9d-2에서 한 번에 rename하지 말고 안전하게 가라.

1단계:
- enum에 DELETED와 SOFT_DELETED를 둘 다 둔다.
- markDeleted()는 SOFT_DELETED를 쓰게 변경한다.
- health scan은 DELETED와 SOFT_DELETED를 모두 soft-deleted로 취급한다.

2단계:
- 수동 DDL/UPDATE로 기존 'DELETED' → 'SOFT_DELETED' 변경.
- 테스트로 DELETED 잔존 0건 확인.

3단계:
- 후속 phase에서 DELETED enum 제거.

이건 설계 문서에 추가하는 게 좋다. 지금 상태로 구현자가 바로 enum 삭제하면 운영에서 터질 수 있다.

2. Medium — applicantRefHash 입력값이 아직 명확하지 않다

설계서는 applicantRefHash = HMAC-SHA256 + server pepper라고만 되어 있고, 무엇을 HMAC 입력으로 삼는지가 명시되지 않았다.

이게 중요하다. 절대 ci, ciHash, email, phone을 입력값으로 쓰면 안 된다. ciHash를 overwrite하는 방향과도 충돌한다.

권장 확정안:

applicantRefHash = HMAC_SHA256(AUDIT_HMAC_SECRET, "APPLICANT:" + applicantId)

또는 application 단위 연결만 필요하면:

applicationRefHash = HMAC_SHA256(AUDIT_HMAC_SECRET, "APPLICATION:" + applicationId)

Phase 9의 목적상 applicantRefHash는 여러 지원서를 같은 지원자 기준으로 묶는 감사 연결자일 가능성이 높으니 applicantId 기반이 맞다. 문서에 입력값을 박아라.

3. Medium — 날짜 일반화 컬럼이 명확하지 않다

인벤토리는 학력/경력 정확 날짜를 보존하지 않고 GENERALIZE+NULLIFY로 바꿨다. 방향은 맞다.

그런데 “연도 컬럼을 새로 둘 것인지, 그냥 null 처리할 것인지”가 아직 최종 확정되지 않았다.

예를 들어 문서에는 이런 표현이 있다.

admissionYear / graduationYear
year-month bucket 또는 근속개월수

하지만 DDL 요약에는 이 일반화용 신규 컬럼이 명확히 나열되지 않았다. DDL 요약에는 nullable 변경 중심으로만 적혀 있다.

수정 지시:

9d 전에 둘 중 하나로 확정해라.

안 A: 통계 포기
- admissionDate, graduationDate, career.startDate, career.endDate 전부 null
- 일반화 컬럼 추가 없음

안 B: 일반화 통계 유지
- admissionYear int nullable
- graduationYear int nullable
- careerStartYearMonth varchar(7) nullable
- careerEndYearMonth varchar(7) nullable
- careerDurationMonths int nullable

애매하게 두면 9d 구현 때 또 흔들린다.

4. Medium — RetentionPolicy 충돌 시 fail-safe 처리가 필요하다

선택 규칙은 좋아졌지만, overlap 금지를 “검증”한다고만 하면 운영 DB 직접 수정이나 과거 데이터로 충돌이 생겼을 때 dry-run이 비결정적일 수 있다.

추가 지시:

active policy 후보가 2개 이상이면 아무 것도 선택하지 않는다.
해당 application/jobPosting은 SKIPPED + POLICY_CONFLICT 로 남긴다.
ActivityLog/PurgeBatch summary에도 policyConflictCount를 남긴다.

이 정도면 충분하다.

5. Low — Interview.memo 잔존은 범위 밖으로 인정 가능하지만 운영 가이드가 필요하다

인벤토리는 Interview.memo, locationName, roomName, onlineMeetingUrl이 interview-level 공유 행이라 per-application purge 대상이 아니라고 정리했다.

이건 현실적으로 맞다. 다만 memo에 후보 실명이 들어가면 잔존한다.

Phase 9 범위 밖으로 빼는 건 허용하지만, 최소한 운영 가이드 문구는 추가하는 게 좋다.

Interview.memo에는 후보 실명, 전화번호, 이메일, 평가성 자유서술을 입력하지 않는다.
후보별 메모/평가는 InterviewEvaluation.comment로만 남기고, purge 시 nullify한다.