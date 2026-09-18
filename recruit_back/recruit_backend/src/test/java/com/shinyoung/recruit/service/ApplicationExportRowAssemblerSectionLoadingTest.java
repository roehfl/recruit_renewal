package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 선택 컬럼이 요구하는 섹션만 조회하는지(불필요한 1:N 조회 없음)를 DB 없이 고정한다. */
@ExtendWith(MockitoExtension.class)
class ApplicationExportRowAssemblerSectionLoadingTest {

    @Mock private ApplicationBasicInfoRepository basicInfoRepository;
    @Mock private ApplicationMilitaryRepository militaryRepository;
    @Mock private ApplicationEducationRepository educationRepository;
    @Mock private ApplicationCareerRepository careerRepository;
    @Mock private ApplicationCertificateRepository certificateRepository;
    @Mock private ApplicationLanguageRepository languageRepository;
    @Mock private ApplicationAwardRepository awardRepository;
    @Mock private ApplicationGapPeriodRepository gapPeriodRepository;
    @Mock private StageResultRepository stageResultRepository;
    @Mock private CommonCodeService commonCodeService;
    @Mock private EntityManager entityManager;

    private ApplicationExportRowAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ApplicationExportRowAssembler(
                basicInfoRepository, militaryRepository, educationRepository, careerRepository,
                certificateRepository, languageRepository, awardRepository, gapPeriodRepository,
                stageResultRepository, commonCodeService, Clock.systemDefaultZone());
        ReflectionTestUtils.setField(assembler, "entityManager", entityManager);
    }

    @Test
    void application_columns_use_base_row_only_and_clear_persistence_context() {
        List<Map<ApplicationExportColumn, String>> values = assembler.assemble(
                List.of(row(1L)),
                List.of(ApplicationExportColumn.APPLICATION_ID, ApplicationExportColumn.STATUS,
                        ApplicationExportColumn.APPLICATION_TYPE, ApplicationExportColumn.SUBMITTED_AT),
                assembler.newCodeNames());

        assertThat(values).containsExactly(Map.of(
                ApplicationExportColumn.APPLICATION_ID, "1",
                ApplicationExportColumn.STATUS, "제출 완료",
                ApplicationExportColumn.APPLICATION_TYPE, "신입",
                ApplicationExportColumn.SUBMITTED_AT, "2026-05-10 10:00"));
        verifyNoInteractions(basicInfoRepository, militaryRepository, educationRepository, careerRepository,
                certificateRepository, languageRepository, awardRepository, gapPeriodRepository,
                stageResultRepository, commonCodeService);
        verify(entityManager).clear();
    }

    @Test
    void career_columns_query_only_careers() {
        assembler.assemble(
                List.of(row(1L)),
                List.of(ApplicationExportColumn.CAREERS, ApplicationExportColumn.CURRENT_SALARY),
                assembler.newCodeNames());

        verify(careerRepository).findByJobApplicationIdInOrderBySortOrderAscIdAsc(List.of(1L));
        verifyNoInteractions(basicInfoRepository, militaryRepository, educationRepository,
                certificateRepository, languageRepository, awardRepository, gapPeriodRepository, stageResultRepository);
    }

    @Test
    void empty_page_returns_empty_without_queries() {
        assertThat(assembler.assemble(List.of(), ApplicationExportColumn.defaults(), assembler.newCodeNames())).isEmpty();

        verifyNoInteractions(basicInfoRepository, educationRepository, stageResultRepository, entityManager);
    }

    @Test
    void truncate_keeps_cells_within_excel_limit_with_room_for_formula_escape() {
        String truncated = ApplicationExportRowAssembler.truncate("가".repeat(40_000));

        assertThat(truncated).hasSize(ApplicationExportRowAssembler.MAX_CELL_LENGTH)
                .endsWith(ApplicationExportRowAssembler.TRUNCATED_SUFFIX);
        assertThat(ApplicationExportRowAssembler.truncate("짧은 값")).isEqualTo("짧은 값");
    }

    @Test
    void truncate_does_not_split_a_surrogate_pair() {
        // "a" 오프셋으로 자르는 지점(내부 인덱스 32,757)이 정확히 high surrogate 에 떨어지게 만든다 —
        // 오프셋 없이 이모지만 반복하면 그 지점이 low surrogate 라 backoff 없이도 우연히 통과한다.
        String truncated = ApplicationExportRowAssembler.truncate("a" + "😀".repeat(20_000));

        assertThat(truncated).hasSize(ApplicationExportRowAssembler.MAX_CELL_LENGTH - 1)
                .endsWith(ApplicationExportRowAssembler.TRUNCATED_SUFFIX);
        String beforeSuffix = truncated.substring(0,
                truncated.length() - ApplicationExportRowAssembler.TRUNCATED_SUFFIX.length());
        assertThat(Character.isHighSurrogate(beforeSuffix.charAt(beforeSuffix.length() - 1))).isFalse();
    }

    private static ApplicationExportRow row(Long applicationId) {
        return new ApplicationExportRow(
                applicationId, "스냅샷", "01000000000", "acct@example.com", "공고",
                JobPositionApplicationType.NEW_GRADUATE, "Backend", null, null, JobApplicationStatus.SUBMITTED,
                LocalDateTime.of(2026, 5, 10, 10, 0), null,
                LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 10, 10, 0));
    }
}
