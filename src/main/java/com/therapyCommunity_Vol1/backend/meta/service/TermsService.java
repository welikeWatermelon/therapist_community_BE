package com.therapyCommunity_Vol1.backend.meta.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
public class TermsService {

    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);
    private static final String TERMS_KEY = "terms/service-v1.0.html";
    private static final String PRIVACY_KEY = "terms/privacy-v1.0.html";

    private final S3Presigner s3Presigner;
    private final String bucket;

    public TermsService(
            @Value("${app.aws.s3.bucket:}") String bucket,
            @Value("${app.aws.region:ap-northeast-2}") String region,
            @Value("${app.aws.enabled:false}") boolean awsEnabled
    ) {
        this.bucket = bucket;
        if (awsEnabled) {
            this.s3Presigner = S3Presigner.builder()
                    .region(software.amazon.awssdk.regions.Region.of(region))
                    .build();
        } else {
            this.s3Presigner = null;
        }
    }

    public String getTermsUrl() {
        return presign(TERMS_KEY);
    }

    public String getPrivacyUrl() {
        return presign(PRIVACY_KEY);
    }

    private String presign(String key) {
        if (s3Presigner == null) {
            return "https://" + bucket + ".s3.amazonaws.com/" + key;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_DURATION)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
