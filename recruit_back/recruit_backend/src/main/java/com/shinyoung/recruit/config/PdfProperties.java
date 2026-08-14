package com.shinyoung.recruit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application PDF 렌더링 설정. CJK 폰트 임베드용 리소스 경로/패밀리를 외부 설정으로 둔다.
 *
 * <p>CJK 폰트(.ttf)는 {@code src/main/resources/fonts/}에 번들되어 jar/classpath로 배포되므로 컨테이너
 * 시스템 폰트가 없어도 한글이 출력된다. 기본값은 정적 폰트 {@code NanumGothic-Regular.ttf}(SIL OFL 1.1)이며,
 * 변수폰트({@code NotoSansKR[wght].ttf})는 PDFBox 2.x 호환성 이슈로 기본값에서 제외한다. 렌더러는 이 폰트를
 * 고정 패밀리({@code ApplicationPdfFont})로 등록하고 템플릿 CSS가 같은 이름을 참조한다.
 */
@Component
@ConfigurationProperties(prefix = "recruit.pdf")
public class PdfProperties {

    /** 임베드할 CJK 폰트의 classpath 경로(번들된 SIL OFL 폰트). */
    private String fontClasspath = "fonts/NanumGothic-Regular.ttf";

    public String getFontClasspath() {
        return fontClasspath;
    }

    public void setFontClasspath(String fontClasspath) {
        this.fontClasspath = fontClasspath;
    }
}
