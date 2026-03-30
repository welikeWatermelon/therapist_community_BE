package com.therapyCommunity_Vol1.backend.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@ConditionalOnProperty(
    name = "app.aws.enabled",
    havingValue = "true",
    matchIfMissing = false
)

@Configuration
@Profile("!local")
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${app.aws.region}") String region
    ) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
