package com.shinyoung.recruit.config;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.context.annotation.Configuration;

/**
 * 업로드 xlsx(전형결과·면접 일정·학교 import)의 압축 폭탄 방어.
 *
 * <p>파서는 {@code XSSFWorkbook}으로 파일 전체를 메모리에 올린다. POI 기본값은 압축 비율(1%)만 보고 항목 크기는
 * 4GB까지 허용해, 5MB 파일이 수백 MB XML로 풀려 힙을 소진할 수 있다. 업로드는 1만 행 상한이라 시트·공유 문자열
 * XML이 수 MB 수준이므로 항목당 20MB로 막는다. 값은 JVM 전역(static)이다.
 */
@Configuration
public class PoiSecurityConfig {

    static final long MAX_ENTRY_SIZE_BYTES = 20L * 1024 * 1024;

    public PoiSecurityConfig() {
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_SIZE_BYTES);
    }
}
