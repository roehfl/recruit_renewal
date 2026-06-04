1. Blocker — ciHash 보존 설계가 현재 코드와 충돌한다

PII inventory는 Applicant.ciHash를 HASH_ONLY로 보존한다고 정리했다. 문서에는 “HMAC 단방향”처럼 적혀 있지만, 실제 코드는 HMAC이 아니다. 그냥 SHA-256이다.

실제 HashUtil은 단순 MessageDigest.getInstance("SHA-256")이고 salt/pepper/HMAC이 없다.

더 큰 문제는 회원가입에서 existsByCiHash(ciHash)로 중복 가입을 막고 있다는 점이다.

즉, ref-count 이후 ci, name, email, loginId, password를 지워도 ciHash를 그대로 남기면:

실제 CI 기반 연결자가 남는다.
파기 후 같은 사람이 다시 지원자 계정을 만들 수 없다.
“비가역 파기”라고 보기 어렵다.

이건 설계상 Blocker다.

수정안:

ref0 파기 시점에는 ciHash도 그대로 보존하지 말고 아래 중 하나로 가야 한다.

권장안 A:
ci = null
ciHash = "PURGED:" + UUID/random value

또는

권장안 B:
ci = null
ciHash nullable DDL 변경 후 null

중복 가입 방지 기능을 계속 유지하고 싶다면 그건 “파기 후에도 동일인 식별을 계속 보유하겠다”는 뜻이라, 개인정보 파기 목적과 충돌한다. Phase 9에서는 파기 우선으로 가는 게 맞다.

2. High — Upload audit의 sourceFileName은 PII-free가 아니다

설계서의 typed AuditMetadata는 좋다. 자유 Map 금지는 맞다.

그런데 UploadMetadata에 sourceFileName을 그대로 넣는 건 위험하다. 현재 UploadAuditLogger도 업로드 원본 파일명을 audit에 남기고 있다.

관리자가 파일명을 이렇게 올릴 수 있다.

홍길동_1차면접결과.xlsx
2026_신입공채_불합격자명단.xlsx

그러면 ActivityLog가 PII-free라는 전제가 깨진다.

수정안:

sourceFileName 원문 저장 금지.

record UploadMetadata(
    long stageId,
    String outcome,
    long rowCount,
    long changedCount,
    long unchangedCount,
    long errorCount,
    long staleCount,
    String sourceFileNameHash,
    String sourceFileExtension,
    long sourceFileSize,
    String contentHash
) implements AuditMetadata {}

원본 파일명은 SLF4J에도 남기지 않는 게 낫다. 최소한 ActivityLog에는 금지해라.

3. High — export fail-close 구현 시 temp file 누수 가능성이 있다

현재 export controller는 파일을 먼저 만들고, 그 다음 audit log를 남긴 뒤 response를 만든다.

현재 temp 파일 삭제는 ExcelExportResponseFactory의 StreamingResponseBody 안에서만 수행된다. 즉, response가 만들어지고 스트리밍이 시작돼야 삭제된다.

Phase 9에서 ActivityLog fail-close를 적용하면 audit insert 실패 시 파일은 생성됐는데 response factory까지 도달하지 못한다. 그러면 temp xlsx가 남는다.

수정안:

9b에서 export/PDF fail-close를 적용할 때 반드시 이런 형태가 필요하다.

ExcelExportFile file = applicationExportService.exportApplications(...);
try {
    activityLogService.recordEgressFailClose(...);
    return excelExportResponseFactory.toResponse(file);
} catch (RuntimeException e) {
    Files.deleteIfExists(file.path());
    throw e;
}

PDF도 동일한 구조가 필요하다.

4. High — RetentionPolicy 선택 규칙이 부족하다

설계서는 RetentionPolicy를 전역 기본 + 공고별 override로 둔다고 했다. retentionPeriod, baselineType, enabled, effectiveFrom, effectiveTo, jobPostingId를 둔다고 되어 있다.

하지만 실제로 가장 중요한 게 빠졌다.

정책이 여러 개면 어떤 것을 고르는가?
effectiveFrom/effectiveTo는 scan 시점 기준인가, anchor 기준인가?
전역 정책과 공고 override가 동시에 있으면 무조건 override 우선인가?
기간이 겹치는 정책은 DB에서 막을 것인가, 서비스에서 막을 것인가?

이게 없으면 dry-run 결과가 비결정적이 된다.

수정안:

9c 전에 아래 규칙을 박아야 한다.

1. jobPostingId override가 있으면 override 우선
2. override가 없으면 global default 사용
3. effectiveFrom/effectiveTo는 scanAt 기준으로 평가
4. 같은 jobPostingId에 effective period overlap 금지
5. global enabled policy도 동일 시점에 1개만 허용
6. 없으면 POLICY_NOT_FOUND로 SKIP
5. High — hiringEndedAt을 누가/언제 세팅하는지 없다

retention anchor를 JobPosting.hiringEndedAt으로 잡은 건 좋다. 암묵 closedAt fallback 금지도 맞다.

그런데 현재 JobPosting.close()는 status = CLOSED, closedAt = now만 세팅한다. hiringEndedAt은 없다.

즉, Phase 9에서 컬럼만 추가하면 모든 공고가 ANCHOR_NOT_FIXED로 skip될 수 있다.

수정안:

9c에 명시해야 한다.

POST /api/admin/job-postings/{id}/hiring-ended
또는
POST /api/admin/retention/job-postings/{id}/anchor

그리고 이 명령은 ActivityLog 대상이어야 한다.

actionType = RETENTION_ANCHOR_SET
targetType = JOB_POSTING

자동 세팅은 위험하다. “공고 마감”과 “채용 프로세스 종료”는 다르기 때문에 수동 확정 명령이 낫다.

6. High — onboarded/HR 이관 자동 hold 근거가 현재 도메인에 없다

설계서는 RetentionHold 자동 제외를 “최종 입사확정/onboarded/HR 이관 완료”로 잡았다.

그런데 현재 StageResultStatus에는 PENDING, PASSED, FAILED, ABSENT, WITHDRAWN, HOLD만 있다. HIRED, ONBOARDED, HR_TRANSFERRED 같은 상태가 없다.

따라서 “자동 제외”를 구현할 근거가 없다.

수정안:

Phase 9에서는 자동 hold를 빼고 이렇게 가는 게 안전하다.

Phase 9 RetentionHold = manual hold only
자동 onboarded hold = 후속 Phase

또는 신규 도메인을 추가해야 한다.

ApplicationHireStatus
- NOT_HIRED
- OFFERED
- ACCEPTED
- ONBOARDED
- HR_TRANSFERRED

이걸 Phase 9에 같이 넣으면 범위가 커진다. 지금은 manual hold가 낫다.

7. Medium — RetentionHold requestMatcher가 GET까지 막는다

ADR-0007은 GET retention 조회를 ROLE_RECRUIT_ADMIN까지 허용한다고 했다.

그런데 구현 지시 예시는 이렇다.

.requestMatchers("/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")

이건 method 구분이 없다. 따라서 GET도 ROLE_PRIVACY_ADMIN만 가능해진다.

수정안:

.requestMatchers(HttpMethod.POST, "/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")
.requestMatchers(HttpMethod.GET, "/api/admin/retention/holds/**")
    .hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
8. Medium — exact date 보존은 파기 관점에서 위험하다

PII inventory는 학력 입학/졸업일, 경력 시작/종료일을 KEEP_TOMBSTONE으로 두고 있다.

문서도 이게 quasi-identifier 위험이라는 걸 알고 “확인 필요”로 남겼다.

이 상태로 9d를 들어가면 안 된다. 학교 schoolId + 입학/졸업일 + 학점 조합은 충분히 재식별 가능성이 있다.

수정안:

정확한 날짜 보존 금지. 최소 bucket 처리.

education.admissionDate      → admissionYear 또는 null
education.graduationDate     → graduationYear 또는 null
career.startDate/endDate     → year-month bucket 또는 근속개월수
semesterGrade.schoolYear     → 보존 가능하나 schoolId와 결합 위험 검토

Phase 9 파기라면 “통계 편의”보다 “재식별 가능성 제거”가 우선이다.

9. Medium — storage health scan은 신규 상태와 null storagePath를 처리하도록 명시해야 한다

현재 health scan은 STORED, DELETED, MISSING만 대상으로 본다.

그리고 storagePath가 null이거나 invalid면 issue로 추가한다.

그런데 Phase 9에서는 BINARY_DELETED 최종 상태에서 storagePath를 null로 만들 계획이다.

그러면 scan 로직이 그대로면 BINARY_DELETED + storagePath null을 invalid로 볼 위험이 있다.

수정안:

상태별 scan 정책을 명시해야 한다.

STORED                  : storagePath required, file must exist
SOFT_DELETED            : storagePath required until cleanup, file should not exist
MISSING                 : storagePath may exist, file absent
BINARY_DELETE_PENDING   : storagePath required, retry target
BINARY_DELETE_FAILED    : storagePath required, retry target
BINARY_DELETED          : storagePath nullable, file must not exist
METADATA_ONLY           : storagePath null allowed
10. Low — PurgeBatch/PurgeJobItem을 append-only라고 부르는 건 애매하다

설계서에는 PurgeBatch/PurgeJobItem을 append-only 원장이라고 표현한다.

하지만 실제로 batch는 RUNNING → COMPLETED/PARTIAL_FAILED/FAILED 상태 전이가 필요하다. item도 pending/failed/retry를 업데이트할 가능성이 있다. 그러면 엄밀히 append-only가 아니다.

수정안:

용어를 분리해라.

ActivityLog = append-only
PurgeBatch/PurgeJobItem = mutable ledger/control table, delete 금지

정말 append-only로 갈 거면 상태 전이를 별도 event row로 쌓아야 한다. 그건 과하다. 지금은 “delete 금지 원장” 정도가 맞다.