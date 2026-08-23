package com.shinyoung.recruit.config;

import com.shinyoung.recruit.common.crypto.AesCryptoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class CryptoConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(CryptoConfig.class)
                    .withPropertyValues("crypto.aes.key=22791194512954214612461221261067");

    @Test
    void bean_생성_검증() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AesCryptoUtil.class);
        });
    }
}
