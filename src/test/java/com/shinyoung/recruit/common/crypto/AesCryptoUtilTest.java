package com.shinyoung.recruit.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AesCryptoUtilTest {

    private AesCryptoUtil aesCryptoUtil;

    @BeforeEach
    void setUp() {
        aesCryptoUtil = new AesCryptoUtil("22791194512954214612461221261067");
    }

    @Test
    void encrypt_decrypt_정상동작() {
        String plain = "hello";

        String encrypted = aesCryptoUtil.encrypt(plain);
        String decrypted = aesCryptoUtil.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }
}
