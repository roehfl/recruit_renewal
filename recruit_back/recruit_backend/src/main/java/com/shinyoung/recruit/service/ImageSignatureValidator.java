package com.shinyoung.recruit.service;

/** 공고 이미지 매직바이트 검증. Content-Type 위조 업로드를 서빙 전에 차단한다. */
public final class ImageSignatureValidator {

    private ImageSignatureValidator() {
    }

    public static boolean matches(String contentType, byte[] head) {
        if (contentType == null || head == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> head.length >= 3
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "image/png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> head.length >= 12
                    && startsWith(head, 0x52, 0x49, 0x46, 0x46)
                    && (head[8] & 0xFF) == 0x57 && (head[9] & 0xFF) == 0x45
                    && (head[10] & 0xFF) == 0x42 && (head[11] & 0xFF) == 0x50;
            default -> false;
        };
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
