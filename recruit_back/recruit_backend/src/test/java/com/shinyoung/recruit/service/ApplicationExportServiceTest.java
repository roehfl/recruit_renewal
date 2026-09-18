package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.ExportProperties;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.exception.ExportRowLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationExportServiceTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private ExcelExportWriter excelExportWriter;

    @Mock
    private ExportProperties exportProperties;

    @Mock
    private ApplicationExportRowAssembler rowAssembler;

    @Captor
    private ArgumentCaptor<ExcelExportSpec<Map<ApplicationExportColumn, String>>> specCaptor;

    @Captor
    private ArgumentCaptor<ExportRowSource<Map<ApplicationExportColumn, String>>> sourceCaptor;

    private ApplicationExportService applicationExportService;

    @BeforeEach
    void setUp() {
        applicationExportService = new ApplicationExportService(
                jobApplicationRepository,
                jobPostingRepository,
                excelExportWriter,
                exportProperties,
                new AdminApplicationSearchConditionFactory(),
                rowAssembler
        );
    }

    @Test
    void export가_row_cap을_초과하면_writer를_호출하지_않고_거부한다() throws IOException {
        given(exportProperties.getMaxRows()).willReturn(1L);
        given(jobApplicationRepository.countExportApplications(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).willReturn(5L);

        assertThatThrownBy(() -> applicationExportService.exportApplications(null, null, ApplicationExportColumn.defaults()))
                .isInstanceOf(ExportRowLimitExceededException.class)
                .hasMessageContaining(ExportRowLimitExceededException.CODE);

        verify(excelExportWriter, never()).writeToTempFile(any(), any());
    }

    @Test
    void export가_row_cap_이내면_writer로_파일을_생성한다() throws IOException {
        Path tempFile = Files.createTempFile("row-cap-within-", ".xlsx");
        try {
            given(exportProperties.getMaxRows()).willReturn(50_000L);
            given(jobApplicationRepository.countExportApplications(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).willReturn(3L);
            given(excelExportWriter.writeToTempFile(any(), any())).willReturn(tempFile);

            ExcelExportFile file = applicationExportService.exportApplications(null, null, ApplicationExportColumn.defaults());

            assertThat(file.path()).isEqualTo(tempFile);
            assertThat(file.rowCount()).isEqualTo(3L);
            assertThat(file.fileName()).isEqualTo("applications-export.xlsx");
            verify(excelExportWriter).writeToTempFile(any(), any());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void sheet_follows_selected_columns_and_rows_go_through_assembler() throws IOException {
        Path tempFile = Files.createTempFile("columns-", ".xlsx");
        try {
            List<ApplicationExportColumn> columns = List.of(ApplicationExportColumn.APPLICATION_ID, ApplicationExportColumn.CAREERS);
            CommonCodeNames codeNames = new CommonCodeNames(null);
            given(exportProperties.getMaxRows()).willReturn(50_000L);
            given(jobApplicationRepository.countExportApplications(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).willReturn(1L);
            given(rowAssembler.newCodeNames()).willReturn(codeNames);
            given(excelExportWriter.writeToTempFile(any(), any())).willReturn(tempFile);

            applicationExportService.exportApplications(null, null, columns);

            verify(excelExportWriter).writeToTempFile(specCaptor.capture(), sourceCaptor.capture());
            ExcelExportSpec<Map<ApplicationExportColumn, String>> spec = specCaptor.getValue();
            assertThat(spec.columns()).extracting(ExportColumn::header).containsExactly("수험번호", "경력");
            assertThat(spec.columns()).extracting(ExportColumn::wrapText).containsExactly(false, true);
            assertThat(spec.columns().get(1).value(Map.of(ApplicationExportColumn.CAREERS, "A사"))).isEqualTo("A사");

            // 페이지 소스는 base 조회 결과를 assembler 로 넘긴다(repository mock 은 빈 목록을 돌려준다).
            // 여러 페이지를 호출해도 codeNames 는 export 1회당 한 번만 만들고(newCodeNames), 매 페이지 같은
            // 인스턴스를 assemble 에 넘기는지 확인한다.
            sourceCaptor.getValue().fetch(0, 1_000);
            sourceCaptor.getValue().fetch(1, 1_000);
            verify(rowAssembler, times(1)).newCodeNames();
            verify(rowAssembler, times(2)).assemble(List.of(), columns, codeNames);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
