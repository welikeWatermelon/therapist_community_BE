package com.therapyCommunity_Vol1.backend.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    private CorsConfiguration buildCors(String allowedOrigins) {
        // corsConfigurationSource()는 생성자 의존성을 사용하지 않으므로 null로 생성
        SecurityConfig config = new SecurityConfig(null, null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", allowedOrigins);

        CorsConfigurationSource source = config.corsConfigurationSource();
        // "/**" 에 등록된 설정을 직접 조회 (요청 path 매칭 우회)
        return ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfigurations().get("/**");
    }

    @Test
    void capacitor_origin이_property와_무관하게_항상_포함된다() {
        CorsConfiguration cors = buildCors("http://localhost:3000,http://127.0.0.1:3000");

        assertThat(cors.getAllowedOrigins())
                .contains("capacitor://localhost", "http://localhost");
    }

    @Test
    void 기존_web_origin은_그대로_유지된다() {
        CorsConfiguration cors = buildCors("http://localhost:3000,http://127.0.0.1:3000");

        assertThat(cors.getAllowedOrigins())
                .contains("http://localhost:3000", "http://127.0.0.1:3000");
    }

    @Test
    void allowCredentials는_true를_유지한다() {
        CorsConfiguration cors = buildCors("http://localhost:3000");

        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void 와일드카드는_사용하지_않는다() {
        CorsConfiguration cors = buildCors("http://localhost:3000");

        assertThat(cors.getAllowedOrigins()).doesNotContain("*");
    }

    @Test
    void env에_이미_capacitor_origin이_있어도_중복되지_않는다() {
        CorsConfiguration cors = buildCors("http://localhost:3000,capacitor://localhost,http://localhost");

        assertThat(cors.getAllowedOrigins())
                .filteredOn(o -> o.equals("capacitor://localhost"))
                .hasSize(1);
        assertThat(cors.getAllowedOrigins())
                .filteredOn(o -> o.equals("http://localhost"))
                .hasSize(1);
    }
}
