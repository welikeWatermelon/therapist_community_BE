package com.therapyCommunity_Vol1.backend.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    // application.yaml 의 app.cors.mobile-origins 기본값과 동일
    private static final String DEFAULT_MOBILE_ORIGINS = "capacitor://localhost,http://localhost";

    private CorsConfiguration buildCors(String allowedOrigins) {
        return buildCors(allowedOrigins, DEFAULT_MOBILE_ORIGINS);
    }

    private CorsConfiguration buildCors(String allowedOrigins, String mobileOrigins) {
        // corsConfigurationSource()는 생성자 의존성을 사용하지 않으므로 null로 생성
        SecurityConfig config = new SecurityConfig(null, null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", allowedOrigins);
        ReflectionTestUtils.setField(config, "mobileOrigins", mobileOrigins);

        CorsConfigurationSource source = config.corsConfigurationSource();
        // "/**" 에 등록된 설정을 직접 조회 (요청 path 매칭 우회)
        return ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfigurations().get("/**");
    }

    @Test
    void capacitor_origin이_기본값으로_항상_포함된다() {
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
    void web과_mobile_origin이_겹쳐도_중복되지_않는다() {
        // allowedOrigins(web)에도 capacitor/http localhost가 있고, mobile 기본값에도 있음 → 1회만
        CorsConfiguration cors = buildCors("http://localhost:3000,capacitor://localhost,http://localhost");

        assertThat(cors.getAllowedOrigins())
                .filteredOn(o -> o.equals("capacitor://localhost"))
                .hasSize(1);
        assertThat(cors.getAllowedOrigins())
                .filteredOn(o -> o.equals("http://localhost"))
                .hasSize(1);
    }

    @Test
    void mobile_origins_프로퍼티가_비면_capacitor_origin도_없다() {
        // config-owned: 코드 상수가 아니라 프로퍼티로 제어됨을 보장
        CorsConfiguration cors = buildCors("http://localhost:3000", "");

        assertThat(cors.getAllowedOrigins())
                .doesNotContain("capacitor://localhost", "http://localhost");
    }

    @Test
    void checkOrigin은_허용_origin을_통과시키고_미허용은_차단한다() {
        CorsConfiguration cors = buildCors("http://localhost:3000");

        assertThat(cors.checkOrigin("capacitor://localhost")).isEqualTo("capacitor://localhost");
        assertThat(cors.checkOrigin("http://localhost")).isEqualTo("http://localhost");
        assertThat(cors.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(cors.checkOrigin("https://evil.example.com")).isNull();
    }
}
