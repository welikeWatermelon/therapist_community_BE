package com.therapyCommunity_Vol1.backend.file.service;

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
}
