package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공공데이터포털 서비스키 정규화 테스트. 값은 실제 키가 아닌 dummy 다.
 *
 * <p>어느 표기를 설정하든 쿼리스트링에는 Encoding 표기로 나가야 한다.
 */
class PublicDataServiceKeyTest {

    @Test
    void Encoding_표기는_그대로_유지된다() {
        assertThat(PublicDataServiceKey.toQueryValue("abc%2Bdef%2Fghi%3D%3D"))
                .isEqualTo("abc%2Bdef%2Fghi%3D%3D");
    }

    @Test
    void Decoding_표기는_Encoding_표기로_바뀐다() {
        assertThat(PublicDataServiceKey.toQueryValue("abc+def/ghi=="))
                .isEqualTo("abc%2Bdef%2Fghi%3D%3D");
    }

    @Test
    void 특수문자_없는_키는_그대로_둔다() {
        assertThat(PublicDataServiceKey.toQueryValue("dummyServiceKey1234")).isEqualTo("dummyServiceKey1234");
    }

    @Test
    void 형식이_깨진_값은_그대로_인코딩한다() {
        assertThat(PublicDataServiceKey.toQueryValue("abc%zz")).isEqualTo("abc%25zz");
    }

    @Test
    void 빈값은_그대로_돌려준다() {
        assertThat(PublicDataServiceKey.toQueryValue(null)).isNull();
        assertThat(PublicDataServiceKey.toQueryValue("")).isEmpty();
    }
}
