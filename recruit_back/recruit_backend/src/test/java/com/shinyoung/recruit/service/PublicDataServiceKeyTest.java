package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공공데이터포털 서비스키 정규화 테스트. 값은 실제 키가 아닌 dummy 다.
 */
class PublicDataServiceKeyTest {

    @Test
    void Encoding_표기는_한번_디코딩한다() {
        assertThat(PublicDataServiceKey.normalize("abc%2Bdef%2Fghi%3D%3D"))
                .isEqualTo("abc+def/ghi==");
    }

    @Test
    void Decoding_표기는_그대로_둔다() {
        // '+' 가 공백으로 바뀌면 안 된다.
        assertThat(PublicDataServiceKey.normalize("abc+def/ghi==")).isEqualTo("abc+def/ghi==");
    }

    @Test
    void 특수문자_없는_키는_그대로_둔다() {
        assertThat(PublicDataServiceKey.normalize("dummyServiceKey1234")).isEqualTo("dummyServiceKey1234");
    }

    @Test
    void 형식이_깨진_값은_그대로_넘긴다() {
        assertThat(PublicDataServiceKey.normalize("abc%zz")).isEqualTo("abc%zz");
    }

    @Test
    void null_은_그대로_돌려준다() {
        assertThat(PublicDataServiceKey.normalize(null)).isNull();
    }
}
