package com.therapyCommunity_Vol1.backend.file.service;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageService {
    StoredFileInfo storeTherapistVerificationImage(MultipartFile file);

    StoredFileInfo storePostAttachment(MultipartFile file);

    StoredFileInfo storeProfileImage(MultipartFile file);

    StoredFileInfo storeKnowledgeDocument(MultipartFile file);

    StoredFileResource loadAsResource(
            String storedPath,
            String contentType,
            String originalFilename
    );

    InputStream loadAsStream(String storedPath);

    void delete(String storedPath);
}
