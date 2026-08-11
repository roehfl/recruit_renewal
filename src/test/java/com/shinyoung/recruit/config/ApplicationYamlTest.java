package com.shinyoung.recruit.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 {@code src/main/resources/application.yaml} 자체를 검증한다.
 *
 * <p>{@code @SpringBootTest}는 클래스패스 우선순위상 {@code src/test/resources/application.yaml}을 읽으므로
 * 운영 설정 파일의 문법 오류를 잡지 못한다. 실제로 최상위 {@code recruit} 키를 중복 정의해
 * 기동이 실패한 적이 있어(2026-08-11) 이 테스트를 둔다.
 *
 * <p>Spring Boot의 {@code OriginTrackedYamlLoader}와 동일하게 중복 키를 거부하도록 로더를 구성한다.
 */
class ApplicationYamlTest {

    private static final Path APPLICATION_YAML = Path.of("src/main/resources/application.yaml");

    private Map<String, Object> loadStrictly() throws Exception {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);

        try (InputStream inputStream = Files.newInputStream(APPLICATION_YAML)) {
            return new Yaml(new SafeConstructor(loaderOptions)).load(inputStream);
        }
    }

    @Test
    void 운영_설정_파일은_중복키_없이_파싱된다() throws Exception {
        assertThat(Files.exists(APPLICATION_YAML))
                .as("운영 설정 파일 경로가 바뀌면 이 테스트를 함께 고쳐야 한다")
                .isTrue();

        assertThat(loadStrictly()).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void LDAP_설정이_recruit_블록_아래_선언되어_있다() throws Exception {
        Map<String, Object> root = loadStrictly();
        Map<String, Object> recruit = (Map<String, Object>) root.get("recruit");

        assertThat(recruit).as("recruit 블록이 있어야 한다").isNotNull();

        Map<String, Object> ldap = (Map<String, Object>) recruit.get("ldap");

        assertThat(ldap).as("recruit.ldap 블록이 있어야 한다").isNotNull();
        assertThat(ldap).containsOnlyKeys(
                "url", "base", "manager-dn", "manager-password",
                "user-search-base", "user-search-filter", "group-search-base"
        );
    }

    /*
     * 접속정보가 저장소에 다시 하드코딩되는 것을 막는다.
     * 모든 값이 ${...} 플레이스홀더여야 한다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void LDAP_설정값은_모두_외부주입_플레이스홀더다() throws Exception {
        Map<String, Object> root = loadStrictly();
        Map<String, Object> recruit = (Map<String, Object>) root.get("recruit");
        Map<String, Object> ldap = (Map<String, Object>) recruit.get("ldap");

        assertThat(ldap.values())
                .allSatisfy(value -> assertThat(String.valueOf(value)).startsWith("${"));
    }
}
