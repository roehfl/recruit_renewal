package com.shinyoung.recruit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application PDF 렌더링 설정. CJK 폰트 임베드용 리소스 경로/패밀리를 외부 설정으로 둔다.
 *
 * <p>폰트 바이너리(.ttf)는 저장소에 포함하지 않으며, 운영/개발 환경에서 {@code fontClasspath} 위치에
 * SIL OFL 1.1 폰트(예: Noto Sans KR, 나눔고딕)를 배치한다. 폰트가 없으면 임베드를 건너뛰고(경고 로그)
 * ASCII는 기본 폰트로 렌더된다(한글은 환경 폰트에 의존).
 */
@Component
@ConfigurationProperties(prefix = "recruit.pdf")
public class PdfProperties {

    /** 임베드할 CJK 폰트의 classpath 경로(없으면 임베드 생략). */
    private String fontClasspath = "fonts/NotoSansKR-Regular.ttf";

    /** 템플릿 CSS의 font-family와 일치시키는 폰트 패밀리명. */
    private String fontFamily = "Noto Sans KR";

    public String getFontClasspath() {
        return fontClasspath;
    }

    public void setFontClasspath(String fontClasspath) {
        this.fontClasspath = fontClasspath;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }
}
