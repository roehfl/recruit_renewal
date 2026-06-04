반드시 보완할 점
1. Blocker — PII 필드 인벤토리가 아직 너무 약하다

설계서에 “섹션/answers PII 컬럼은 실제 엔티티/DDL 확인 후 확정”이라고 되어 있는데, 이건 “남은 이슈” 수준이 아니라 9d 착수 전 선행 산출물이어야 한다.

현재 코드 기준으로 바로 문제가 보인다.

JobApplication.applicantNameSnapshot은 nullable = false다. 파기 시 null 처리하려면 DDL 변경이 필요하거나, PII-free placeholder 정책이 필요하다.

Applicant는 email, userName, password, phoneNumber, ci, ciHash를 들고 있고, 특히 ciHash는 nullable = false, unique = true다. 단순 null 익명화가 불가능하다.

ApplicationAttachment도 originalFileName, storedFileName, storagePath, contentType, fileSize 등이 nullable = false로 잡혀 있다. 설계서의 “originalFilename 제거, storagePath 최종 purge 후 null”을 그대로 구현하려면 DDL 변경이 필요하다.

ApplicationEducation.schoolName도 nullable = false다. 학교명은 재식별 가능성이 있으므로 소거 대상인데, null 처리할 수 없다.

ApplicationCareer.companyName, startDate, currentlyEmployed도 재식별성이 높은데 nullable = false가 섞여 있다.

ApplicationCertificate.certificateName, issuingOrganization, acquiredDate도 nullable = false다. 자격 통계 때문에 일부 tombstone을 남기고 싶은 욕심이 생길 수 있는데, 개인 단위 상세 자격 이력은 재식별 리스크가 있다.

보완 지시:

9d 전에 반드시 phase-09-pii-field-inventory.md를 만들고, 모든 지원서 관련 엔티티 필드를 아래처럼 분류해야 한다.

KEEP_TOMBSTONE     : 통계/감사 연결용 비식별 값
NULLIFY            : nullable 변경 후 null
PLACEHOLDER        : NOT NULL 유지 필요 시 "__PURGED__" 등 비식별 치환
HASH_ONLY          : 원문 제거 후 HMAC/hash만 보존
DELETE_ROW         : row 자체 삭제 가능
RETAIN_UNTIL_REF0  : Applicant 공통 PII처럼 ref-count 이후 처리

이거 없으면 9d 구현은 높은 확률로 “파기했다고 표시했지만 실제 PII가 남는” 상태가 된다.

2. High — terminal 판정이 현재 도메인과 정확히 안 맞는다

설계서는 terminal을 WITHDRAWN 또는 “최종 stage StageResult 확정”으로 정의했다.

문제는 현재 코드상 “확정”이라는 독립 필드가 없다.
Stage에는 finalStage, status, resultAnnouncementDateTime이 있고, StageResult에는 resultStatus, decidedAt, decidedBy, version이 있다.

즉 구현자가 마음대로 해석할 여지가 있다.

예를 들어 아래 중 무엇이 terminal인가?

finalStage = true AND Stage.status = RESULT_ANNOUNCED
finalStage = true AND Stage.status = CLOSED
finalStage = true AND StageResult.resultStatus != PENDING
finalStage = true AND decidedAt != null
공고 JobPosting.status = CLOSED 까지 필요?

이걸 9c에서 확정하지 않으면 dry-run 결과가 흔들린다.

보완 지시:

9c 설계/구현 지시문에 terminal query를 명시해야 한다.

추천 기준은 보수적으로 이거다.

terminal =
  JobApplication.status == WITHDRAWN
  OR
  (
    finalStage row exists
    AND finalStage.status IN (RESULT_ANNOUNCED, CLOSED)
    AND StageResult exists for application + finalStage
    AND StageResult.resultStatus != PENDING
    AND StageResult.decidedAt IS NOT NULL
  )

그리고 finalStage가 없거나 여러 개면 APPLICATION_NOT_TERMINAL 또는 INVALID_STAGE_CONFIGURATION로 SKIP하는 게 안전하다.

3. High — metadataJson allowlist가 아직 추상적이다

설계서에는 metadataJson을 actionType별 allowlist + PII-free 검증으로 제한한다고 되어 있다. 방향은 맞다.

하지만 구현 지시문 수준에서는 이 표현만으로 부족하다.
특히 기존 export audit은 filtersSafeJson, filtersHash, rowCount, fileName, clientIp, userAgent 등을 남긴다.

여기서 자유 Map<String, Object>를 허용하면 결국 누군가 applicantName, phoneNumber, email 같은 값을 metadata에 넣을 가능성이 생긴다.

보완 지시:

9b 전에 AuditMetadata를 자유 Map이 아니라 action별 typed record로 고정해야 한다.

예시:

public sealed interface AuditMetadata permits
        ExportMetadata,
        PdfMetadata,
        StageResultChangeMetadata,
        PurgeBatchMetadata {
}

public record ExportMetadata(
        String datasetType,
        String filtersHash,
        Long rowCount
) implements AuditMetadata {
}

그리고 ObjectMapper.writeValueAsString(metadata)는 ActivityLogService 내부에서만 수행하게 해야 한다.
서비스 호출부에서 raw JSON 문자열이나 Map을 넘기게 하면 설계가 바로 무너진다.

4. Medium — PhysicalFileStatus.DELETED와 신규 바이너리 삭제 상태가 충돌할 수 있다

현재 PhysicalFileStatus는 METADATA_ONLY, STORED, MISSING, DELETED 네 개뿐이다.

그런데 현재 ApplicationAttachment.markDeleted()는 soft delete 성격의 삭제 처리에서 physicalFileStatus = DELETED로 바꾼다.

Phase 09 설계는 여기에 BINARY_DELETE_PENDING, BINARY_DELETED, BINARY_DELETE_FAILED를 추가하려고 한다.

문제는 기존 DELETED가 진짜 물리 파일 삭제인지, 논리 삭제인지 의미가 애매해진다는 점이다.

보완 지시:

9d-2 전에 상태 의미를 정리해야 한다.

추천은 이거다.

METADATA_ONLY        : 파일 미업로드 metadata만 존재
STORED               : 파일 존재
MISSING              : DB는 있으나 파일 없음
SOFT_DELETED         : 사용자/관리자 삭제 처리됨, 파일 처리 정책은 별도
BINARY_DELETE_PENDING
BINARY_DELETED
BINARY_DELETE_FAILED

기존 DELETED를 그대로 쓰지 말고, 가능하면 SOFT_DELETED로 의미를 분리하는 게 낫다.
지금 이름 그대로 가면 purge saga에서 상태 해석이 꼬인다.

5. Medium — requestMatcher는 path뿐 아니라 HTTP method까지 명시해야 한다

ADR-0007은 narrow matcher를 broad /api/admin/**보다 먼저 배치해야 한다고 잘 적었다.

그런데 API 설계상 같은 /api/admin/retention/policies 계열에서 GET은 RECRUIT_ADMIN, POST/PUT/DELETE는 PRIVACY_ADMIN이다.

그러면 path matcher만으로는 부족하다. method까지 분기해야 한다.

보완 지시:

SecurityConfig 구현 지시문에 아래 수준으로 박아야 한다.

.requestMatchers(HttpMethod.POST, "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
.requestMatchers(HttpMethod.PUT, "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")

.requestMatchers(HttpMethod.POST, "/api/admin/retention/purge-batches/execute").hasAuthority("ROLE_PRIVACY_ADMIN")

.requestMatchers(HttpMethod.GET, "/api/admin/retention/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")
.requestMatchers(HttpMethod.GET, "/api/admin/audit/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN", "ROLE_PRIVACY_ADMIN")

그리고 이 matcher들은 반드시 기존 broad /api/admin/**보다 위에 있어야 한다.

6. Medium — ActivityLog 자체 lifecycle 제외는 괜찮지만 조회 가드는 필요하다

설계서가 ActivityLog 자체 보존/회전/아카이빙을 Phase 09 범위 밖으로 뺀 건 이해된다.

다만 ActivityLog에는 actorId, ipAddress, userAgent, applicationId, applicantRefHash가 들어간다. 설계서도 “완전 PII-free 테이블은 아니다”라고 정확히 적었다.

그래서 lifecycle은 후속으로 빼더라도, 9b read API에는 최소한 아래 가드가 필요하다.

page size max
occurredAt range max
default recent range
ip/userAgent 마스킹 테스트
metadataJson PII 금지 테스트
ROLE_RECRUIT_ADMIN vs ROLE_PRIVACY_ADMIN projection 분리 테스트

이건 lifecycle policy가 아니라 read API 안전장치라서 9b 범위에 들어가야 한다.

7. Low — ADR status는 9a 착수 전에 accepted로 전환

현재 ADR 0005/0006/0007은 proposed 상태다.

히스토리에도 구현 착수 시 accepted 전환이라고 되어 있다.

이건 큰 문제는 아니지만, 9a 구현 지시문에는 반드시 포함해라.

ADR-0005/0006/0007 status를 proposed → accepted로 변경한다.
단, ADR-0005는 9d 전 PII field inventory 확정 전까지 accepted-with-implementation-gate로 명시해도 된다.