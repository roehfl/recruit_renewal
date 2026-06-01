package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.ExportProperties;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.exception.ExportRowLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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

    private ApplicationExportService applicationExportService;

    @BeforeEach
    void setUp() {
        applicationExportService = new ApplicationExportService(
                jobApplicationRepository,
                jobPostingRepository,
                excelExportWriter,
                exportProperties
        );
    }

    @Test
    void export가_row_cap을_초과하면_writer를_호출하지_않고_거부한다() throws IOException {
        given(exportProperties.getMaxRows()).willReturn(1L);
        given(jobApplicationRepository.countExportApplications(isNull(), isNull(), isNull())).willReturn(5L);

        assertThatThrownBy(() -> applicationExportService.exportApplications(null, null, null))
                .isInstanceOf(ExportRowLimitExceededException.class)
                .hasMessageContaining(ExportRowLimitExceededException.CODE);

        verify(excelExportWriter, never()).writeToTempFile(any(), any());
    }

    @Test
    void export가_row_cap_이내면_writer로_파일을_생성한다() throws IOException {
        Path tempFile = Files.createTempFile("row-cap-within-", ".xlsx");
        try {
            given(exportProperties.getMaxRows()).willReturn(50_000L);
            given(jobApplicationRepository.countExportApplications(isNull(), isNull(), isNull())).willReturn(3L);
            given(excelExportWriter.writeToTempFile(any(), any())).willReturn(tempFile);

            ExcelExportFile file = applicationExportService.exportApplications(null, null, null);

            assertThat(file.path()).isEqualTo(tempFile);
            assertThat(file.rowCount()).isEqualTo(3L);
            assertThat(file.fileName()).isEqualTo("applications-export.xlsx");
            verify(excelExportWriter).writeToTempFile(any(), any());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
