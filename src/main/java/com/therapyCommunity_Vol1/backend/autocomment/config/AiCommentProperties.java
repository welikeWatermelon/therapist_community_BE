package com.therapyCommunity_Vol1.backend.autocomment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai-comment")
public class AiCommentProperties {

    private boolean enabled = false;
    // 채팅 생성 경로: gemini-api(기존 REST) | vertex(Spring AI ChatClient)
    private String chatProvider = "gemini-api";
    private String apiKey;
    private String chatModel = "gemini-2.5-flash";
    private String embeddingModel = "gemini-embedding-001";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private int timeoutSeconds = 10;
    private String aiUserEmail = "ai-comment@system.local";
    private Retrieval retrieval = new Retrieval();

    @Getter
    @Setter
    public static class Retrieval {
        private int topK = 5;
        private double minScore = 0.3;
    }
}
