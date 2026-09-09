package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.PdfProperties;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.PdfBulkLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 일괄 zip 생성 규칙 회귀(S3). 상한/중복/파일명/temp 정리를 고정한다. */
class ApplicationPdfBulkServiceTest {

    private ApplicationPdfService pdfService;
    private PdfProperties pdfProperties;
    private ApplicationPdfBulkService bulkService;
    private final List<ApplicationPdfZipFile> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        pdfService = mock(ApplicationPdfService.class);
        pdfProperties = new PdfProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-09-09T02:03:04Z"), ZoneId.of("Asia/Seoul"));
        bulkService = new ApplicationPdfBulkService(pdfService, pdfProperties, clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (ApplicationPdfZipFile file : created) {
            Files.deleteIfExists(file.path());
        }
    }

    @Test
    void 선택한_지원서를_각각_PDF로_담아_zip을_만든다() throws Exception {
        givenPdfFor(1L, "1_홍길동.pdf");
        givenPdfFor(2L, "2_김철수.pdf");

        ApplicationPdfZipFile zipFile = generate(List.of(1L, 2L));

        assertThat(entryNames(zipFile)).containsExactly("1_홍길동.pdf", "2_김철수.pdf");
        assertThat(zipFile.entries()).extracting(ApplicationPdfZipFile.Entry::applicationId)
                .containsExactly(1L, 2L);
        assertThat(zipFile.fileName()).isEqualTo("applications-20260909110304.zip");
    }

    @Test
    void 중복_id는_한_번만_담고_상한_계산에서도_한_건으로_센다() throws Exception {
        pdfProperties.setBulkMaxCount(2);
        givenPdfFor(1L, "1_홍길동.pdf");

        ApplicationPdfZipFile zipFile = generate(List.of(1L, 1L, 1L));

        assertThat(entryNames(zipFile)).containsExactly("1_홍길동.pdf");
        verify(pdfService, times(1)).generate(1L);
    }

    @Test
    void 상한을_넘으면_400_예외를_던지고_렌더하지_않는다() {
        pdfProperties.setBulkMaxCount(2);

        assertThatThrownBy(() -> bulkService.generate(List.of(1L, 2L, 3L)))
                .isInstanceOf(PdfBulkLimitExceededException.class)
                .hasMessageContaining("최대 2건")
                .hasMessageContaining("요청 3건");

        verify(pdfService, times(0)).generate(anyLong());
    }

    @Test
    void 파일명이_겹치면_접미사를_붙여_덮어쓰지_않는다() throws Exception {
        givenPdfFor(1L, "동명이인.pdf");
        givenPdfFor(2L, "동명이인.pdf");

        ApplicationPdfZipFile zipFile = generate(List.of(1L, 2L));

        assertThat(entryNames(zipFile)).containsExactly("동명이인.pdf", "동명이인(2).pdf");
    }

    @Test
    void 한_건이라도_렌더에_실패하면_전체를_실패시키고_temp_파일을_남기지_않는다() throws Exception {
        givenPdfFor(1L, "1_홍길동.pdf");
        given(pdfService.generate(2L)).willThrow(new JobApplicationNotFoundException("없음"));
        long before = countTempZips();

        assertThatThrownBy(() -> bulkService.generate(List.of(1L, 2L)))
                .isInstanceOf(JobApplicationNotFoundException.class);

        assertThat(countTempZips())
                .as("실패 시 temp zip 이 남아 있으면 안 된다")
                .isEqualTo(before);
    }

    private long countTempZips() throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.filter(path -> path.getFileName().toString().startsWith("application-pdf-bulk-")).count();
        }
    }

    private ApplicationPdfZipFile generate(List<Long> ids) {
        ApplicationPdfZipFile zipFile = bulkService.generate(ids);
        created.add(zipFile);
        return zipFile;
    }

    private void givenPdfFor(Long applicationId, String fileName) {
        willAnswer(invocation -> new ApplicationPdfDocument(
                ("%PDF-" + fileName).getBytes(StandardCharsets.UTF_8), fileName, 100L, 200L))
                .given(pdfService).generate(applicationId);
    }

    private List<String> entryNames(ApplicationPdfZipFile zipFile) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(zipFile.path())))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
