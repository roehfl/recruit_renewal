package com.shinyoung.recruit.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Excel upload(StageResult) 공통 설정. write path이므로 export보다 보수적인 행수/파일 크기 한도를 외부 설정으로 둔다.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "recruit.upload")
public class UploadProperties {

    /**
     * 단일 upload로 처리 가능한 최대 데이터 행 수. 초과 시 파일 전체를 거부한다.
     */
    @Min(1)
    private long maxRows = 10_000;

    /**
     * upload 파일의 최대 크기. 초과 시 파일 전체를 거부한다.
     */
    private DataSize maxFileSize = DataSize.ofMegabytes(5);

    public long getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(long maxRows) {
        this.maxRows = maxRows;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
}
