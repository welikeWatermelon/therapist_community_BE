package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PopularCursorTest {

    @Test
    void encode_decode_라운드트립() {
        PopularCursor original = new PopularCursor(205548L, 42L);

        String encoded = original.encode();
        PopularCursor decoded = PopularCursor.decode(encoded);

        assertThat(decoded.score()).isEqualTo(original.score());
        assertThat(decoded.id()).isEqualTo(original.id());
    }

    @Test
    void 잘못된_Base64_입력시_INVALID_INPUT() {
        assertThatThrownBy(() -> PopularCursor.decode("not-valid-base64!!!"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 필수필드_누락시_INVALID_INPUT() {
        String missingId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"score\":205548}".getBytes());
        assertThatThrownBy(() -> PopularCursor.decode(missingId))
                .isInstanceOf(CustomException.class);
    }
}
