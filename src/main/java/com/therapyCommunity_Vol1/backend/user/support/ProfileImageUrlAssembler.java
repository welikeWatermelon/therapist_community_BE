package com.therapyCommunity_Vol1.backend.user.support;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 프로필 이미지 스토리지 키 ↔ 외부 노출 URL 변환을 단일 책임으로 담당.
 *
 * DB에는 스토리지 키(파일명만, 예: "abc-123.jpg")를 저장하고,
 * 응답 조립 시점에 풀 URL로 변환:
 * - S3 환경: presigned URL을 발급해 클라이언트가 S3에 직접 GET하게 함 (백엔드 프록시 없이)
 * - 로컬 환경: baseUrl + 다운로드 경로 prefix를 붙여 백엔드 download endpoint URL로 fallback
 */
@Component
public class ProfileImageUrlAssembler {

    private static final String DOWNLOAD_URL_PREFIX = "/api/v1/me/profile-image/";
    // 스토리지(S3 버킷/로컬 디스크) 내 프로필 이미지 디렉토리
    static final String STORAGE_DIR = "profile-images/";
    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    private final String baseUrl;
    private final FileStorageService fileStorageService;

    public ProfileImageUrlAssembler(
            @Value("${app.base-url}") String baseUrl,
            FileStorageService fileStorageService
    ) {
        this.baseUrl = baseUrl;
        this.fileStorageService = fileStorageService;
    }

    /**
     * DB에 저장된 파일명 → 프론트가 사용할 풀 URL.
     * null/blank → null 반환(프로필 이미지 미설정).
     * 이미 http(s)://로 시작하면 과거 데이터로 간주하고 그대로 반환(마이그레이션 실패 방어).
     * S3 환경에서는 presigned URL을 발급, 미지원 구현체(Local 등)는 백엔드 endpoint URL로 fallback.
     */
    public String toFullUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        if (storageKey.startsWith("http://") || storageKey.startsWith("https://")) {
            return storageKey;
        }
        String presignedUrl = fileStorageService.presignGet(STORAGE_DIR + storageKey, PRESIGN_TTL);
        if (presignedUrl != null) {
            return presignedUrl;
        }
        return baseUrl + DOWNLOAD_URL_PREFIX + storageKey;
    }

    /**
     * 외부에서 들어온 값(풀 URL 또는 파일명 등) → DB에 저장할 파일명만 추출.
     * 프론트가 업로드 응답으로 받은 풀 URL을 다시 보내는 케이스를 방어한다.
     * `{prefix}profile-images/uuid.jpg`, `profile-images/uuid.jpg`, `uuid.jpg` 세 케이스 모두 → `uuid.jpg`.
     */
    public String toStorageKey(String anyInput) {
        if (anyInput == null || anyInput.isBlank()) {
            return null;
        }
        String value = anyInput;
        int downloadPrefixIdx = value.indexOf(DOWNLOAD_URL_PREFIX);
        if (downloadPrefixIdx >= 0) {
            value = value.substring(downloadPrefixIdx + DOWNLOAD_URL_PREFIX.length());
        }
        if (value.startsWith(STORAGE_DIR)) {
            value = value.substring(STORAGE_DIR.length());
        }
        return value;
    }

    /**
     * 스토리지 접근(`FileStorageService.loadAsResource`)에 넘길 전체 키.
     * 파일명만 저장된 DB 값에 디렉토리 prefix를 붙인다.
     */
    public String toStoragePath(String storageKey) {
        return STORAGE_DIR + storageKey;
    }
}
