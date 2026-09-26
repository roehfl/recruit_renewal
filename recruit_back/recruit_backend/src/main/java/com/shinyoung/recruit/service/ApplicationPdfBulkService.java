package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.PdfProperties;
import com.shinyoung.recruit.exception.PdfBulkLimitExceededException;
import com.shinyoung.recruit.exception.PdfGenerationException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 운영자용 일괄 Application PDF 생성(admin 전용, read-only). 선택한 지원서를 각각 PDF 로 렌더해 zip 하나로 묶는다.
 * 개별 PDF 내용은 단건 다운로드와 완전히 같다({@link ApplicationPdfService} 재사용).
 *
 * <p><b>zip 을 temp 파일에 다 만든 뒤에 응답을 시작한다.</b> 스트리밍이 시작되면 이미 200 이 전송되어
 * 중간 실패를 오류 상태코드로 되돌릴 수 없기 때문이다. 같은 이유로 렌더 실패는 fail-fast 로 전체를 실패시킨다.
 *
 * <p>메모리에는 한 번에 PDF 한 건분만 올린다(렌더 즉시 zip 엔트리로 흘려보낸다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationPdfBulkService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ApplicationPdfService applicationPdfService;
    private final PdfProperties pdfProperties;
    private final Clock clock;

    public ApplicationPdfZipFile generate(List<Long> applicationIds) {
        List<Long> targets = distinct(applicationIds);
        int maxCount = pdfProperties.getBulkMaxCount();
        if (targets.size() > maxCount) {
            throw new PdfBulkLimitExceededException(targets.size(), maxCount);
        }

        Path tempFile = createTempFile();
        Set<String> usedNames = new LinkedHashSet<>();
        List<ApplicationPdfZipFile.Entry> entries = new ArrayList<>();
        try (OutputStream out = Files.newOutputStream(tempFile);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Long applicationId : targets) {
                ApplicationPdfDocument document = applicationPdfService.generate(applicationId);
                zip.putNextEntry(new ZipEntry(uniqueEntryName(usedNames, document.fileName())));
                zip.write(document.content());
                zip.closeEntry();
                // 감사는 건별로 남기므로 PDF 내용은 버리고 식별자만 모아둔다.
                entries.add(new ApplicationPdfZipFile.Entry(
                        applicationId, document.jobPostingId(), document.jobPositionId()));
            }
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new PdfGenerationException("지원서 PDF 압축에 실패했습니다.", e);
        } catch (RuntimeException e) {
            // 렌더/조회 실패는 원래 예외(404/500 매핑)를 그대로 전파하고 temp 파일만 정리한다.
            deleteQuietly(tempFile);
            throw e;
        }

        return new ApplicationPdfZipFile(tempFile, buildFileName("applications-", ".zip"), entries);
    }

    /**
     * 인쇄용: 선택한 지원서 PDF 를 선택 순서대로 이어 붙여 PDF 한 개로 만든다. 각 지원서 내용은 단건 PDF 와 같다.
     * zip 과 같은 이유로 temp 파일에 다 만든 뒤 응답하고, 메모리에는 렌더 중인 한 건만 올린다
     * (렌더 결과는 건별 temp 파일로 흘려보내고 합칠 때도 temp 파일 버퍼를 쓴다).
     */
    public ApplicationPdfPrintFile generateMerged(List<Long> applicationIds) {
        List<Long> targets = distinct(applicationIds);
        int maxCount = pdfProperties.getBulkMaxCount();
        if (targets.size() > maxCount) {
            throw new PdfBulkLimitExceededException(targets.size(), maxCount);
        }

        List<Path> parts = new ArrayList<>();
        Path merged = createTempFile("application-pdf-print-", ".pdf");
        List<ApplicationPdfZipFile.Entry> entries = new ArrayList<>();
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (Long applicationId : targets) {
                ApplicationPdfDocument document = applicationPdfService.generate(applicationId);
                Path part = createTempFile("application-pdf-part-", ".pdf");
                parts.add(part);
                Files.write(part, document.content());
                merger.addSource(part.toFile());
                entries.add(new ApplicationPdfZipFile.Entry(
                        applicationId, document.jobPostingId(), document.jobPositionId()));
            }
            try (OutputStream out = Files.newOutputStream(merged)) {
                merger.setDestinationStream(out);
                merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
            }
        } catch (IOException e) {
            deleteQuietly(merged);
            throw new PdfGenerationException("인쇄용 지원서 PDF 생성에 실패했습니다.", e);
        } catch (RuntimeException e) {
            deleteQuietly(merged);
            throw e;
        } finally {
            parts.forEach(this::deleteQuietly);
        }

        return new ApplicationPdfPrintFile(merged, buildFileName("applications-print-", ".pdf"), entries);
    }

    /** 같은 id 를 반복 전송해 상한을 우회하지 못하도록 중복을 제거한다. 선택 순서는 유지한다. */
    private List<Long> distinct(List<Long> applicationIds) {
        if (applicationIds == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(applicationIds.stream().filter(Objects::nonNull).toList()));
    }

    /**
     * zip 안에서 같은 이름이 겹치면 뒤엣것이 덮이므로 접미사를 붙인다.
     * 파일명이 {@code {수험번호}_{이름}.pdf} 라 보통은 겹치지 않지만 방어적으로 확인한다.
     */
    private String uniqueEntryName(Set<String> usedNames, String fileName) {
        String candidate = fileName;
        int suffix = 2;
        while (!usedNames.add(candidate)) {
            int dot = fileName.lastIndexOf('.');
            String base = dot < 0 ? fileName : fileName.substring(0, dot);
            String extension = dot < 0 ? "" : fileName.substring(dot);
            candidate = base + "(" + suffix + ")" + extension;
            suffix++;
        }
        return candidate;
    }

    private String buildFileName(String prefix, String extension) {
        return prefix + LocalDateTime.now(clock).format(FILE_TIMESTAMP) + extension;
    }

    private Path createTempFile() {
        return createTempFile("application-pdf-bulk-", ".zip");
    }

    private Path createTempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw new PdfGenerationException("지원서 PDF 임시 파일 생성에 실패했습니다.", e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // temp 파일 정리 실패는 원인 예외 전파를 막지 않는다.
        }
    }
}
