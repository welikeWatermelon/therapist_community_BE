package com.therapyCommunity_Vol1.backend.file.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@ConditionalOnProperty(
    name = "app.aws.enabled",
    havingValue = "true",
    matchIfMissing = false
)
@Primary
@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Override
    public StoredFileInfo storeTherapistVerificationImage(MultipartFile file) {
        try {
            validateImage(file);
            return store(file, "therapist-verifications");
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public StoredFileInfo storePostAttachment(MultipartFile file) {
        try {
            validatePdf(file);
            return store(file, "post-attachments");
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public StoredFileInfo storeProfileImage(MultipartFile file) {
        try {
            validateImage(file);
            return store(file, "profile-images");
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public StoredFileResource loadAsResource(
            String storedPath,
            String contentType,
            String originalFilename
    ) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedPath)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);

            Resource resource = new ByteArrayResource(objectBytes.asByteArray()) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            };

            return new StoredFileResource(resource, contentType, originalFilename);
        } catch (NoSuchKeyException e) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }


    @Override
    public StoredFileInfo storeKnowledgeDocument(MultipartFile file) {
        try {
            return store(file, "knowledge-documents");
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public java.io.InputStream loadAsStream(String storedPath) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedPath)
                    .build();
            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public void delete(String storedPath) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedPath)
                    .build());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    private StoredFileInfo store(MultipartFile file, String directory) {
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown";

        String extension = extractExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + extension;
        String key = directory + "/" + storedFilename;

        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        ContentStreamProvider streamProvider = () -> {
            try {
                return file.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };

        RequestBody requestBody = RequestBody.fromContentProvider(
                streamProvider,
                file.getSize(),
                contentType
        );

        s3Client.putObject(putObjectRequest, requestBody);

        return new StoredFileInfo(
                key,
                originalFilename,
                contentType
        );
    }

    private static final long MAX_LICENSE_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "webp");

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
        }

        if (file.getSize() > MAX_LICENSE_IMAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
        }

        String originalFilename = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename();

        String ext = extractExtension(originalFilename);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if(!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
        }

        try (InputStream is = file.getInputStream()) {
            if (ImageIO.read(is) == null) {
                throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
        }
    }

    private String extractExtension(String originalFilename) {
        int index = originalFilename.lastIndexOf(".");
        if(index < 0 || index == originalFilename.length() -1) {
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
        String ext = extractExtension(originalFilename);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (!ALLOWED_POST_ATTACHMENT_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT))) {
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

}
