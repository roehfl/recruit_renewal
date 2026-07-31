package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JusoProperties;
import com.shinyoung.recruit.dto.response.AddressSearchResponse;
import com.shinyoung.recruit.exception.InvalidAddressSearchRequestException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 주소 검색(juso.go.kr 프록시). 파라미터 검증/클램핑 후 {@link JusoAddressClient}를 호출하고,
 * 원본 응답을 프론트 계약용 {@link AddressSearchResponse}로 정제한다.
 */
@Service
public class AddressSearchService {

    private final JusoAddressClient jusoAddressClient;
    private final JusoProperties properties;

    public AddressSearchService(JusoAddressClient jusoAddressClient, JusoProperties properties) {
        this.jusoAddressClient = jusoAddressClient;
        this.properties = properties;
    }

    /**
     * 주소를 검색한다.
     *
     * @param keyword      검색어(공백이면 400)
     * @param currentPage  현재 페이지(1 미만이면 1로 보정)
     * @param countPerPage 페이지당 개수(1 미만이면 1, 상한 초과 시 상한으로 보정)
     * @throws InvalidAddressSearchRequestException 검색어 공백, 또는 조회 범위 상한 초과(400)
     */
    public AddressSearchResponse search(String keyword, int currentPage, int countPerPage) {
        if (!StringUtils.hasText(keyword)) {
            throw new InvalidAddressSearchRequestException("검색어를 입력해 주세요.");
        }
        String trimmedKeyword = keyword.trim();
        int page = Math.max(currentPage, 1);
        int size = Math.min(Math.max(countPerPage, 1), properties.getMaxCountPerPage());

        // juso는 totalCount와 무관하게 조회 범위(page*size)에 상한을 두고, 넘으면 E0015를 반환한다.
        // 그대로 흘려보내면 JusoAddressClient가 502(상위 장애)로 뭉개 호출자 오류와 실제 juso 장애를
        // 구분할 수 없게 되므로, 외부 호출 전에 400으로 막는다.
        // long 캐스팅: currentPage가 Integer.MAX_VALUE급이면 int 곱셈이 음수로 오버플로해 검사를 통과한다.
        int maxSearchRange = properties.getMaxSearchRange();
        if ((long) page * size > maxSearchRange) {
            throw new InvalidAddressSearchRequestException(
                    "조회 가능한 검색 범위(%d건)를 초과했습니다. 검색어를 더 자세히 입력해 주세요."
                            .formatted(maxSearchRange));
        }

        JusoApiResponse raw = jusoAddressClient.search(trimmedKeyword, page, size);
        return toResponse(raw);
    }

    private AddressSearchResponse toResponse(JusoApiResponse raw) {
        JusoApiResponse.Common common = raw.results().common();
        List<JusoApiResponse.Juso> jusoList = Optional.ofNullable(raw.results().juso()).orElseGet(List::of);

        List<AddressSearchResponse.AddressItem> addresses = jusoList.stream()
                .map(j -> new AddressSearchResponse.AddressItem(
                        j.roadAddr(),
                        j.jibunAddr(),
                        j.zipNo(),
                        j.siNm(),
                        j.sggNm(),
                        j.emdNm(),
                        j.bdNm(),
                        j.engAddr()
                ))
                .toList();

        int totalCount = parseIntSafely(common.totalCount());
        int responseCountPerPage = parseIntSafely(common.countPerPage());

        return new AddressSearchResponse(
                totalCount,
                parseIntSafely(common.currentPage()),
                responseCountPerPage,
                maxPage(totalCount, responseCountPerPage),
                addresses
        );
    }

    /**
     * 실제 조회 가능한 마지막 페이지. 프론트가 {@code totalCount / countPerPage}로 페이지 수를 계산하면
     * juso 조회 범위 상한을 넘는 페이지가 만들어져 E0015가 나므로, 상한으로 한 번 더 깎아서 내려준다.
     */
    private int maxPage(int totalCount, int countPerPage) {
        if (totalCount <= 0 || countPerPage <= 0) {
            return 0;
        }
        int byTotalCount = (totalCount + countPerPage - 1) / countPerPage;
        int bySearchRange = properties.getMaxSearchRange() / countPerPage;
        return Math.min(byTotalCount, bySearchRange);
    }

    /** juso 숫자 필드는 문자열로 내려온다. 파싱 실패 시 0(방어적). */
    private static int parseIntSafely(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
