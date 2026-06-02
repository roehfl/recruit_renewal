package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.ExportProperties;
import com.shinyoung.recruit.exception.ExportRowLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {

    @Mock
    private ExcelExportWriter excelExportWriter;

    @Mock
    private ExportProperties exportProperties;

    private ExcelExportService excelExportService;

    private final ExcelExportSpec<String> spec = new ExcelExportSpec<>(
            "sheet", List.of(new ExportColumn<>("value", value -> value))
    );

    @BeforeEach
    void setUp() {
        excelExportService = new ExcelExportService(excelExportWriter, exportProperties);
    }

    @Test
    void row수가_max를_초과하면_writer를_호출하지_않고_거부한다() throws IOException {
        given(exportProperties.getMaxRows()).willReturn(1L);

        assertThatThrownBy(() -> excelExportService.generate(spec, List.of("a", "b"), "x.xlsx"))
                .isInstanceOf(ExportRowLimitExceededException.class)
                .hasMessageContaining(ExportRowLimitExceededException.CODE);

        verify(excelExportWriter, never()).writeToTempFile(any(), any(), anyBoolean());
    }

    @Test
    void row수가_max_이내면_writer로_파일을_생성한다() throws IOException {
        Path tempFile = Files.createTempFile("excel-export-service-", ".xlsx");
        try {
            given(exportProperties.getMaxRows()).willReturn(50L);
            given(excelExportWriter.writeToTempFile(any(), any(), anyBoolean())).willReturn(tempFile);

            ExcelExportFile file = excelExportService.generate(spec, List.of("a", "b", "c"), "x.xlsx");

            assertThat(file.path()).isEqualTo(tempFile);
            assertThat(file.rowCount()).isEqualTo(3L);
            assertThat(file.fileName()).isEqualTo("x.xlsx");
            verify(excelExportWriter).writeToTempFile(any(), any(), anyBoolean());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
