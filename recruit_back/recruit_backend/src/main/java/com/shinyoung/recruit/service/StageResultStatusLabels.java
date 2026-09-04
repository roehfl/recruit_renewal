package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 전형 결과 상태의 관리자용 한글 라벨. 엑셀 업로드 템플릿(prefill·드롭다운)과 업로드 파싱이 같은 표를 쓴다.
 * 파싱은 한글 라벨과 enum 이름(대소문자 무시)을 모두 받는다 — 사용자가 드롭다운 대신 {@code PASSED} 처럼
 * 직접 입력해도 통한다. (구 영문 템플릿 파일 자체는 header 시그니처 불일치로 파서가 먼저 거부한다.)
 */
public final class StageResultStatusLabels {

    private static final Map<StageResultStatus, String> LABELS = new EnumMap<>(Map.of(
            StageResultStatus.PENDING, "대기",
            StageResultStatus.PASSED, "합격",
            StageResultStatus.FAILED, "불합격",
            StageResultStatus.HOLD, "보류",
            StageResultStatus.ABSENT, "결시",
            StageResultStatus.WITHDRAWN, "철회"));

    /** 업로드에서 선택 가능한 값 = 전체 − 대기(PENDING). 드롭다운 목록 순서. */
    private static final List<String> UPLOAD_CHOICES = List.of("합격", "불합격", "보류", "결시", "철회");

    private StageResultStatusLabels() {
    }

    public static String label(StageResultStatus status) {
        if (status == null) {
            return "";
        }
        String label = LABELS.get(status);
        if (label == null) {
            throw new IllegalStateException("매핑되지 않은 StageResultStatus: " + status);
        }
        return label;
    }

    public static Optional<StageResultStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        for (Map.Entry<StageResultStatus, String> entry : LABELS.entrySet()) {
            if (entry.getValue().equals(value)) {
                return Optional.of(entry.getKey());
            }
        }
        try {
            return Optional.of(StageResultStatus.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static List<String> uploadChoices() {
        return UPLOAD_CHOICES;
    }
}
