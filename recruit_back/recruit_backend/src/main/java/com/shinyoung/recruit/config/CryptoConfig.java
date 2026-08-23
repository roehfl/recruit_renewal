package com.shinyoung.recruit.config;

import com.shinyoung.recruit.common.crypto.AesCryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    @Bean
    public AesCryptoUtil aesCryptoUtil(@Value("${crypto.aes.key}") String key) {
        return new AesCryptoUtil(key);
    }
}
