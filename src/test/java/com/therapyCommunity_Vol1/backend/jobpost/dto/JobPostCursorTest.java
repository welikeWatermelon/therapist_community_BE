package com.therapyCommunity_Vol1.backend.jobpost.dto;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostCursorTest {

    @Test
    void encode_decode_라운드트립() {
        JobPostCursor cursor = new JobPostCursor(LocalDate.of(2026, 6, 30), 42L);

        JobPostCursor decoded = JobPostCursor.decode(cursor.encode());

        assertThat(decoded.deadlineDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(decoded.id()).isEqualTo(42L);
    }

    @Test
    void 잘못된_커서는_예외다() {
        assertThatThrownBy(() -> JobPostCursor.decode("!!!not-base64!!!"))
                .isInstanceOf(CustomException.class);
    }
}
