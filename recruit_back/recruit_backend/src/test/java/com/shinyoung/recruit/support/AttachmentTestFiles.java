package com.shinyoung.recruit.support;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 첨부 업로드 테스트용 파일 내용. 업로드는 매직바이트가 확장자와 맞아야 통과하므로(AttachmentSignatureValidator)
 * 파일명 확장자에 맞는 시그니처를 본문 앞에 붙인다. 시그니처를 모르는 확장자는 본문만 준다.
 */
public final class AttachmentTestFiles {

    private AttachmentTestFiles() {
    }

    public static byte[] content(String originalFileName, String body) {
        byte[] signature = signature(originalFileName);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[signature.length + bodyBytes.length];
        System.arraycopy(signature, 0, result, 0, signature.length);
        System.arraycopy(bodyBytes, 0, result, signature.length, bodyBytes.length);
        return result;
    }

    private static byte[] signature(String originalFileName) {
        int dot = originalFileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : originalFileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
            case "jpg", "jpeg" -> bytes(0xFF, 0xD8, 0xFF, 0xE0);
            case "png" -> bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "doc", "xls", "hwp" -> bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "docx", "xlsx", "hwpx" -> bytes(0x50, 0x4B, 0x03, 0x04);
            default -> new byte[0];
        };
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
