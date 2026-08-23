package com.shinyoung.recruit.common.hash;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HashUtilTest {

    @Test
    void 동일값은_항상_같은_해시() {
        String plain = "hash test";

        String hash1 = HashUtil.sha256(plain);
        String hash2 = HashUtil.sha256(plain);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void 다른값은_다른_해시() {
        String hash1 = HashUtil.sha256("aaa");
        String hash2 = HashUtil.sha256("aab");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void 해시는_원본과_다름() {
        String plain = "original";
        String hash = HashUtil.sha256(plain);

        assertThat(plain).isNotEqualTo(hash);
    }

    @Test
    void 해시_길이_검증() {
        String hash = HashUtil.sha256("hashValue");
        assertThat(hash).hasSize(64);
    }

    @Test
    void null값_예외확인() {
        assertThatThrownBy(() -> HashUtil.sha256(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid input for hashing");
    }
}
