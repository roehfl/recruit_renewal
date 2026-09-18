package com.shinyoung.recruit.common.util;

public final class HtmlTextUtils {
    private static final int MAX_SEARCH_TEXT_LENGTH = 1000;

    private HtmlTextUtils() {

    }

    public static String extractText(String html) {
        if(html == null || html.isBlank()) {
            return "";
        }

        String text = org.jsoup.Jsoup.parse(html).text();
        if(text.length() > MAX_SEARCH_TEXT_LENGTH) {
            return text.substring(0, MAX_SEARCH_TEXT_LENGTH);
        }

        return text;
    }

    // v-html 로 렌더링되는 본문용. script·이벤트 핸들러 속성·javascript: 링크를 제거하고 기본 서식은 유지한다.
    public static String sanitize(String html) {
        if(html == null) {
            return null;
        }

        return org.jsoup.Jsoup.clean(html, "", org.jsoup.safety.Safelist.relaxed(),
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
    }
}
