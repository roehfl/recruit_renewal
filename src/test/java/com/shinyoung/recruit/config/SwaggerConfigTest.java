package com.shinyoung.recruit.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class SwaggerConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(SwaggerConfig.class);
    @Test
    void bean_생성_검증() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenAPI.class);
        });
    }
}
