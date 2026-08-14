package com.shinyoung.recruit.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LDAP 접속·검색 설정이 하드코딩이 아니라 외부 설정으로 주입되는지 검증한다.
 *
 * <p>실제 접속정보는 쓰지 않고 example/dummy 값만 사용한다(CLAUDE.md 4.2).
 */
class LdapPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(LdapProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void 값을_주입하지_않으면_안전한_기본값이_쓰이고_미설정으로_판정된다() {
        contextRunner.run(context -> {
            LdapProperties properties = context.getBean(LdapProperties.class);

            assertThat(properties.getUrl()).isEqualTo("ldap://");
            assertThat(properties.getBase()).isEmpty();
            assertThat(properties.getManagerDn()).isEmpty();
            assertThat(properties.getManagerPassword()).isEmpty();
            assertThat(properties.getUserSearchBase()).isEmpty();
            assertThat(properties.getGroupSearchBase()).isEmpty();
            assertThat(properties.getUserSearchFilter()).isEqualTo("(sAMAccountName={0})");
            assertThat(properties.isConfigured()).isFalse();
        });
    }

    @Test
    void 외부_설정값이_그대로_주입된다() {
        contextRunner
                .withPropertyValues(
                        "recruit.ldap.url=ldap://ad.example.com:389",
                        "recruit.ldap.base=dc=example,dc=com",
                        "recruit.ldap.manager-dn=cn=dummy-bind,ou=service,dc=example,dc=com",
                        "recruit.ldap.manager-password=dummy-password",
                        "recruit.ldap.user-search-base=ou=example-users",
                        "recruit.ldap.user-search-filter=(userPrincipalName={0})",
                        "recruit.ldap.group-search-base=ou=example-groups"
                )
                .run(context -> {
                    LdapProperties properties = context.getBean(LdapProperties.class);

                    assertThat(properties.getUrl()).isEqualTo("ldap://ad.example.com:389");
                    assertThat(properties.getBase()).isEqualTo("dc=example,dc=com");
                    assertThat(properties.getManagerDn()).isEqualTo("cn=dummy-bind,ou=service,dc=example,dc=com");
                    assertThat(properties.getManagerPassword()).isEqualTo("dummy-password");
                    assertThat(properties.getUserSearchBase()).isEqualTo("ou=example-users");
                    assertThat(properties.getUserSearchFilter()).isEqualTo("(userPrincipalName={0})");
                    assertThat(properties.getGroupSearchBase()).isEqualTo("ou=example-groups");
                    assertThat(properties.isConfigured()).isTrue();
                });
    }

    /*
     * application.yaml에 적은 기본값 표현식을 그대로 검증한다.
     * 필터 기본값에 중괄호({0})가 들어가므로 플레이스홀더 파서가 이를 깨뜨리지 않아야 한다.
     */
    @Test
    void 필터_기본값_플레이스홀더_표현식이_중괄호를_포함해도_해석된다() {
        contextRunner
                .withPropertyValues(
                        "recruit.ldap.user-search-filter=${LDAP_USER_SEARCH_FILTER:(sAMAccountName={0})}"
                )
                .run(context -> {
                    LdapProperties properties = context.getBean(LdapProperties.class);

                    assertThat(properties.getUserSearchFilter()).isEqualTo("(sAMAccountName={0})");
                });
    }

    @Test
    void 자격증명이_비어_있으면_미설정으로_판정된다() {
        contextRunner
                .withPropertyValues(
                        "recruit.ldap.url=ldap://ad.example.com:389",
                        "recruit.ldap.user-search-base=ou=example-users"
                )
                .run(context -> {
                    LdapProperties properties = context.getBean(LdapProperties.class);

                    assertThat(properties.isConfigured()).isFalse();
                });
    }

    @Test
    void url이_기본값이면_나머지가_채워져도_미설정으로_판정된다() {
        contextRunner
                .withPropertyValues(
                        "recruit.ldap.manager-dn=cn=dummy-bind,ou=service,dc=example,dc=com",
                        "recruit.ldap.manager-password=dummy-password",
                        "recruit.ldap.user-search-base=ou=example-users"
                )
                .run(context -> {
                    LdapProperties properties = context.getBean(LdapProperties.class);

                    assertThat(properties.isConfigured()).isFalse();
                });
    }
}
