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
}
