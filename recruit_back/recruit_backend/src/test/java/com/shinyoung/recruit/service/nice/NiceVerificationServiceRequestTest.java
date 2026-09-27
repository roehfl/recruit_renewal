package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;
import com.shinyoung.recruit.exception.NiceVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NiceVerificationServiceRequestTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private NiceVerificationStore store;
    private NicePlaindataCodec codec;
    private MockNiceClient client;
    private NiceVerificationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        store = new NiceVerificationStore();
        codec = new NicePlaindataCodec();
        client = new MockNiceClient(clock, codec);

        NiceProperties properties = new NiceProperties();
        properties.setSiteCode("SITECODE");
        properties.setSitePassword("SITEPASS");
        properties.setReturnUrl("https://example.test/api/auth/nice/callback");
        properties.setErrorUrl("https://example.test/api/auth/nice/callback/error");

        service = new NiceVerificationService(client, codec, store, properties, clock);
    }

    private Map<String, String> plaindataOf(String encodeData) {
        return codec.decode(client.decode(encodeData).plaindata());
    }

    @Test
    void requestStoresPendingRecordBoundToSession() {
        String encodeData = service.request(NiceVerificationPurpose.SIGNUP, "sess-1");

        assertNotNull(encodeData);
        NiceVerificationRecord record = store.find(plaindataOf(encodeData).get("REQ_SEQ")).orElseThrow();

        assertEquals(NiceVerificationStatus.PENDING, record.status());
        assertEquals("sess-1", record.sessionId());
        assertEquals(NiceVerificationPurpose.SIGNUP, record.purpose());
        assertEquals(NOW, record.issuedAt());
    }

    @Test
    void requestPlaindataCarriesSiteCodeAndReturnUrls() {
        Map<String, String> plaindata =
                plaindataOf(service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));

        assertEquals("SITECODE", plaindata.get("SITECODE"));
        assertEquals("https://example.test/api/auth/nice/callback", plaindata.get("RTN_URL"));
        assertEquals("https://example.test/api/auth/nice/callback/error", plaindata.get("ERR_URL"));
    }

    @Test
    void requestPlaindataUsesLegacyKeyOrder() {
        // 레거시 운영 시스템에서 확인한 순서다(2026-09-21). NICE 규격이므로 바꾸면 실패한다.
        Map<String, String> plaindata =
                plaindataOf(service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));

        assertEquals(
                List.of("REQ_SEQ", "SITECODE", "AUTH_TYPE", "RTN_URL", "ERR_URL", "POPUP_GUBUN", "CUSTOMIZE"),
                List.copyOf(plaindata.keySet()));
    }

    @Test
    void authTypeAndCustomizeAreEmptyButPresent() {
        // 빈 값과 '키를 빼는 것'은 다르다. 평문에 9:AUTH_TYPE0: 으로 나가야 한다.
        Map<String, String> plaindata =
                plaindataOf(service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));

        assertEquals("", plaindata.get("AUTH_TYPE"));
        assertEquals("", plaindata.get("CUSTOMIZE"));
        assertEquals("N", plaindata.get("POPUP_GUBUN"));
    }

    @Test
    void eachRequestGetsDistinctReqSeq() {
        String first = plaindataOf(service.request(NiceVerificationPurpose.SIGNUP, "sess-1")).get("REQ_SEQ");
        String second = plaindataOf(service.request(NiceVerificationPurpose.SIGNUP, "sess-1")).get("REQ_SEQ");

        assertNotEquals(first, second);
    }

    /* 비로그인 공개 경로라 무한히 부를 수 있다. 보관소가 가득 차면 만료분을 정리해 보고, 그래도 가득이면 거부한다. */
    @Test
    void requestIsRejectedWhenStoreIsFullOfLiveRecords() {
        for (int i = 0; i < NiceVerificationStore.MAX_RECORDS; i++) {
            store.save(NiceVerificationRecord.pending("live-" + i, NiceVerificationPurpose.SIGNUP, "s", NOW));
        }

        assertThrows(NiceVerificationException.class, () -> service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));
    }

    @Test
    void requestPurgesExpiredRecordsWhenStoreIsFull() {
        Instant old = NOW.minus(Duration.ofHours(1));
        for (int i = 0; i < NiceVerificationStore.MAX_RECORDS; i++) {
            store.save(NiceVerificationRecord.pending("old-" + i, NiceVerificationPurpose.SIGNUP, "s", old));
        }

        assertNotNull(service.request(NiceVerificationPurpose.SIGNUP, "sess-1"));
        assertEquals(1, store.size());
    }
}
