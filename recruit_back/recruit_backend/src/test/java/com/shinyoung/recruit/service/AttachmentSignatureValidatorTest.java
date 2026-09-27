package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentSignatureValidatorTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0);
    private static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    private static final byte[] OLE2 = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
    private static final byte[] ZIP = bytes(0x50, 0x4B, 0x03, 0x04);
    private static final byte[] EXE = bytes('M', 'Z', 0x90, 0x00);

    @Test
    void 확장자별_시그니처가_맞으면_통과한다() {
        assertThat(AttachmentSignatureValidator.matches("pdf", PDF)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("jpg", JPEG)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("jpeg", JPEG)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("png", PNG)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("doc", OLE2)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("xls", OLE2)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("hwp", OLE2)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("hwp", "HWP Document File V3.00".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(AttachmentSignatureValidator.matches("docx", ZIP)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("xlsx", ZIP)).isTrue();
        assertThat(AttachmentSignatureValidator.matches("hwpx", ZIP)).isTrue();
    }

    @Test
    void PDF는_앞부분_안에_시그니처가_있으면_통과한다() {
        byte[] withPreamble = new byte[20 + PDF.length];
        System.arraycopy(PDF, 0, withPreamble, 20, PDF.length);

        assertThat(AttachmentSignatureValidator.matches("pdf", withPreamble)).isTrue();
    }

    @Test
    void 다른_형식이나_실행파일이나_빈_내용은_거부한다() {
        assertThat(AttachmentSignatureValidator.matches("pdf", EXE)).isFalse();
        assertThat(AttachmentSignatureValidator.matches("png", JPEG)).isFalse();
        assertThat(AttachmentSignatureValidator.matches("docx", OLE2)).isFalse();
        assertThat(AttachmentSignatureValidator.matches("doc", ZIP)).isFalse();
        assertThat(AttachmentSignatureValidator.matches("jpg", new byte[0])).isFalse();
        assertThat(AttachmentSignatureValidator.matches("pdf", null)).isFalse();
    }

    @Test
    void 시그니처를_모르는_확장자는_거부한다() {
        assertThat(AttachmentSignatureValidator.matches("txt", "hello".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(AttachmentSignatureValidator.matches("zip", ZIP)).isFalse();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
