1. Blocker — public API에서 source=ADMIN_WEB을 그대로 받으면 안 됨

현재 설계는 ClientEventSource에 APPLICANT_WEB, ADMIN_WEB을 모두 정의하고, POST /api/client-events는 permitAll로 둔다.
그런데 request DTO는 source를 클라이언트 body에서 받는다.

이 조합이면 미인증 사용자가 아래처럼 보낼 수 있다.

{
  "source": "ADMIN_WEB",
  "eventType": "API_ERROR",
  "severity": "ERROR",
  "clientSessionId": "x",
  "clientEventId": "y"
}

즉, public endpoint에서 ADMIN_WEB 진단 로그를 위조할 수 있다.

수정안

09f-1에서는 둘 중 하나로 고정해라.

POST /api/client-events는 09f-1에서 APPLICANT_WEB만 허용한다.
request.source가 ADMIN_WEB이면 400으로 거부한다.
ADMIN_WEB 수집은 향후 별도 admin-authenticated endpoint에서만 허용한다.

더 안전하게 가려면 source를 request body에서 빼고 서버에서 무조건 APPLICANT_WEB로 세팅하는 게 낫다.

테스트도 추가해야 한다.

anonymous POST source=ADMIN_WEB -> 400
authenticated applicant POST source=ADMIN_WEB -> 400
source omitted 정책을 선택했다면 server-side APPLICANT_WEB 세팅 검증
2. Blocker — metadata 정책이 “allowlist”라고 되어 있지만 실제로는 denylist에 가까움

BE 문서는 ClientEventMetadataSanitizer를 allowlist 정책이라고 설명하지만, 실제 고정된 내용은 key 개수/길이, value 타입/길이, JSON 길이, 그리고 금지 key 패턴이다.
원본 문서도 이벤트별 key를 “허용 예”로만 제시한다.

이러면 이런 key들이 통과할 수 있다.

mobile
tel
residentNo
ssn
schoolName
companyName
zipCode
kakaoId
guardianContact

금지어 목록만으로 PII를 막는 건 불안하다. 특히 이 로그는 지원자 화면에서 발생하기 때문에 PII가 섞일 확률이 높다.

수정안

문서에 이렇게 고정해라.

metadata는 eventType별 exact allowlist만 허용한다.
allowlist에 없는 key는 400으로 거부한다.
금지 key 패턴은 allowlist 통과 후에도 적용되는 2차 방어선이다.

예:

API_ERROR:
- durationMs
- retryable
- axiosCode

API_TIMEOUT:
- durationMs
- timeoutMs

JS_ERROR:
- file
- line
- column

ATTACHMENT_UPLOAD_FAILED:
- fileSize
- fileExtension
- uploadStep

테스트도 아래를 추가해라.

metadata.unknownKey -> 400
metadata.mobile -> 400
metadata.schoolName -> 400
metadata.companyName -> 400
metadata.fileName -> 400
eventType=API_ERROR에서 fileExtension -> 400
eventType=ATTACHMENT_UPLOAD_FAILED에서 axiosCode -> 400
3. Blocker — message, stackSummary, metadataJson을 양 권한에 노출한다는 전제가 약함

설계는 metadataJson, message, stackSummary는 수집 시점에 PII가 차단/sanitize되었으므로 ROLE_RECRUIT_ADMIN과 ROLE_PRIVACY_ADMIN 모두에게 노출한다고 한다.

그런데 현재 sanitize 규칙은 제어문자 제거, 컬럼 길이 truncate, query string 제거 수준이다.
이건 XSS/로그 인젝션 완화에는 도움이 되지만, PII 제거와는 다르다.

예를 들어 FE 코드에서 실수로 이런 message를 보내면 truncate만 되고 저장될 수 있다.

홍길동 지원자 저장 실패
010-1234-5678 validation failed
test@example.com submit error

수정안

둘 중 하나를 택해야 한다.

권장안 A — message를 자유 문자열로 받지 않는다
message는 자유 입력이 아니라 FE에서 정의한 safe message code 또는 고정 문구만 허용한다.
상세 원인은 errorCode, eventType, httpStatus, apiPath, relatedCorrelationId로 추적한다.
대안 B — RECRUIT_ADMIN에는 민감 가능 필드를 마스킹한다
ROLE_RECRUIT_ADMIN:
- message masked or omitted
- stackSummary masked or omitted
- metadataJson은 allowlist key/value만 노출

ROLE_PRIVACY_ADMIN:
- 원문 접근 가능

개인적으로는 A + metadata exact allowlist가 맞다.

4. Major — duplicate race 처리는 saveAndFlush 없으면 의도대로 안 될 수 있음

설계는 existsByClientSessionIdAndClientEventId 선확인 후 insert하고, 동시 요청 race로 DataIntegrityViolationException이 나면 catch해서 duplicate=true 성공 응답을 준다고 되어 있다.

문제는 JPA에서 repository.save()만 하고 메서드가 끝나면 unique violation이 transaction commit 시점에 터질 수 있다는 점이다. 그러면 service 내부 catch를 못 타고, 기존 GlobalExceptionHandler의 DataIntegrityViolationException -> 409 매핑으로 빠질 수 있다. 현재 전역 핸들러는 DB 제약 위반을 409로 응답한다.

수정안

문서에 아래를 명시해라.

중복 race를 duplicate=true로 흡수하려면 insert는 saveAndFlush 또는 save 후 repository.flush/entityManager.flush로 즉시 flush한다.
DataIntegrityViolationException catch 범위 안에서 flush가 발생해야 한다.

테스트도 그냥 exists 테스트가 아니라 실제 unique 충돌을 만들어야 한다.

same clientSessionId/clientEventId insert race or forced duplicate insert
-> response success=true, accepted=false, duplicate=true
-> GlobalExceptionHandler 409로 새지 않음
5. Major — rate limit key가 client-controlled 값에 의존함

설계의 rate limit key는 clientSessionId + ":" + ip다.
그런데 clientSessionId는 클라이언트가 보내는 body 값이다.

공격자나 오동작 클라이언트가 clientSessionId만 계속 바꾸면 같은 IP에서도 rate limit을 우회할 수 있다.

수정안

rate limit을 2단으로 잡아라.

1차: ip 기준 global limit
2차: ip + clientSessionId 기준 session limit
3차: authenticated 상태면 principalHash 기준 optional limit

예:

ip: 1분 300건
ip + clientSessionId: 1분 60건
principalHash: 1분 120건

그리고 clientSessionId 형식도 UUID 또는 안전한 opaque id로 제한해라. 최소한 blank/random long string으로 map key 폭증하는 건 막아야 한다.

clientSessionId max length 80 + allowed pattern
rate limiter map max size 초과 시 신규 key 제한 또는 oldest eviction
6. Major — 새 예외의 GlobalExceptionHandler 매핑을 문서에 넣어야 함

BE 문서는 InvalidClientEventLogException, ClientEventRateLimitExceededException, ClientEventLogNotFoundException, InvalidClientEventQueryException을 추가한다고 되어 있다.
그리고 테스트 전략도 400, 429, 401/403을 검증한다고 되어 있다.

그런데 변경 파일 목록에 GlobalExceptionHandler.java가 없다. 현재 프로젝트는 도메인 예외를 전역 핸들러에서 명시 매핑하는 패턴을 쓰고 있다.

수정안

09f-1/3 변경 파일에 추가해라.

exception/GlobalExceptionHandler.java 변경
- InvalidClientEventLogException -> 400
- ClientEventRateLimitExceededException -> 429
- InvalidClientEventQueryException -> 400
- ClientEventLogNotFoundException -> 404

테스트:

PII metadata key -> 400
rate limit exceeded -> 429
client event not found -> 404
invalid range/page -> 400
보정하면 좋은 부분

POST /api/client-events는 public write endpoint다. 최소한 controller에 아래를 고정하는 게 좋다.

@PostMapping(
    value = "/client-events",
    consumes = MediaType.APPLICATION_JSON_VALUE
)

그리고 @Valid @RequestBody를 명시해라. 현재 DTO 검증 자체는 설계되어 있지만, JSON only 계약을 고정해두면 이상한 form/text 요청을 줄일 수 있다. request DTO의 필수/길이 검증은 이미 방향이 맞다.

Retention은 방향은 맞지만, deleteByReceivedAtBefore(...)를 “bulk delete”로 기대한다면 repository에 명시적 @Modifying @Query를 쓰라고 문서에 적는 게 좋다. 현재 설계는 bulk delete라고 표현되어 있다