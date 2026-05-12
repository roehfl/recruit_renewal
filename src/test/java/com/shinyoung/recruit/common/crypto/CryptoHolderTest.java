package com.shinyoung.recruit.common.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CryptoHolderTest {

    @Test
    void aesCryptoUtil_주입된다() {
        AesCryptoUtil util = CryptoHolder.get();

        assertThat(util).isNotNull();
    }

    @Test
    void encrypt_동작확인() {
        AesCryptoUtil util = CryptoHolder.get();

        String plainText = "helloTest";
        String encrypted = util.encrypt(plainText);
        String decrypted = util.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
    }
}
