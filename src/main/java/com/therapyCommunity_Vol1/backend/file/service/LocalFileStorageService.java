package com.therapyCommunity_Vol1.backend.file.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Profile({"local", "dev"})
public class LocalFileStorageService implements FileStorageService {

    private final Path uploadRootPath;

    public LocalFileStorageService(
            @Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        try {
            this.uploadRootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(this.uploadRootPath.resolve("therapist-verifications"));
            Files.createDirectories(this.uploadRootPath.resolve("post-attachments"));
            Files.createDirectories(this.uploadRootPath.resolve("profile-images"));
            Files.createDirectories(this.uploadRootPath.resolve("knowledge-documents"));
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public StoredFileInfo storeTherapistVerificationImage(MultipartFile file) {
        return store(file, "therapist-verifications");
    }

    @Override
    public StoredFileInfo storePostAttachment(MultipartFile file) {
        validatePdf(file);
        return store(file, "post-attachments");
    }

    @Override
    public StoredFileInfo storeProfileImage(MultipartFile file) {
        validateProfileImage(file);
        return store(file, "profile-images");
    }

    private StoredFileInfo store(MultipartFile file, String directory) {
        try {
            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "unknown";

            String extension = extractExtension(originalFilename);
            String storedFilename = UUID.randomUUID() + extension;

            Path target = uploadRootPath
                    .resolve(directory)
                    .resolve(storedFilename);

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return new StoredFileInfo(
                    directory + "/" + storedFilename,
                    originalFilename,
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            );
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public StoredFileInfo storeKnowledgeDocument(MultipartFile file) {
        return store(file, "knowledge-documents");
    }

    @Override
    public InputStream loadAsStream(String storedPath) {
        try {
            Path filePath = uploadRootPath.resolve(storedPath).normalize();
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
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
                throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
            }
            Files.deleteIfExists(target);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    private String extractExtension(String originalFilename) {
        int index = originalFilename.lastIndexOf(".");
        if (index == -1) {
            return "";
        }
        return originalFilename.substring(index);
    }

    private static final long MAX_POST_ATTACHMENT_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_POST_ATTACHMENT_MIME_TYPES =
            Set.of("application/pdf");
    private static final Set<String> ALLOWED_POST_ATTACHMENT_EXTENSIONS =
            Set.of("pdf");

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_POST_ATTACHMENT);
        }

        if (file.getSize() > MAX_POST_ATTACHMENT_SIZE) {
            throw new CustomException(ErrorCode.INVALID_POST_ATTACHMENT);
        }

        String originalFilename = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (!ALLOWED_POST_ATTACHMENT_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new CustomException(ErrorCode.INVALID_POST_ATTACHMENT);
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_POST_ATTACHMENT_MIME_TYPES.contains(contentType)) {
            throw new CustomException(ErrorCode.INVALID_POST_ATTACHMENT);
        }

        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(5);
            if (header.length < 5 || !"%PDF-".equals(new String(header))) {
                throw new CustomException(ErrorCode.INVALID_POST_ATTACHMENT);
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_POST_ATTACHMENT);
        }
    }

    private static final long MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_PROFILE_IMAGE_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_PROFILE_IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "webp");

    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (file.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String originalFilename = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (!ALLOWED_PROFILE_IMAGE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PROFILE_IMAGE_MIME_TYPES.contains(contentType)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
