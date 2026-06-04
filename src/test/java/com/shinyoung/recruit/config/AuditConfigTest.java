package com.shinyoung.recruit.config;

import com.shinyoung.recruit.common.hash.AuditHmac;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuditConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(AuditConfig.class);

    @Test
    void secret이_주입되면_AuditHmac_빈_생성() {
        contextRunner
                .withPropertyValues("audit.hmac-secret=some-real-secret")
                .run(context -> assertThat(context).hasSingleBean(AuditHmac.class));
    }

    @Test
    void 비운영에서_secret이_비어도_fallback으로_빈_생성() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuditHmac.class);
        });
    }

    @Test
    void 운영_profile에서_secret이_비면_기동_실패() {
        contextRunner
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class);
                });
    }
}
