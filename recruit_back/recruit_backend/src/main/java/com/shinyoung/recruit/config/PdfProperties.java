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

    /**
     * 섹션을 페이지 경계에서 쪼개지 않을지 여부.
     *
     * <p>{@code true}(기본)면 섹션 하나가 남은 공간에 들어가지 않을 때 통째로 다음 페이지로 넘긴다.
     * 섹션이 잘리지 않아 읽기 쉽지만 앞 페이지에 빈 공간이 남는다. {@code false}는 행 단위로만
     * 쪼개지지 않게 해 페이지를 조밀하게 채운다. 항목 수가 적은 대부분의 지원서에서는 두 방식의
     * 결과가 같고, 표가 길어질 때만 갈린다.
     */
    private boolean keepSectionTogether = true;

    /**
     * 일괄 PDF 다운로드로 한 번에 받을 수 있는 최대 지원서 수.
     *
     * <p>기본 20은 목록 조회 API가 한 화면에 내려주는 건수와 같다. 상한의 주 목적은 서버 부하가 아니라
     * PII 반출 규모를 화면에서 선택 가능한 범위로 묶어두는 것이다.
     */
    private int bulkMaxCount = 20;

    public String getFontClasspath() {
        return fontClasspath;
    }

    public void setFontClasspath(String fontClasspath) {
        this.fontClasspath = fontClasspath;
    }

    public int getBulkMaxCount() {
        return bulkMaxCount;
    }

    public void setBulkMaxCount(int bulkMaxCount) {
        this.bulkMaxCount = bulkMaxCount;
    }

    public boolean isKeepSectionTogether() {
        return keepSectionTogether;
    }

    public void setKeepSectionTogether(boolean keepSectionTogether) {
        this.keepSectionTogether = keepSectionTogether;
    }
}
