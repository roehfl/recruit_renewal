package com.shinyoung.recruit.config;

import com.shinyoung.recruit.security.auth.CustomLdapUserDetailsMapper;
import com.shinyoung.recruit.security.auth.RoutingAuthenticationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationConfigTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AuthenticationConfig.class);

    @Test
    void bean_생성_검증() {
        contextRunner.run(context -> {
//            assertThat(context).hasSingleBean(LdapContextSource.class);
//            assertThat(context).hasSingleBean(LdapAuthenticationProvider.class);
//            assertThat(context).hasSingleBean(DaoAuthenticationProvider.class);
//            assertThat(context).hasSingleBean(RoutingAuthenticationProvider.class);
//            assertThat(context).hasSingleBean(AuthenticationManager.class);
//            assertThat(context).hasSingleBean(PasswordEncoder.class);
//            assertThat(context).hasSingleBean(CustomLdapUserDetailsMapper.class);
        });
    }
}
