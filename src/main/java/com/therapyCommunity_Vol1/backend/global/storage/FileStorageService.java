package com.therapyCommunity_Vol1.backend.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFileInfo storeTherapistVerificationImage(MultipartFile file);

    StoredFileResource loadAsResource(
            String storedPath,
            String contentType,
            String originalFilename
    );

    void delete(String storedPath);
}
