package com.therapyCommunity_Vol1.backend.knowledge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {

    private boolean enabled = false;
    private String apiKey;
    private String embeddingModel = "text-embedding-004";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private int timeoutSeconds = 10;
}
