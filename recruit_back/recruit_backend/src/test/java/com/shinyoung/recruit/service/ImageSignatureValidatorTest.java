package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSignatureValidatorTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] WEBP_HEAD = {0x52, 0x49, 0x46, 0x46, 0x10, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};

    @Test
    void PNG_시그니처를_인식한다() {
        assertThat(ImageSignatureValidator.matches("image/png", PNG_HEAD)).isTrue();
    }

    @Test
    void JPEG_시그니처를_인식한다() {
        assertThat(ImageSignatureValidator.matches("image/jpeg", JPEG_HEAD)).isTrue();
    }

    @Test
    void WEBP_시그니처를_인식한다() {
        assertThat(ImageSignatureValidator.matches("image/webp", WEBP_HEAD)).isTrue();
    }

    @Test
    void contentType과_시그니처가_다르면_거부한다() {
        assertThat(ImageSignatureValidator.matches("image/png", JPEG_HEAD)).isFalse();
    }

    @Test
    void 미지원_contentType은_거부한다() {
        assertThat(ImageSignatureValidator.matches("image/gif", PNG_HEAD)).isFalse();
    }

    @Test
    void null_또는_짧은_head는_거부한다() {
        assertThat(ImageSignatureValidator.matches("image/png", null)).isFalse();
        assertThat(ImageSignatureValidator.matches("image/png", new byte[]{(byte) 0x89})).isFalse();
    }
}
