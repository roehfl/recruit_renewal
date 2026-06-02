package com.shinyoung.recruit.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.shinyoung.recruit.config.PdfProperties;
import com.shinyoung.recruit.dto.response.ApplicationPdfView;
import com.shinyoung.recruit.exception.PdfGenerationException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Application PDF 렌더러. Thymeleaf로 XHTML을 만들고 jsoup으로 well-formed XML로 정규화한 뒤
 * openhtmltopdf(PDFBox)로 PDF byte[]를 생성한다.
 *
 * <p>보안: 템플릿은 applicant free-text를 {@code th:text}로만 출력하고 외부 resource를 로드하지 않는다.
 * 폰트는 번들된 local 리소스만 사용한다(외부 URL 금지). CJK 폰트가 classpath에 있으면 임베드하고,
 * 없으면 임베드를 생략한다(경고 로그; ASCII는 기본 폰트로 렌더, 한글은 환경 폰트에 의존).
 */
@Component
@RequiredArgsConstructor
public class ApplicationPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(ApplicationPdfRenderer.class);
    private static final String TEMPLATE_NAME = "application-pdf";

    private final TemplateEngine templateEngine;
    private final PdfProperties pdfProperties;

    public byte[] render(ApplicationPdfView view) {
        String html = renderHtml(view);
        String xhtml = toXhtml(html);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            applyFont(builder);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new PdfGenerationException("지원서 PDF 생성에 실패했습니다.", e);
        }
    }

    private String renderHtml(ApplicationPdfView view) {
        Context context = new Context(Locale.KOREA);
        context.setVariable("header", view.header());
        context.setVariable("sections", view.sections());
        return templateEngine.process(TEMPLATE_NAME, context);
    }

    /**
     * Thymeleaf HTML 출력을 jsoup으로 파싱 후 XML 문법으로 직렬화해 openhtmltopdf가 요구하는 well-formed
     * XHTML을 보장한다(미닫힌 태그/void 요소 정규화).
     */
    private String toXhtml(String html) {
        Document document = Jsoup.parse(html);
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .prettyPrint(false);
        return document.html();
    }

    private void applyFont(PdfRendererBuilder builder) {
        ClassPathResource font = new ClassPathResource(pdfProperties.getFontClasspath());
        if (!font.exists()) {
            log.warn("PDF CJK 폰트를 classpath:{} 에서 찾지 못해 임베드를 생략합니다. 한글 출력을 위해 SIL OFL 폰트를 배치하세요.",
                    pdfProperties.getFontClasspath());
            return;
        }
        builder.useFont(() -> {
            try {
                return font.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, pdfProperties.getFontFamily());
    }
}
