package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Excel export 공통 설정. row cap 등 export 인프라 정책 값을 외부 설정으로 노출한다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.export")
public class ExportProperties {

    /**
     * 단일 export로 생성 가능한 최대 행 수. 생성 전 count가 이 값을 초과하면 거부한다.
     */
    @Min(1)
    private long maxRows = 50_000;

    public long getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(long maxRows) {
        this.maxRows = maxRows;
    }
}
