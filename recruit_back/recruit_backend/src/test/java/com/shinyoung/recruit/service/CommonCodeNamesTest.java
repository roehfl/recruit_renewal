package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.CommonCodeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CommonCodeNamesTest {

    @Mock
    private CommonCodeService commonCodeService;

    @Test
    void looks_up_each_group_once_and_falls_back_to_the_raw_code() {
        given(commonCodeService.getActiveCodes("APPLICATION_ROUTE")).willReturn(List.of(
                new CommonCodeResponse(1L, "APPLICATION_ROUTE", "WEB", "홈페이지", 1, true, null)));
        CommonCodeNames names = new CommonCodeNames(commonCodeService);

        assertThat(names.name("APPLICATION_ROUTE", "WEB")).isEqualTo("홈페이지");
        assertThat(names.name("APPLICATION_ROUTE", "WEB")).isEqualTo("홈페이지");
        // 미등록 코드는 누락을 감추지 않고 코드값 그대로(PDF 와 같은 규칙)
        assertThat(names.name("APPLICATION_ROUTE", "UNKNOWN")).isEqualTo("UNKNOWN");

        verify(commonCodeService, times(1)).getActiveCodes("APPLICATION_ROUTE");
    }

    @Test
    void blank_code_returns_empty_without_lookup() {
        CommonCodeNames names = new CommonCodeNames(commonCodeService);

        assertThat(names.name("NATIONALITY", null)).isEmpty();
        assertThat(names.name("NATIONALITY", " ")).isEmpty();
        verifyNoInteractions(commonCodeService);
    }
}
