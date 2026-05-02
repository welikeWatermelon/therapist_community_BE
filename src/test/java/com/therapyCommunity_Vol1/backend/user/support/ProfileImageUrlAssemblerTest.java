package com.therapyCommunity_Vol1.backend.user.support;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProfileImageUrlAssemblerTest {

    // mock의 default presignGet은 null이라 fallback(baseUrl + endpoint) 경로로 동작.
    // S3 환경의 presigned URL 동작은 통합 테스트에서 검증.
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ProfileImageUrlAssembler assembler = new ProfileImageUrlAssembler("http://localhost:8080", fileStorageService);

    @Test
    void toFullUrl_파일명만_저장된_경우_풀_URL로_조립() {
        String url = assembler.toFullUrl("abc.jpg");
        assertThat(url).isEqualTo("http://localhost:8080/api/v1/me/profile-image/abc.jpg");
    }

    @Test
    void toFullUrl_null_또는_blank는_null_반환() {
        assertThat(assembler.toFullUrl(null)).isNull();
        assertThat(assembler.toFullUrl("")).isNull();
        assertThat(assembler.toFullUrl("   ")).isNull();
    }

    @Test
    void toFullUrl_이미_http로_시작하면_그대로_반환() {
        String legacy = "http://legacy.example.com/api/v1/me/profile-image/profile-images/old.jpg";
        assertThat(assembler.toFullUrl(legacy)).isEqualTo(legacy);
    }

    @Test
    void toStorageKey_파일명만_들어오면_그대로_반환() {
        assertThat(assembler.toStorageKey("abc.jpg")).isEqualTo("abc.jpg");
    }

    @Test
    void toStorageKey_profile_images_prefix를_벗겨낸다() {
        assertThat(assembler.toStorageKey("profile-images/abc.jpg")).isEqualTo("abc.jpg");
    }

    @Test
    void toStorageKey_풀_URL에서_파일명만_추출한다() {
        String full = "http://localhost:8080/api/v1/me/profile-image/profile-images/abc.jpg";
        assertThat(assembler.toStorageKey(full)).isEqualTo("abc.jpg");
    }

    @Test
    void toStorageKey_파일명만_있는_신형_풀_URL에서도_파일명_추출() {
        String full = "http://localhost:8080/api/v1/me/profile-image/abc.jpg";
        assertThat(assembler.toStorageKey(full)).isEqualTo("abc.jpg");
    }

    @Test
    void toStorageKey_null_또는_blank는_null() {
        assertThat(assembler.toStorageKey(null)).isNull();
        assertThat(assembler.toStorageKey("")).isNull();
    }

    @Test
    void toStoragePath는_디렉토리_prefix를_붙인다() {
        assertThat(assembler.toStoragePath("abc.jpg")).isEqualTo("profile-images/abc.jpg");
    }
}
