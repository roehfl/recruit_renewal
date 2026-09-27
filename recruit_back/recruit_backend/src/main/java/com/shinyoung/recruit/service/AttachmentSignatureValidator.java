package com.shinyoung.recruit.service;

import java.nio.charset.StandardCharsets;

/**
 * 지원자 첨부 매직바이트 검증. 확장자·Content-Type 은 클라이언트가 정하므로, 실제 내용이 확장자의 형식인지 본다.
 * 실행 파일·스크립트를 문서 확장자로 바꿔 올리는 경우를 막는다(매크로·파서 취약점은 형식이 같아 막지 못한다).
 *
 * <p>시그니처를 모르는 확장자는 거부한다 — 허용 확장자를 설정으로 늘리려면 여기에 시그니처도 추가해야 한다.
 */
public final class AttachmentSignatureValidator {

    /** 파일 앞부분을 이만큼 읽는다. PDF 는 규격상 {@code %PDF-} 앞에 다른 바이트가 올 수 있어 넉넉히 본다. */
    public static final int HEAD_LENGTH = 1024;

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int[] JPEG = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    /** MS 복합 문서(OLE2) — doc·xls·hwp(5.0). */
    private static final int[] OLE2 = {0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1};
    /** ZIP — docx·xlsx·hwpx. */
    private static final int[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    /** 한글 3.0 이하(HWP 97) 문서. */
    private static final byte[] HWP3 = "HWP Document File".getBytes(StandardCharsets.US_ASCII);

    private AttachmentSignatureValidator() {
    }

    public static boolean matches(String extension, byte[] head) {
        if (extension == null || head == null) {
            return false;
        }
        return switch (extension) {
            case "pdf" -> contains(head, PDF);
            case "jpg", "jpeg" -> startsWith(head, JPEG);
            case "png" -> startsWith(head, PNG);
            case "doc", "xls" -> startsWith(head, OLE2);
            case "hwp" -> startsWith(head, OLE2) || startsWith(head, HWP3);
            case "docx", "xlsx", "hwpx" -> startsWith(head, ZIP);
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

    private static boolean startsWith(byte[] head, byte[] expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (head[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] head, byte[] expected) {
        for (int start = 0; start + expected.length <= head.length; start++) {
            boolean match = true;
            for (int i = 0; i < expected.length; i++) {
                if (head[start + i] != expected[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
