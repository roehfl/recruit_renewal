package com.shinyoung.recruit.common.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AesAttributeConverterTest {

    private final AesAttributeConverter converter = new AesAttributeConverter();

    @Test
    void convertToDatabaseColumn_encrypt_정상동작() {
        String plain = "이솔";

        String encrypted = converter.convertToDatabaseColumn(plain);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plain);
    }

    @Test
    void convertToEntityAttribute_decrypt_정상동작() {
        String plain = "이솔";

        String encrypted = converter.convertToDatabaseColumn(plain);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void null값_처리() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
