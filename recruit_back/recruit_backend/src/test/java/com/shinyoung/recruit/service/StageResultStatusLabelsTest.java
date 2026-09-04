package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.StageResultStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StageResultStatusLabelsTest {

    @Test
    void label_maps_every_status_to_korean() {
        assertThat(StageResultStatusLabels.label(StageResultStatus.PENDING)).isEqualTo("대기");
        assertThat(StageResultStatusLabels.label(StageResultStatus.PASSED)).isEqualTo("합격");
        assertThat(StageResultStatusLabels.label(StageResultStatus.FAILED)).isEqualTo("불합격");
        assertThat(StageResultStatusLabels.label(StageResultStatus.HOLD)).isEqualTo("보류");
        assertThat(StageResultStatusLabels.label(StageResultStatus.ABSENT)).isEqualTo("결시");
        assertThat(StageResultStatusLabels.label(StageResultStatus.WITHDRAWN)).isEqualTo("철회");
        assertThat(StageResultStatusLabels.label(null)).isEmpty();
    }

    @Test
    void parse_accepts_korean_label_and_enum_name_case_insensitively() {
        assertThat(StageResultStatusLabels.parse("합격")).contains(StageResultStatus.PASSED);
        assertThat(StageResultStatusLabels.parse(" 불합격 ")).contains(StageResultStatus.FAILED);
        assertThat(StageResultStatusLabels.parse("PASSED")).contains(StageResultStatus.PASSED);
        assertThat(StageResultStatusLabels.parse("hold")).contains(StageResultStatus.HOLD);
        assertThat(StageResultStatusLabels.parse("대기")).contains(StageResultStatus.PENDING);
    }

    @Test
    void parse_rejects_unknown_or_blank() {
        assertThat(StageResultStatusLabels.parse("합")).isEmpty();
        assertThat(StageResultStatusLabels.parse("")).isEmpty();
        assertThat(StageResultStatusLabels.parse(null)).isEmpty();
    }

    @Test
    void upload_choices_exclude_pending() {
        assertThat(StageResultStatusLabels.uploadChoices())
                .containsExactly("합격", "불합격", "보류", "결시", "철회");
    }
}
