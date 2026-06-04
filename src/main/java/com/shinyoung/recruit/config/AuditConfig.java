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
 * <p>fail-safe(9a 리뷰 보완 — profile 이름 의존 제거): secret 이 비어 있으면 기본적으로 기동을 중단한다.
 * fallback 은 {@code audit.allow-fallback-secret=true}(= env {@code AUDIT_ALLOW_FALLBACK_SECRET})를
 * <b>명시적으로</b> 켠 local/test 환경에서만 허용한다(경고 로그). 운영 profile 이름이 {@code prod} 가 아니어도
 * (prd/real/production 등) secret 누락이 조용히 fallback 으로 넘어가지 않는다.
 *
 * <p>이중 가드: flag 가 잘못 켜져 있어도 active profile 에 {@code prod} 가 있으면 fallback 을 거부한다.
 */
@Configuration
public class AuditConfig {

    private static final Logger log = LoggerFactory.getLogger(AuditConfig.class);
    private static final String NON_PROD_FALLBACK_SECRET = "local-dev-audit-hmac-secret-change-me";

    @Bean
    public AuditHmac auditHmac(
            @Value("${audit.hmac-secret:}") String secret,
            @Value("${audit.allow-fallback-secret:false}") boolean allowFallbackSecret,
            Environment environment
    ) {
        if (secret == null || secret.isBlank()) {
            boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (prod || !allowFallbackSecret) {
                throw new IllegalStateException(
                        "AUDIT_HMAC_SECRET (audit.hmac-secret) must be set. "
                                + "Fallback secret is allowed only when audit.allow-fallback-secret=true "
                                + "(AUDIT_ALLOW_FALLBACK_SECRET) in non-production environments.");
            }
            log.warn("AUDIT_HMAC_SECRET not set; using non-production fallback audit HMAC secret "
                    + "(audit.allow-fallback-secret=true). DO NOT use this in production.");
            secret = NON_PROD_FALLBACK_SECRET;
        }
        return new AuditHmac(secret);
    }
}
