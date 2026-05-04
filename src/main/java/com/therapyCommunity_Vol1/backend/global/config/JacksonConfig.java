package com.therapyCommunity_Vol1.backend.global.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    /**
     * 도메인은 LocalDateTime(KST 시각)으로 저장하되, JSON 응답에는 항상 +09:00 offset을 붙여
     * 클라이언트가 timezone-aware로 파싱할 수 있게 한다.
     * MEL-41: FE가 "Z 부착 보정"으로 우회하던 9시간 어긋남을 백엔드 직렬화에서 정정.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer kstOffsetLocalDateTimeCustomizer() {
        return builder -> builder.serializerByType(LocalDateTime.class, new KstOffsetLocalDateTimeSerializer());
    }

    private static final class KstOffsetLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        private static final ZoneOffset KST = ZoneOffset.ofHours(9);

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            OffsetDateTime withOffset = value.atOffset(KST);
            gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(withOffset));
        }
    }
}
