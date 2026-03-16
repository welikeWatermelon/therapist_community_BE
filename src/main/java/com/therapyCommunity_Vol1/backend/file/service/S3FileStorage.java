package com.therapyCommunity_Vol1.backend.file.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.storage.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileResource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
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

@Primary
@Profile("!local")
@Service
@RequiredArgsConstructor
public class S3FileStorage implements FileStorageService {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket}")
    private String bucket;

    @Override
    public StoredFileInfo storeTherapistVerificationImage(MultipartFile file) {
        try {
            validateImage(file);

            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "unknown";

            String extension = extractExtension(originalFilename);
            String storedFilename = UUID.randomUUID() + extension;
            String key = "therapist-verifications/" + storedFilename;

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
        } catch (Exception e) {
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
    public void delete(String storedPath) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedPath)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("파일 삭제 실패", e);
        }
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

}
