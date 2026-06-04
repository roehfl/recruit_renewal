남은 보완
Low — X-Request-Id의 control character 검증 범위를 조금 더 넓히는 게 좋다

현재 CorrelationIdFilter는 CR/LF만 거부한다.

탭이나 다른 제어문자가 들어오면 ActivityLogService.safe()에서는 저장 전에 정리되지만, 필터는 그 값을 응답 헤더로 echo한다. 응답 헤더 값에 제어문자가 있으면 WAS에 따라 reject될 수 있다.

수정 권장:

if (trimmed.length() > MAX_CORRELATION_ID || trimmed.chars().anyMatch(Character::isISOControl)) {
    return UUID.randomUUID().toString();
}

이건 blocker는 아니다. 9b 전에 같이 처리하면 된다.

Low — ActorType.EMPLOYEE인데 actorId가 null인 케이스는 아직 허용된다

현재 필수 검증은 occurredAt, actorType, actionType, actionResult, targetType까지만 한다.

감사 품질 관점에서는 9b 계측 시점에 아래 규칙을 추가하는 게 낫다.

actorType = EMPLOYEE 또는 APPLICANT 이면 actorId 필수
actorType = SYSTEM 또는 ANONYMOUS 이면 actorId null 허용

다만 9a foundation 단계에서는 허용 가능한 수준이다.