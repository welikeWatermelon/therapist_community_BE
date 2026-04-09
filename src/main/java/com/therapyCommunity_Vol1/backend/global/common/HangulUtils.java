package com.therapyCommunity_Vol1.backend.global.common;

public final class HangulUtils {

    private static final char HANGUL_BEGIN = 0xAC00; // '가'
    private static final char HANGUL_END = 0xD7A3;   // '힣'
    private static final int JUNGSEONG_COUNT = 21;
    private static final int JONGSEONG_COUNT = 28;

    private static final char[] CHOSEONG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
            'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private HangulUtils() {}

    /**
     * 한글 문자열에서 초성만 추출한다.
     * 한글이 아닌 문자는 그대로 유지한다.
     * "언어치료" → "ㅇㅇㅊㄹ"
     */
    public static String extractChoseong(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHangulSyllable(c)) {
                int index = (c - HANGUL_BEGIN) / (JUNGSEONG_COUNT * JONGSEONG_COUNT);
                sb.append(CHOSEONG[index]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 문자열이 모두 초성(자음)으로만 구성되어 있는지 판별한다.
     * "ㅇㅇㅊㄹ" → true, "언어" → false
     */
    public static boolean isChoseongOnly(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!isChoseong(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHangulSyllable(char c) {
        return c >= HANGUL_BEGIN && c <= HANGUL_END;
    }

    private static boolean isChoseong(char c) {
        for (char ch : CHOSEONG) {
            if (ch == c) return true;
        }
        return false;
    }
}
