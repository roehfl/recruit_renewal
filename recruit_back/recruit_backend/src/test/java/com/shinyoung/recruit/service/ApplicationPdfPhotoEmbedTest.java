package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.ApplicationPdfView;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.BasicInfo;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사진 embed 회귀(S2). openhtmltopdf 가 {@code data:} 스킴 이미지를 실제로 그리는지 고정한다.
 * 이 동작이 깨지면 사진 칸이 조용히 비므로, PDF 안에 이미지 XObject 가 실렸는지로 검증한다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class ApplicationPdfPhotoEmbedTest {

    @Autowired
    private ApplicationPdfRenderer renderer;

    @Test
    void data_URI_사진이_PDF에_이미지로_embed된다() throws Exception {
        byte[] pdf = renderer.render(viewWithPhoto(pngDataUri()));

        assertThat(countImages(pdf))
                .as("사진 data URI 가 PDF 이미지로 변환되어야 한다")
                .isPositive();
    }

    @Test
    void 사진이_없으면_이미지_없이_렌더된다() throws Exception {
        byte[] pdf = renderer.render(viewWithPhoto(null));

        assertThat(countImages(pdf)).isZero();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    private int countImages(byte[] pdf) throws Exception {
        int count = 0;
        try (PDDocument document = PDDocument.load(pdf)) {
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                var resources = document.getPage(page).getResources();
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(name);
                    if (xObject instanceof PDImageXObject) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** 26mm x 34mm 사진 자리에 들어갈 최소 크기의 실제 PNG. */
    private String pngDataUri() throws Exception {
        BufferedImage image = new BufferedImage(120, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(90, 120, 160));
        graphics.fillRect(0, 0, 120, 160);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private ApplicationPdfView viewWithPhoto(String photoDataUri) {
        ApplicationPdfView.Header header = new ApplicationPdfView.Header(
                1L, "홍길동", "공개채용", "신입", "리테일 영업", "본사", "SUBMITTED", "2026-08-30 17:42");
        BasicInfo basicInfo = new BasicInfo(
                photoDataUri, "홍길동", "HONG GILDONG", "010-1234-5678", "",
                "내국인", "비대상", "1997-03-15", "비대상", "hong@example.com", "서울시 영등포구");
        return new ApplicationPdfView(header, basicInfo, List.of());
    }
}
