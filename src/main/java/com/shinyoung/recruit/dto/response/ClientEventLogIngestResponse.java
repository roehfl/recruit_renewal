package com.shinyoung.recruit.dto.response;

/** 수집 결과(Phase 09f). FE telemetry는 fire-and-forget이라 이 값을 사용하지 않는다 — 운영 진단/테스트용. */
public record ClientEventLogIngestResponse(
        boolean accepted,
        boolean duplicate,
        Long id
) {

    public static ClientEventLogIngestResponse ofAccepted(Long id) {
        return new ClientEventLogIngestResponse(true, false, id);
    }

    public static ClientEventLogIngestResponse ofDuplicate() {
        return new ClientEventLogIngestResponse(false, true, null);
    }

    public static ClientEventLogIngestResponse ofDisabled() {
        return new ClientEventLogIngestResponse(false, false, null);
    }
}
