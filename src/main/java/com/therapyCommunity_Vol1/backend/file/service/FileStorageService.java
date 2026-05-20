package com.therapyCommunity_Vol1.backend.file.service;

import com.therapyCommunity_Vol1.backend.file.dto.S3ObjectMeta;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorageService {
    StoredFileInfo storeTherapistVerificationImage(MultipartFile file);

    StoredFileInfo storePostAttachment(MultipartFile file);

    StoredFileInfo storePostImage(MultipartFile file);

    StoredFileInfo storeProfileImage(MultipartFile file);

    StoredFileInfo storeKnowledgeDocument(MultipartFile file);

    StoredFileResource loadAsResource(
            String storedPath,
            String contentType,
            String originalFilename
    );

    InputStream loadAsStream(String storedPath);

    void delete(String storedPath);

    /**
     * 클라이언트가 직접 GET할 수 있는 임시 URL을 발급.
     * S3 환경에서는 presigned URL을 반환하여 백엔드 프록시 없이 S3에 직접 접근.
     * 로컬 등 미지원 구현체는 null을 반환하면 호출부가 백엔드 download endpoint로 fallback.
     */
    default String presignGet(String storedPath, Duration ttl) {
        return null;
    }

    /**
     * 클라이언트가 직접 PUT할 수 있는 임시 업로드 URL을 발급.
     * 호출부는 storedPath/contentType 을 미리 결정해 넘기고, 클라는 같은 Content-Type 헤더로 PUT 해야 함.
     * 로컬 등 미지원 구현체는 null 반환 → 호출부가 503 으로 거절.
     */
    default String presignPut(String storedPath, String contentType, Duration ttl) {
        return null;
    }

    /**
     * 객체 메타(size, contentType) 조회. 미지원/존재하지 않으면 null.
     * confirm 단계에서 클라가 신고한 size 가 실제 size 와 일치하는지 재검증할 때 사용.
     */
    default S3ObjectMeta headObject(String storedPath) {
        return null;
    }

    /**
     * 같은 버킷 내 object 복사. uploads-pending/ → 최종 prefix 이동에 사용.
     * 미지원 구현체는 UnsupportedOperationException — confirm 흐름은 S3 환경에서만 동작.
     */
    default void copy(String fromKey, String toKey) {
        throw new UnsupportedOperationException("copy is not supported by this storage backend");
    }

    /**
     * 객체의 첫 maxBytes 바이트를 반환. magic byte 검증에 사용.
     * 미지원/오류 시 빈 배열 반환 — 호출부가 UPLOAD_MIME_MISMATCH 로 처리.
     */
    default byte[] getFirstBytes(String storedPath, int maxBytes) {
        return new byte[0];
    }

    /**
     * 객체의 마지막 maxBytes 바이트를 반환. MP4 mvhd 가 파일 끝에 있는 비-faststart 영상에서 사용.
     * 미지원/오류 시 빈 배열 반환.
     */
    default byte[] getLastBytes(String storedPath, int maxBytes) {
        return new byte[0];
    }
}
