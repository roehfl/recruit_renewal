package com.shinyoung.recruit.config;

import com.shinyoung.recruit.service.nice.MockNiceClient;
import com.shinyoung.recruit.service.nice.NiceClient;
import com.shinyoung.recruit.service.nice.NicePlaindataCodec;
import com.shinyoung.recruit.service.nice.RealNiceClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiceClientConfigTest {

    private final NiceClientConfig config = new NiceClientConfig();
    private final NicePlaindataCodec codec = new NicePlaindataCodec();

    private NiceProperties properties(boolean mockEnabled, String siteCode) {
        NiceProperties properties = new NiceProperties();
        properties.setMockEnabled(mockEnabled);
        properties.setSiteCode(siteCode);
        return properties;
    }

    /** 실연동에 필요한 네 값을 모두 채운다. 거부 테스트가 하나씩 비운다. */
    private NiceProperties realProperties() {
        NiceProperties properties = properties(false, "SITECODE");
        properties.setSitePassword("SITEPASS");
        properties.setReturnUrl("https://example.test/api/auth/nice/callback");
        properties.setErrorUrl("https://example.test/api/auth/nice/callback/error");
        return properties;
    }

    /** 기동을 거부하고, 메시지에 빈 키 이름은 적되 자격증명 값은 적지 않는다. */
    private void assertRefused(NiceProperties properties, String blankKey) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> config.niceClient(properties, Clock.systemUTC(), codec));

        assertTrue(e.getMessage().contains(blankKey), e.getMessage());
        assertFalse(e.getMessage().contains("SITEPASS"), e.getMessage());
    }

    @Test
    void realClientWhenMockDisabledAndAllRealSettingsPresent() {
        NiceClient client = config.niceClient(realProperties(), Clock.systemUTC(), codec);

        assertInstanceOf(RealNiceClient.class, client);
    }

    @Test
    void mockClientWhenMockEnabledAndSiteCodeAbsent() {
        NiceClient client = config.niceClient(properties(true, ""), Clock.systemUTC(), codec);

        assertInstanceOf(MockNiceClient.class, client);
    }

    @Test
    void mockEnabledWithSiteCodeFailsFast() {
        // 운영 설정에 Mock 이 섞인 상태. 기동을 막지 않으면 본인확인 우회 경로가 열린다.
        assertThrows(IllegalStateException.class,
                () -> config.niceClient(properties(true, "SITECODE"), Clock.systemUTC(), codec));
    }

    @Test
    void mockDisabledWithoutSiteCodeFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> config.niceClient(properties(false, ""), Clock.systemUTC(), codec));
    }

    /*
     * 사이트코드만 있고 나머지가 비면 기동은 되고 첫 인증 시점에 실패한다. 기동에서 막아야 한다(fail-fast).
     */
    @Test
    void realWithoutSitePasswordFailsFast() {
        NiceProperties properties = realProperties();
        properties.setSitePassword("");

        assertRefused(properties, "site-password");
    }

    @Test
    void realWithoutReturnUrlFailsFast() {
        NiceProperties properties = realProperties();
        properties.setReturnUrl("");

        assertRefused(properties, "return-url");
    }

    @Test
    void realWithoutErrorUrlFailsFast() {
        NiceProperties properties = realProperties();
        properties.setErrorUrl(" ");

        assertRefused(properties, "error-url");
    }
}
