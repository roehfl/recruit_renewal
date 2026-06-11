남은 Minor — Service 단위 테스트가 새 message 계약과 불일치

ClientEventLogServiceTest의 정상 수집 테스트가 아직 자유 문자열 message를 직접 넘긴다.

"Request failed with status code 500"

또 다른 테스트도 service를 직접 호출하면서 "submit failed 010-1234-5678 retry"를 저장 가능 경로로 검증한다.

문제는 컨트롤러에서는 @Valid로 막히지만, 서비스 자체는 message safe-code 검증을 하지 않는다. 실제 저장 시점에서는 safe() + 숫자 마스킹만 하고 그대로 저장한다.

현재 public API 경로만 보면 실질 위험은 낮다. 하지만 테스트가 새 정책과 반대로 “서비스는 자유 문자열을 저장할 수 있음”을 고정하고 있어서 나중에 내부 호출 경로가 생기면 정책이 깨진다.

수정 권장:

ClientEventLogServiceTest.정상_수집시_서버값으로_저장된다의 message를 API_REQUEST_FAILED로 교체.
message의_7자리_이상_연속_숫자는_마스킹된다 테스트는 제거하거나, “DTO 검증 우회 경로 대비 2차 방어” 목적이면 별도 sanitizer 단위로 분리.
더 안전하게는 서비스에도 동일 패턴 검증을 넣어라.
private static final Pattern SAFE_MESSAGE_CODE =
        Pattern.compile("^[A-Z][A-Z0-9_]{2,80}$");

private String safeMessage(String message) {
    String sanitized = safe(message, MAX_MESSAGE);
    if (sanitized == null) {
        return null;
    }
    if (!SAFE_MESSAGE_CODE.matcher(sanitized).matches()) {
        throw new InvalidClientEventLogException("message는 safe message code만 허용됩니다.");
    }
    return sanitized;
}

그리고 builder에는:

.message(safeMessage(request.message()))

이렇게 바꾸는 게 가장 깔끔하다.