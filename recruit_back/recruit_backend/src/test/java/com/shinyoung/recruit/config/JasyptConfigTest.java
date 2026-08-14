package com.shinyoung.recruit.config;

import com.shinyoung.recruit.common.crypto.AesCryptoUtil;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class JasyptConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(JasyptConfig.class)
                    .withPropertyValues("app.modules.pkgs=shinrecruit");

    @Test
    void bean_생성_검증() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(StringEncryptor.class);
        });
    }
}
