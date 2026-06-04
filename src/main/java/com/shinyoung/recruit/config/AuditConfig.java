package com.shinyoung.recruit.config;

import com.shinyoung.recruit.common.hash.AuditHmac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * 감사 인프라 설정(Phase 09a). {@link AuditHmac} 의 pepper 를 외부 설정 {@code audit.hmac-secret}
 * (= env {@code AUDIT_HMAC_SECRET})에서 주입한다. 하드코딩하지 않는다.
 *
 * <p>fail-safe: 운영(prod profile)에서 secret 이 비어 있으면 기동을 중단한다. 비운영(dev/test)에서는
 * 전용 fallback 값을 쓰되 경고를 남긴다(운영 사용 금지).
 */
@Configuration
public class AuditConfig {

    private static final Logger log = LoggerFactory.getLogger(AuditConfig.class);
    private static final String NON_PROD_FALLBACK_SECRET = "local-dev-audit-hmac-secret-change-me";

    @Bean
    public AuditHmac auditHmac(@Value("${audit.hmac-secret:}") String secret, Environment environment) {
        if (secret == null || secret.isBlank()) {
            boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (prod) {
                throw new IllegalStateException(
                        "AUDIT_HMAC_SECRET (audit.hmac-secret) must be set in production.");
            }
            log.warn("AUDIT_HMAC_SECRET not set; using non-production fallback audit HMAC secret. "
                    + "DO NOT use this in production.");
            secret = NON_PROD_FALLBACK_SECRET;
        }
        return new AuditHmac(secret);
    }
}
