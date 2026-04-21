package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostCursorTest {

    @Test
    void encode_decode_라운드트립() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 9, 12, 0, 0, 123456000);
        PostCursor original = new PostCursor(now, 42L);

        String encoded = original.encode();
        PostCursor decoded = PostCursor.decode(encoded);

        assertThat(decoded.createdAt()).isEqualTo(original.createdAt());
        assertThat(decoded.id()).isEqualTo(original.id());
    }

    @Test
    void 잘못된_Base64_입력시_INVALID_INPUT() {
        assertThatThrownBy(() -> PostCursor.decode("not-valid-base64!!!"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 잘못된_JSON_입력시_INVALID_INPUT() {
        String badJson = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{bad json}".getBytes());
        assertThatThrownBy(() -> PostCursor.decode(badJson))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 필수필드_누락시_INVALID_INPUT() {
        String missingId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"createdAt\":\"2026-04-09T12:00:00.000000\"}".getBytes());
        assertThatThrownBy(() -> PostCursor.decode(missingId))
                .isInstanceOf(CustomException.class);
    }
}
