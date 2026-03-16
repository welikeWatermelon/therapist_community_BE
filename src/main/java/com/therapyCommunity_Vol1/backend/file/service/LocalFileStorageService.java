package com.therapyCommunity_Vol1.backend.file.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.storage.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Profile("local")
public class LocalFileStorageService implements FileStorageService {

    private final Path uploadRootPath;

    public LocalFileStorageService(
            @Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        try {
            this.uploadRootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(this.uploadRootPath.resolve("therapist-verifications"));
        } catch (IOException e) {
            throw new RuntimeException("로컬 업로드 디렉터리 생성 실패", e);
        }
    }

    @Override
    public StoredFileInfo storeTherapistVerificationImage(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "unknown";

            String extension = extractExtension(originalFilename);
            String storedFilename = UUID.randomUUID() + extension;

            Path target = uploadRootPath
                    .resolve("therapist-verifications")
                    .resolve(storedFilename);

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return new StoredFileInfo(
                    "therapist-verifications/" + storedFilename,
                    originalFilename,
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            );
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }
    }

    @Override
    public StoredFileResource loadAsResource(
            String storedPath,
            String contentType,
            String originalFilename
    ) {
        try {
            Path filePath = uploadRootPath.resolve(storedPath).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if(!resource.exists()) {
                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
            }

            return new StoredFileResource(resource, contentType, originalFilename);
        } catch (MalformedURLException e) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public void delete(String storedPath) {
        try {
            Path target = uploadRootPath.resolve(storedPath).normalize();

            if (!target.startsWith(uploadRootPath)) {
                throw new RuntimeException("잘못된 파일 경로");
            }
            Files.deleteIfExists(target);
        } catch (Exception e) {
            throw new RuntimeException("파일 삭제 실패", e);
        }
    }

    private String extractExtension(String originalFilename) {
        int index = originalFilename.lastIndexOf(".");
        if (index == -1) {
            return "";
        }
        return originalFilename.substring(index);
    }
}
