보완 필요
1. Major — request-derived 문자열 길이 제한이 없다

이게 가장 큰 문제다.

ActivityLog 컬럼은 길이 제한이 있다.

actorId             length = 100
actorRoleSnapshot   length = 255
correlationId       length = 100
ipAddress           length = 64
userAgent           length = 512
reasonMessage       length = 1000

실제 엔티티도 이렇게 되어 있다.

그런데 CorrelationIdFilter는 외부에서 들어온 X-Request-Id를 trim()만 해서 그대로 쓴다. 길이 제한도 없고 문자 검증도 없다.

문제는 9b에서 export/PDF/download fail-close가 붙으면, 외부 사용자가 긴 X-Request-Id나 긴 User-Agent를 보내는 것만으로 audit insert가 실패할 수 있다는 점이다. 그러면 의도치 않게 export가 막히거나, 성공 변경 audit이면 비즈니스 트랜잭션까지 rollback될 수 있다.

수정 지시:

9b 전에 9a 보완으로 처리해라.

private static final int MAX_CORRELATION_ID = 100;
private static final int MAX_USER_AGENT = 512;
private static final int MAX_IP_ADDRESS = 64;
private static final int MAX_ACTOR_ID = 100;
private static final int MAX_ROLE_SNAPSHOT = 255;
private static final int MAX_REASON_MESSAGE = 1000;

그리고 최소한 아래는 서비스 저장 직전에 normalize 해야 한다.

private String safe(String value, int max) {
    if (value == null || value.isBlank()) {
        return null;
    }
    String sanitized = value.replaceAll("[\\r\\n\\t]", " ").trim();
    return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
}

CorrelationIdFilter는 더 엄격하게 가는 게 낫다.

private String resolve(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
        return UUID.randomUUID().toString();
    }

    String trimmed = headerValue.trim();
    if (trimmed.length() > 100 || trimmed.contains("\r") || trimmed.contains("\n")) {
        return UUID.randomUUID().toString();
    }

    return trimmed;
}

테스트도 추가해라.

- 긴 X-Request-Id는 UUID로 대체
- 긴 userAgent는 512자로 truncate
- CR/LF 포함 reasonMessage는 sanitize
2. Medium — 운영 DDL 스크립트가 없다

9a는 activity_log라는 신규 영속 테이블을 만든다. 그런데 migration framework가 없고, 운영 DB는 수동 DDL이 필요하다고 문서에 적혀 있다. 구현 문서도 “MariaDB는 activity_log 수동 DDL 필요”라고 남겼다.

현재 application.yaml도 Hibernate ddl-auto는 기본 update지만, 운영에서는 validate/none + 관리형 DDL을 권장한다고 적혀 있다.

즉, 운영 반영 기준으로는 아직 빠졌다.

수정 지시:

다음 파일을 추가해라.

docs/codex/ops/phase-09a-activity-log-ddl.sql

최소 포함:

create table activity_log (
    id bigint not null auto_increment primary key,
    occurred_at datetime not null,
    actor_type varchar(20) not null,
    actor_id varchar(100),
    actor_role_snapshot varchar(255),
    action_type varchar(50) not null,
    action_result varchar(20) not null,
    target_type varchar(40) not null,
    target_id varchar(100),
    job_posting_id bigint,
    application_id bigint,
    applicant_ref_hash varchar(128),
    reason_code varchar(40),
    reason_message varchar(1000),
    correlation_id varchar(100),
    trace_id varchar(100),
    ip_address varchar(64),
    user_agent varchar(512),
    metadata_json longtext
);

create index idx_activity_log_occurred_at on activity_log (occurred_at);
create index idx_activity_log_actor_id on activity_log (actor_id);
create index idx_activity_log_target on activity_log (target_type, target_id);
create index idx_activity_log_application_id on activity_log (application_id);
create index idx_activity_log_job_posting_id on activity_log (job_posting_id);
create index idx_activity_log_action_type on activity_log (action_type);

그리고 아래 finding까지 반영하면 action_result 인덱스도 추가해라.

3. Medium — 9b read API 검색 조건 대비 index가 부족하다

ActivityLog 현재 index는 아래 6개다.

occurred_at
actor_id
target_type,target_id
application_id
job_posting_id
action_type

코드도 그렇게 되어 있다.

그런데 설계된 audit read API는 actionResult도 검색 조건으로 받는다.

대량 audit table에서 actionResult=FAILURE나 DENIED, CONFLICT 검색은 꽤 자주 쓸 가능성이 높다. 지금은 action_result 단독 인덱스가 없다.

수정 지시:

둘 중 하나 추가해라.

@Index(name = "idx_activity_log_action_result", columnList = "action_result")

또는 더 실전적인 조합:

@Index(name = "idx_activity_log_action_result_occurred", columnList = "action_result,occurred_at")

개인적으로는 9b read API가 기간 검색을 거의 항상 같이 쓸 가능성이 높으니 action_result, occurred_at이 낫다.

4. Low — ADR status 전환 누락

9a가 구현 완료됐는데 ADR-0006은 아직 proposed다. 문서상 “Phase 09 구현(9a/9b) 착수 시 accepted 전환”이라고 되어 있다.

ADR-0007도 아직 proposed다. 다만 이건 권한 분리 구현이 9b/9c에서 본격화되므로 9a에서 반드시 accepted로 바꿔야 하는지는 애매하다.

수정 지시:

최소한 ADR-0006은 지금 accepted로 바꿔라.

docs/adr/0006-audit-transaction-policy.md
Status: accepted (2026-06-04, Phase 09a implemented)

ADR-0007은 둘 중 하나로 정해라.

선택 A: 9b SecurityConfig 구현 전까지 proposed 유지
선택 B: 9a에서 정책 확정으로 accepted 전환

히스토리에는 ADR-0006/0007을 9a 착수 시 accepted 전환한다고 되어 있으므로, 문서 정합성상 둘 다 accepted가 더 깔끔하다.

5. Low — 운영 profile 이름이 prod가 아니면 fallback secret을 쓴다

AuditConfig는 active profile에 "prod"가 있을 때만 secret 누락을 기동 실패로 처리한다. 그 외에는 fallback secret을 쓴다.

만약 운영 profile이 prd, real, production 같은 이름이면 fallback secret으로 떠버린다. 이건 운영 사고로 이어질 수 있다.

수정 선택지:

가장 안전한 방식은 profile 기반이 아니라 property 기반이다.

audit:
  hmac-secret: ${AUDIT_HMAC_SECRET:}
  allow-fallback-secret: ${AUDIT_ALLOW_FALLBACK_SECRET:false}

그리고 local/test에서만 명시적으로 true.

if (secretBlank && !allowFallbackSecret) {
    throw new IllegalStateException(...);
}

지금 방식도 개발 편의성은 있지만, 운영 profile 이름이 확정되지 않았다면 위험하다.