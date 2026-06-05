Medium 1 — read API range 상한 계산이 느슨하다

AuditActivityReadService.validateRange()에서 Duration.between(from, to).toDays() > 90으로 검사하고 있다.

toDays()는 소수 일수를 버린다. 그래서 90일 23시간 59분도 90으로 계산되어 통과한다. 문서상 요구사항은 “range≤90일”이다.

아래처럼 고쳐라.

private void validateRange(LocalDateTime from, LocalDateTime to) {
    if (from.isAfter(to)) {
        throw new InvalidAuditQueryException("occurredAt 검색 범위가 올바르지 않습니다(from > to).");
    }
    if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
        throw new InvalidAuditQueryException("occurredAt 검색 범위는 최대 " + MAX_RANGE_DAYS + "일입니다.");
    }
}

테스트도 90일 + 1분 케이스를 추가해라.

Medium 2 — export audit 보조 로그에 raw status가 남을 수 있다

AdminExportController.export()는 status 원문을 그대로 logApplicationsExport()로 넘긴다.
ApplicationExportService는 status를 trim/uppercase로 파싱해서 검증하지만, audit logger에는 여전히 컨트롤러에서 받은 raw string이 들어간다.

ExportAuditLogger.toJson()은 ObjectMapper가 아니라 수동 문자열 조합이고, quote만 '로 바꾼다. CR/LF, backslash, control character는 처리하지 않는다. 그 값은 metadata의 filtersSafeJson뿐 아니라 SLF4J 보조 로그에도 찍힌다.

실제 export 검증은 통과하더라도 status=" SUBMITTED\nfake=1" 같은 입력은 trim 후 유효해질 수 있고, 보조 로그에는 원문 형태가 남을 수 있다.

수정 방향은 둘 중 하나다.

// 권장: export 서비스에서 파싱된 canonical status를 audit filter로 전달
filters.put("status", parsedStatus == null ? null : parsedStatus.name());

또는 ExportAuditLogger에서 필터값을 안전하게 normalize하고 ObjectMapper로 JSON을 만들어라. 수동 JSON 조합은 audit 코드에는 부적합하다.

Low 1 — Stage announce/close audit actor 검증이 약하다

StageService.announce() / close()는 actor 파라미터 없이 AuditRequestContextResolver.resolve()만 사용한다.

실요청에서는 SecurityContext에 CustomUserDetails가 있으므로 EMPLOYEE로 기록될 가능성이 높다. 하지만 직접 서비스 호출이나 비표준 principal이면 ANONYMOUS로 기록된다. 실제 신규 테스트도 직접 서비스 호출이라 announce/close는 ANONYMOUS라고 주석으로 인정하고 있다.

admin 핵심 변경 audit이면 ANONYMOUS 허용은 좋지 않다. 가능하면 StageController에서 @AuthenticationPrincipal을 받고 CurrentEmployeeService.getCurrentEmployeeActor()를 호출한 뒤 서비스에 actor를 넘겨라. 최소한 StageAuditInstrumentationTest에 “실제 컨트롤러 + springSecurity + CustomUserDetails” 케이스를 하나 추가해 EMPLOYEE actor 기록을 검증해라.

Low 2 — 9b 테스트 결과가 “완전 성공”은 아니다

문서상 scoped 136개 중 134 passed / 2 failed다. 실패 원인은 기존 날짜 의존 fixture라고 설명되어 있고, 9b 계측 경로는 별도 테스트로 실증했다고 되어 있다.

이 설명은 수용 가능하다. 다만 audit phase는 회귀 신뢰도가 중요하므로, 날짜 의존 실패를 계속 방치하면 이후 phase마다 “기존 실패”가 누적된다. 9c 전에 StageControllerTest의 하드코딩 접수기간은 고정 Clock 또는 동적 기간으로 정리하는 게 맞다.