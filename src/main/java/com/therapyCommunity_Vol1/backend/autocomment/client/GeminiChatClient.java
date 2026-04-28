package com.therapyCommunity_Vol1.backend.autocomment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiChatClient {

    private final AiCommentProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiChatClient(AiCommentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(timeout)
                                .withReadTimeout(timeout)))
                .build();
    }

    public record ChatResponse(String comment, List<Ground> grounds) {
        public record Ground(Long documentId, String title) {}
    }

    public ChatResponse generate(String systemPrompt, String userPrompt) {
        String url = String.format("/v1beta/models/%s:generateContent", properties.getChatModel());

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))
                ),
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.7
                )
        );

        String response = restClient.post()
                .uri(url)
                .header("x-goog-api-key", properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            log.info("Gemini raw response text: {}", text.substring(0, Math.min(200, text.length())));

            // Gemini가 grounds를 다양한 형식으로 반환할 수 있으므로 유연하게 파싱
            JsonNode parsed = objectMapper.readTree(text);
            String comment = parsed.path("comment").asText(null);

            List<ChatResponse.Ground> grounds = List.of();
            try {
                JsonNode groundsNode = parsed.path("grounds");
                if (groundsNode.isArray() && !groundsNode.isEmpty() && groundsNode.get(0).isObject()) {
                    grounds = objectMapper.convertValue(groundsNode,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ChatResponse.Ground.class));
                }
            } catch (Exception ignored) {
                // grounds 파싱 실패해도 comment만 있으면 OK
            }

            return new ChatResponse(comment, grounds);
        } catch (Exception e) {
            throw new RuntimeException("Gemini chat response parsing failed", e);
        }
    }
}
