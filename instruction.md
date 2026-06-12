Major 2. 문서 예시가 최신 백엔드 계약과 충돌한다

백엔드 DTO는 message를 ^[A-Z][A-Z0-9_]{2,80}$ safe code만 허용한다. 즉 API_REQUEST_FAILED, SESSION_EXPIRED 같은 값만 가능하다.

그런데 설계 문서의 request example에는 아직 "Request failed with status code 500" 같은 자유문장이 들어 있다. 이 값은 현재 백엔드에서 400으로 거부된다.

이건 구현보다 문서/프론트 지시문 문제지만, 프론트 구현자가 이 예시를 보고 그대로 보내면 바로 실패한다.

수정 필요:

"message": "API_REQUEST_FAILED"

그리고 phase-09f-client-event-log-design.md의 request example도 최신 계약에 맞춰 수정해라.

Medium 2. retention-days 값 검증이 없다

ClientEventLogCleanupService는 retentionDays를 설정값으로 받아 now - retentionDays를 threshold로 계산한다.

문제는 retentionDays <= 0 같은 값에 대한 방어가 없다. 실수로 CLIENT_EVENT_LOG_RETENTION_DAYS=0이면 거의 모든 과거 로그가 삭제될 수 있고, 음수면 threshold가 미래가 되어 더 위험하다.

권장:

if (retentionDays < 1 || retentionDays > 365) {
    throw new IllegalArgumentException("client-event-log.retention-days must be between 1 and 365.");
}

운영 설정값이라서 이런 guard는 넣는 게 낫다.


Low. 수동 cleanup과 조회 controller가 섞였다

AdminClientEventLogController 하나에 조회 API와 POST /admin/client-events/cleanup이 같이 있다.

보안 matcher가 분리되어 있어서 기능상 문제는 아니다. 다만 cleanup은 write 성격이므로 장기적으로는 AdminClientEventRetentionController 같은 별도 컨트롤러로 빼도 좋다. 지금 단계에서는 수정 필수는 아니다.