package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageContactsTest {

    @Test
    void 휴대폰은_숫자만_남겨_01로_시작하는_10에서_11자리면_유효하다() {
        assertThat(MessageContacts.isValidPhone("010-1234-5678")).isTrue();
        assertThat(MessageContacts.isValidPhone("0111234567")).isTrue();
        assertThat(MessageContacts.isValidPhone("010-12")).isFalse();
        assertThat(MessageContacts.isValidPhone("02-1234-5678")).isFalse();
        assertThat(MessageContacts.isValidPhone(null)).isFalse();
        assertThat(MessageContacts.normalizePhone(" 010-1234-5678 ")).isEqualTo("01012345678");
    }

    @Test
    void 이메일은_앞뒤_공백을_빼고_x_at_y_dot_z_형식이면_유효하다() {
        assertThat(MessageContacts.isValidEmail(" kim@example.com ")).isTrue();
        assertThat(MessageContacts.isValidEmail("not-an-email")).isFalse();
        assertThat(MessageContacts.isValidEmail("a@b")).isFalse();
        assertThat(MessageContacts.isValidEmail(null)).isFalse();
        assertThat(MessageContacts.normalizeEmail(" kim@example.com ")).isEqualTo("kim@example.com");
    }

    @Test
    void 제외_사유는_값이_없으면_NO_CONTACT_형식이_틀리면_INVALID_CONTACT() {
        assertThat(MessageContacts.skipReason(null)).isEqualTo(MessageContacts.NO_CONTACT);
        assertThat(MessageContacts.skipReason("  ")).isEqualTo(MessageContacts.NO_CONTACT);
        assertThat(MessageContacts.skipReason("010-12")).isEqualTo(MessageContacts.INVALID_CONTACT);
    }

    @Test
    void 로그용_마스킹은_앞뒤만_남긴다() {
        assertThat(MessageContacts.maskEmail("minjun.kim@example.com")).isEqualTo("m***@example.com");
        assertThat(MessageContacts.maskPhone("010-2481-3307")).isEqualTo("010-****-3307");
        assertThat(MessageContacts.maskPhone("0112345678")).isEqualTo("011-****-5678");
    }
}
