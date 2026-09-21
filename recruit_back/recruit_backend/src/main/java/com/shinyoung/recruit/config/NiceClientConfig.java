package com.shinyoung.recruit.config;

import com.shinyoung.recruit.service.nice.MockNiceClient;
import com.shinyoung.recruit.service.nice.NiceClient;
import com.shinyoung.recruit.service.nice.NicePlaindataCodec;
import com.shinyoung.recruit.service.nice.RealNiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link NiceClient} 구현 선택과 <b>fail-closed 가드</b>.
 *
 * <p>정확히 한 조합만 기동을 허용한다.
 * <ul>
 *   <li>mock=false + 실연동 설정 4개(siteCode·sitePassword·returnUrl·errorUrl) 모두 있음 → 실제 연동</li>
 *   <li>mock=true + siteCode 없음 → 개발</li>
 *   <li>mock=true + siteCode <b>있음</b> → 기동 거부. 운영 설정에 Mock 이 섞였다</li>
 *   <li>mock=false + 실연동 설정 중 <b>하나라도 없음</b> → 기동 거부. 실제 경로인데 설정이 빠졌다 —
 *       통과시키면 기동은 되고 첫 인증 시점에 실패한다</li>
 * </ul>
 *
 * <p>애매한 조합을 통과시키면 운영에 본인확인 우회 경로가 생긴다. 뜨지 않는 편이 낫다.
 */
@Configuration
public class NiceClientConfig {

    @Bean
    public NiceClient niceClient(NiceProperties properties, Clock clock, NicePlaindataCodec codec) {
        boolean hasSiteCode = StringUtils.hasText(properties.getSiteCode());

        if (properties.isMockEnabled() && hasSiteCode) {
            throw new IllegalStateException(
                    "recruit.nice.mock-enabled=true 인데 site-code 가 설정돼 있습니다. "
                            + "운영 설정에 Mock 이 섞였을 수 있어 기동을 중단합니다.");
        }
        if (!properties.isMockEnabled()) {
            List<String> missing = missingRealSettings(properties);
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "NICE 실제 연동 설정이 비어 있습니다: " + String.join(", ", missing) + ". "
                                + "개발이라면 recruit.nice.mock-enabled=true 로 두십시오.");
            }
        }

        return properties.isMockEnabled()
                ? new MockNiceClient(clock, codec)
                : new RealNiceClient(properties, codec);
    }

    /** 실제 연동에 필요한데 비어 있는 설정의 키 이름. 값은 자격증명이라 메시지에 넣지 않는다. */
    private List<String> missingRealSettings(NiceProperties properties) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(properties.getSiteCode())) {
            missing.add("recruit.nice.site-code");
        }
        if (!StringUtils.hasText(properties.getSitePassword())) {
            missing.add("recruit.nice.site-password");
        }
        if (!StringUtils.hasText(properties.getReturnUrl())) {
            missing.add("recruit.nice.return-url");
        }
        if (!StringUtils.hasText(properties.getErrorUrl())) {
            missing.add("recruit.nice.error-url");
        }
        return missing;
    }
}
