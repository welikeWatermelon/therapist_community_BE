package com.therapyCommunity_Vol1.backend.fcm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.fcm")
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true", matchIfMissing = false)
public class FcmConfig {
    private boolean enabled = false;
    private String credentialsPath;
    private String projectId;
}
