package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.ApplicationExportColumnGroupResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.shinyoung.recruit.service.ApplicationExportColumn.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationExportColumnTest {

    private static final List<ApplicationExportColumn> DEFAULTS = List.of(
            APPLICATION_ID, JOB_POSITION_NAME, WORK_LOCATION, STATUS, SUBMITTED_AT,
            LATEST_STAGE_RESULT,
            NAME, BIRTH_DATE, AGE, MOBILE_PHONE, EMAIL,
            FINAL_EDUCATION_LEVEL, FINAL_SCHOOL_NAME, FINAL_GRADUATION_DATE);

    @Test
    void blank_or_missing_keys_fall_back_to_default_columns() {
        assertThat(ApplicationExportColumn.defaults()).isEqualTo(DEFAULTS);
        assertThat(ApplicationExportColumn.parse(null)).isEqualTo(DEFAULTS);
        assertThat(ApplicationExportColumn.parse(List.of())).isEqualTo(DEFAULTS);
        assertThat(ApplicationExportColumn.parse(List.of(" ", ""))).isEqualTo(DEFAULTS);
    }

    @Test
    void parse_dedupes_and_orders_by_catalog_regardless_of_request_order() {
        assertThat(ApplicationExportColumn.parse(List.of("email", "APPLICATION_ID", " NAME ", "EMAIL")))
                .containsExactly(APPLICATION_ID, NAME, EMAIL);
    }

    @Test
    void parse_rejects_unknown_key() {
        assertThatThrownBy(() -> ApplicationExportColumn.parse(List.of("APPLICATION_ID", "NOT_A_COLUMN")))
                .isInstanceOf(InvalidJobApplicationException.class)
                .hasMessageContaining("NOT_A_COLUMN");
    }

    @Test
    void required_sections_skip_application_columns() {
        assertThat(ApplicationExportColumn.requiredSections(List.of(APPLICATION_ID, STATUS))).isEmpty();
        assertThat(ApplicationExportColumn.requiredSections(List.of(NAME, AGE, FINAL_SCHOOL_NAME, CURRENT_SALARY, CAREERS)))
                .containsExactlyInAnyOrder(
                        ApplicationExportSection.BASIC_INFO,
                        ApplicationExportSection.EDUCATION,
                        ApplicationExportSection.CAREER);
    }

    @Test
    void catalog_groups_columns_in_declaration_order() {
        List<ApplicationExportColumnGroupResponse> catalog = ApplicationExportColumn.catalog();

        assertThat(catalog).extracting(ApplicationExportColumnGroupResponse::group)
                .containsExactly("지원사항", "전형결과", "기본정보", "병역", "최종학력", "다건 요약");
        assertThat(catalog.get(0).columns().get(0))
                .isEqualTo(new ApplicationExportColumnGroupResponse.Column("APPLICATION_ID", "수험번호", true));
        assertThat(catalog.stream().mapToInt(group -> group.columns().size()).sum())
                .isEqualTo(ApplicationExportColumn.values().length);
    }

    @Test
    void only_one_to_many_summary_columns_wrap_text() {
        assertThat(Arrays.stream(ApplicationExportColumn.values()).filter(ApplicationExportColumn::wrapText).toList())
                .containsExactly(STAGE_RESULTS, EDUCATIONS, CAREERS, CURRENT_SALARY,
                        CERTIFICATES, LANGUAGES, AWARDS, GAP_PERIODS);
    }
}
