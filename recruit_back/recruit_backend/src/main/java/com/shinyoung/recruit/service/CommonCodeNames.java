package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.CommonCodeResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * export 1회 동안 쓰는 공통코드 표시명 캐시. 그룹마다 한 번만 조회한다(5만 행 export 에서 행마다 조회하지 않도록).
 * 미등록·비활성 코드는 코드값을 그대로 돌려준다 — PDF({@code ApplicationPdfService.codeName})와 같은 규칙(누락을 감추지 않는다).
 * 요청 스레드 하나에서만 쓰므로 동기화하지 않는다.
 */
final class CommonCodeNames {

    private final CommonCodeService commonCodeService;
    private final Map<String, Map<String, String>> cache = new HashMap<>();

    CommonCodeNames(CommonCodeService commonCodeService) {
        this.commonCodeService = commonCodeService;
    }

    String name(String groupCode, String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        Map<String, String> codes = cache.computeIfAbsent(groupCode, group -> commonCodeService.getActiveCodes(group)
                .stream()
                .collect(Collectors.toMap(CommonCodeResponse::code, CommonCodeResponse::displayName, (a, b) -> a)));
        return codes.getOrDefault(code, code);
    }
}
