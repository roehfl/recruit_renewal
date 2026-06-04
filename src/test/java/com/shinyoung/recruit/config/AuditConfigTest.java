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
    void allow_fallback이_true면_secret이_비어도_fallback으로_빈_생성() {
        contextRunner
                .withPropertyValues("audit.allow-fallback-secret=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditHmac.class);
                });
    }

    @Test
    void allow_fallback_미설정_기본값이면_secret이_빌_때_기동_실패() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class);
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

    @Test
    void 운영_profile에서는_allow_fallback이_true여도_기동_실패() {
        contextRunner
                .withPropertyValues("audit.allow-fallback-secret=true")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class);
                });
    }
}
