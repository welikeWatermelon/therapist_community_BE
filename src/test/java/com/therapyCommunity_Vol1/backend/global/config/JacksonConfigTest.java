package com.therapyCommunity_Vol1.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MEL-41: LocalDateTime 응답 직렬화에 +09:00 offset이 붙는지 검증.
 * Jackson2ObjectMapperBuilderCustomizer가 적용된 ObjectMapper로 테스트.
 */
class JacksonConfigTest {

    private final ObjectMapper objectMapper = buildObjectMapper();

    private ObjectMapper buildObjectMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().kstOffsetLocalDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void localDateTime은_KST_offset이_붙어_직렬화된다() throws Exception {
        LocalDateTime created = LocalDateTime.of(2026, 5, 2, 14, 30, 15, 123_000_000);

        String json = objectMapper.writeValueAsString(new TimeWrapper(created));

        assertThat(json).contains("+09:00");
        assertThat(json).contains("2026-05-02T14:30:15.123+09:00");
    }

    record TimeWrapper(LocalDateTime createdAt) {}
}
