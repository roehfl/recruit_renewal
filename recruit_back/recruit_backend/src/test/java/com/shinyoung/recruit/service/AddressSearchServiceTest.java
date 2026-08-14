package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JusoProperties;
import com.shinyoung.recruit.dto.response.AddressSearchResponse;
import com.shinyoung.recruit.exception.InvalidAddressSearchRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AddressSearchService} 단위 테스트. 외부 클라이언트는 목으로 대체하고 검증/클램핑/매핑을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AddressSearchServiceTest {

    @Mock
    private JusoAddressClient jusoAddressClient;

    private JusoProperties properties;
    private AddressSearchService service;

    @BeforeEach
    void setUp() {
        properties = new JusoProperties();
        properties.setMaxCountPerPage(100);
        properties.setMaxSearchRange(9000);
        service = new AddressSearchService(jusoAddressClient, properties);
    }

    @Test
    void 검색어가_공백이면_400_예외이고_외부호출하지_않는다() {
        assertThatThrownBy(() -> service.search("   ", 1, 10))
                .isInstanceOf(InvalidAddressSearchRequestException.class);
        verify(jusoAddressClient, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void 검색어_공백을_트림하고_페이지를_클램핑해_클라이언트를_호출한다() {
        when(jusoAddressClient.search(anyString(), anyInt(), anyInt())).thenReturn(emptyRaw());

        service.search("  강남  ", 0, 999);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(jusoAddressClient).search(keyword.capture(), page.capture(), size.capture());

        assertThat(keyword.getValue()).isEqualTo("강남");
        assertThat(page.getValue()).isEqualTo(1);     // 0 -> 1
        assertThat(size.getValue()).isEqualTo(100);    // 999 -> 상한 100
    }

    @Test
    void 원본_응답을_정제_DTO로_매핑한다() {
        JusoApiResponse raw = new JusoApiResponse(new JusoApiResponse.Results(
                new JusoApiResponse.Common("2", "1", "10", "0", "정상"),
                List.of(
                        new JusoApiResponse.Juso("도로명1", "1-1", "1-2", "지번1", "eng1",
                                "07320", "서울특별시", "영등포구", "여의도동", "건물1"),
                        new JusoApiResponse.Juso("도로명2", null, null, "지번2", "eng2",
                                "06236", "서울특별시", "강남구", "역삼동", null)
                )));
        when(jusoAddressClient.search(anyString(), anyInt(), anyInt())).thenReturn(raw);

        AddressSearchResponse response = service.search("여의도", 1, 10);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.currentPage()).isEqualTo(1);
        assertThat(response.countPerPage()).isEqualTo(10);
        assertThat(response.maxPage()).isEqualTo(1);   // ceil(2/10)
        assertThat(response.addresses()).hasSize(2);
        AddressSearchResponse.AddressItem first = response.addresses().get(0);
        assertThat(first.roadAddr()).isEqualTo("도로명1");
        assertThat(first.jibunAddr()).isEqualTo("지번1");
        assertThat(first.zipNo()).isEqualTo("07320");
        assertThat(first.siNm()).isEqualTo("서울특별시");
        assertThat(first.sggNm()).isEqualTo("영등포구");
        assertThat(first.emdNm()).isEqualTo("여의도동");
        assertThat(first.bdNm()).isEqualTo("건물1");
        assertThat(first.engAddr()).isEqualTo("eng1");
    }

    @Test
    void juso_숫자필드가_비정상이면_0으로_방어한다() {
        JusoApiResponse raw = new JusoApiResponse(new JusoApiResponse.Results(
                new JusoApiResponse.Common(null, "abc", "", "0", "정상"),
                List.of()));
        when(jusoAddressClient.search(anyString(), anyInt(), anyInt())).thenReturn(raw);

        AddressSearchResponse response = service.search("강남", 1, 10);

        assertThat(response.totalCount()).isZero();
        assertThat(response.currentPage()).isZero();
        assertThat(response.countPerPage()).isZero();
        assertThat(response.maxPage()).isZero();       // countPerPage 0 → 나눗셈 방어
        assertThat(response.addresses()).isEmpty();
    }

    @Test
    void 조회_범위_상한을_넘으면_외부호출_없이_400_예외다() {
        // juso가 E0015로 거절할 요청(실측: offset 9,010부터 E0015).
        // 502(상위 장애)로 뭉개지 않고 호출 전에 400으로 막는다.
        assertThatThrownBy(() -> service.search("중앙로", 901, 10))
                .isInstanceOf(InvalidAddressSearchRequestException.class)
                .hasMessageContaining("9000");
        verify(jusoAddressClient, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void 조회_범위_상한과_정확히_같으면_통과한다() {
        when(jusoAddressClient.search(anyString(), anyInt(), anyInt())).thenReturn(emptyRaw());

        service.search("중앙로", 900, 10);   // 900 * 10 = 9000 = 상한(실측 정상)

        verify(jusoAddressClient).search("중앙로", 900, 10);
    }

    @Test
    void currentPage가_int_오버플로를_유발해도_범위_검사를_통과하지_못한다() {
        // (int) Integer.MAX_VALUE * 100 은 음수로 오버플로한다. long 캐스팅이 없으면 검사를 통과해버린다.
        assertThatThrownBy(() -> service.search("중앙로", Integer.MAX_VALUE, 100))
                .isInstanceOf(InvalidAddressSearchRequestException.class);
        verify(jusoAddressClient, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    void maxPage는_totalCount가_아니라_조회_범위_상한으로_깎인다() {
        // 실측 사례: keyword=중앙로 → totalCount 10,715 이지만 offset 9,010부터 juso가 E0015로 거절한다.
        JusoApiResponse raw = new JusoApiResponse(new JusoApiResponse.Results(
                new JusoApiResponse.Common("10715", "1", "10", "0", "정상"), List.of()));
        when(jusoAddressClient.search(anyString(), anyInt(), anyInt())).thenReturn(raw);

        AddressSearchResponse response = service.search("중앙로", 1, 10);

        assertThat(response.totalCount()).isEqualTo(10715);   // 원본은 그대로 노출
        assertThat(response.maxPage()).isEqualTo(900);        // ceil(10715/10)=1072 가 아니라 9000/10
    }

    private static JusoApiResponse emptyRaw() {
        return new JusoApiResponse(new JusoApiResponse.Results(
                new JusoApiResponse.Common("0", "1", "10", "0", "정상"),
                List.of()));
    }
}
