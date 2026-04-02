package com.therapyCommunity_Vol1.backend.global.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HangulUtilsTest {

    @Test
    void 한글_초성을_추출한다() {
        assertThat(HangulUtils.extractChoseong("언어치료")).isEqualTo("ㅇㅇㅊㄹ");
        assertThat(HangulUtils.extractChoseong("감각통합")).isEqualTo("ㄱㄱㅌㅎ");
        assertThat(HangulUtils.extractChoseong("놀이치료")).isEqualTo("ㄴㅇㅊㄹ");
    }

    @Test
    void 한글이_아닌_문자는_그대로_유지한다() {
        assertThat(HangulUtils.extractChoseong("ADHD 아동")).isEqualTo("ADHD ㅇㄷ");
        assertThat(HangulUtils.extractChoseong("3세 이상")).isEqualTo("3ㅅ ㅇㅅ");
    }

    @Test
    void 빈_문자열과_null을_처리한다() {
        assertThat(HangulUtils.extractChoseong("")).isEqualTo("");
        assertThat(HangulUtils.extractChoseong(null)).isEqualTo("");
    }

    @Test
    void 초성만으로_구성된_문자열을_판별한다() {
        assertThat(HangulUtils.isChoseongOnly("ㅇㅇㅊㄹ")).isTrue();
        assertThat(HangulUtils.isChoseongOnly("ㄱㄱㅌㅎ")).isTrue();
        assertThat(HangulUtils.isChoseongOnly("ㅎ")).isTrue();
    }

    @Test
    void 초성이_아닌_문자가_포함되면_false를_반환한다() {
        assertThat(HangulUtils.isChoseongOnly("언어")).isFalse();
        assertThat(HangulUtils.isChoseongOnly("ㅇㅇ치료")).isFalse();
        assertThat(HangulUtils.isChoseongOnly("ABC")).isFalse();
        assertThat(HangulUtils.isChoseongOnly("")).isFalse();
        assertThat(HangulUtils.isChoseongOnly(null)).isFalse();
    }
}
