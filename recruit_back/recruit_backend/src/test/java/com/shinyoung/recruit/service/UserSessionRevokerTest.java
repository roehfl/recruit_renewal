package com.shinyoung.recruit.service;

import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionRevokerTest {

    private final SessionRegistryImpl registry = new SessionRegistryImpl();
    private final UserSessionRevoker revoker = new UserSessionRevoker(registry);

    private static CustomUserDetails user(String loginId) {
        return CustomUserDetails.fromLdap(loginId, "", "테스트", List.of());
    }

    @Test
    void 같은_계정의_다른_세션만_만료하고_지금_세션과_다른_계정은_남긴다() {
        registry.registerNewSession("current", user("applicant@example.com"));
        registry.registerNewSession("other-browser", user("applicant@example.com"));
        registry.registerNewSession("someone-else", user("other@example.com"));

        revoker.expireSessions("applicant@example.com", "current");

        assertThat(registry.getSessionInformation("current").isExpired()).isFalse();
        assertThat(registry.getSessionInformation("other-browser").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("someone-else").isExpired()).isFalse();
    }

    @Test
    void 남길_세션이_없으면_그_계정의_세션을_모두_만료한다() {
        registry.registerNewSession("a", user("applicant@example.com"));
        registry.registerNewSession("b", user("applicant@example.com"));

        revoker.expireSessions("applicant@example.com", null);

        assertThat(registry.getSessionInformation("a").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("b").isExpired()).isTrue();
    }
}
