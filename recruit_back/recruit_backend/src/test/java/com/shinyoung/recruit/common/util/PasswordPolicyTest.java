package com.shinyoung.recruit.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void 세_종류_이상이면_8자부터_통과한다() {
        assertThat(PasswordPolicy.isAcceptable("Abcdef1!")).isTrue();
        assertThat(PasswordPolicy.isAcceptable("abcdef1!")).isTrue();
        assertThat(PasswordPolicy.isAcceptable("Abcde1!")).as("7자").isFalse();
    }

    @Test
    void 두_종류면_10자부터_통과한다() {
        assertThat(PasswordPolicy.isAcceptable("abcdefgh12")).isTrue();
        assertThat(PasswordPolicy.isAcceptable("abcdefg12")).as("9자").isFalse();
        assertThat(PasswordPolicy.isAcceptable("ABCDEFGHij")).isTrue();
    }

    @Test
    void 한_종류는_길어도_거부한다() {
        assertThat(PasswordPolicy.isAcceptable("abcdefghijklmnop")).isFalse();
        assertThat(PasswordPolicy.isAcceptable("12345678901234")).isFalse();
    }

    @Test
    void 공백은_종류로_세지_않고_한글은_특수문자로_센다() {
        assertThat(PasswordPolicy.isAcceptable("abcd efgh ij")).as("영문 소문자 1종").isFalse();
        assertThat(PasswordPolicy.isAcceptable("비밀번호abcd12")).isTrue();
    }

    @Test
    void null은_통과시킨다() {
        assertThat(PasswordPolicy.isAcceptable(null)).isTrue();
    }
}
