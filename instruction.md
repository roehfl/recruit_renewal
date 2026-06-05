Medium 1 — Hold reason이 RECRUIT_ADMIN에게 원문 노출된다

현재 GET /api/admin/retention/**는 ROLE_RECRUIT_ADMIN도 접근 가능하다.
그리고 GET /admin/retention/holds는 RetentionHoldResponse를 그대로 반환한다.
그 응답에는 reason 원문이 포함된다.

문제는 hold 사유가 자유 텍스트라는 점이다. 요청 DTO도 reason을 문자열로 받는다.
운영자가 “개인 사유”, “소송”, “민원”, “질병”, “연락처”, “실명” 등을 넣을 가능성이 있다. ActivityLog에는 안 남기는 설계인데, read API에서 RECRUIT_ADMIN에게 그대로 주면 projection 설계가 깨진다.

둘 중 하나로 고쳐라.

권장안:
GET /admin/retention/holds 는 PRIVACY_ADMIN 전용으로 좁힌다.

또는:

대안:
RetentionHoldResponse.from(hold, includeSensitive)
- PRIVACY_ADMIN: reason 원문
- RECRUIT_ADMIN: "***"

9d execute에서 projection을 하겠다고 미뤄놨는데, hold reason은 이미 9c에서 원문 필드가 열린 상태다. 이건 9d까지 미루지 말고 지금 막는 게 맞다.

Medium 2 — Retention write 서비스가 actor blank를 방어하지 않는다

컨트롤러에서는 CurrentEmployeeService.getCurrentEmployeeActor()를 통해 actor를 검증하고 서비스에 넘긴다. 이 경로는 괜찮다.

하지만 서비스 자체는 actor를 검증하지 않는다. 예를 들어 RetentionPolicyService.recordPolicyAudit()는 auditRequestContextResolver.resolve(actor)만 호출한다. actor가 null/blank이면 resolver가 ANONYMOUS로 떨어질 수 있다.
RetentionHoldService, RetentionAnchorService, RetentionDryRunService도 동일 패턴이다.

Retention 정책 변경/hold/anchor/dry-run은 관리자 행위다. 서비스 직접 호출, 배치, 테스트, 미래 스케줄러 경로에서 actor가 비면 감사가 ANONYMOUS로 남을 수 있다.

공통 helper로 막아라.

private String requireActor(String actor) {
    if (actor == null || actor.isBlank()) {
        throw new InvalidRetentionPolicyException("Retention actor is required.");
    }
    return actor.trim();
}

서비스별 예외는 분리해도 된다. 핵심은 retention write/dry-run 수동 실행은 ANONYMOUS로 기록되면 안 된다는 점이다. 미래 scheduler는 별도 SYSTEM actor 정책으로 열어라.

Low 1 — active hold 중복은 race에 취약하다

RetentionHoldService.set()은 existsByApplicationIdAndReleasedAtIsNull()로 active hold 중복을 막는다.
하지만 DB unique 제약은 없다. 동시 요청 2건이 동시에 exists=false를 통과하면 active hold가 2개 생길 수 있다.

eligibility에서는 active hold applicationId를 Set으로 모으기 때문에 파기 대상 제외 자체는 유지된다.
그래도 운영 원장 관점에서는 중복 active hold가 생기는 게 좋지 않다.

MariaDB라 partial unique가 애매하면 후속으로 아래 중 하나를 고려해라.

- generated column active_flag + unique(application_id, active_flag)
- release 불가 중복 상태 감지/repair API
- set 시 pessimistic lock 또는 application row lock
Low 2 — PurgeBatch 목록 조회가 무제한이다

PurgeBatchReadService.getBatches()는 전체 batch를 전부 반환한다.
9c 초기는 괜찮지만, dry-run이 수동으로 여러 번 실행되면 관리 화면에서 무제한 목록이 된다.

9d/9e 전에 page/size를 붙이는 게 낫다.

GET /admin/retention/purge-batches?page=0&size=20
size max 100
Low 3 — service-level validation이 Bean Validation에 과하게 의존한다

RetentionPolicyRequest는 @NotNull, @Min으로 검증된다.
컨트롤러 경로는 @Valid라 괜찮다.

하지만 RetentionPolicyService.create/update()는 request.retentionPeriodDays()와 request.enabled()를 바로 사용한다. 서비스 직접 호출 시 null이면 NPE 또는 autounboxing 문제가 날 수 있다.

이건 치명적이지 않지만, retention은 배치/스케줄러로 확장될 가능성이 크다. 서비스 레벨에서도 최소 방어를 추가하는 게 낫다.