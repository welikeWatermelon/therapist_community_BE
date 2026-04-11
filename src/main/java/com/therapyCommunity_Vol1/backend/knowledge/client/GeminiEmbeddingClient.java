package com.therapyCommunity_Vol1.backend.knowledge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.knowledge.config.KnowledgeProperties;
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
public class GeminiEmbeddingClient {

    private final KnowledgeProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiEmbeddingClient(KnowledgeProperties properties, ObjectMapper objectMapper) {
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

    public float[] embed(String text) {
        return embed(text, properties.getApiKey(), properties.getEmbeddingModel(), properties.getBaseUrl());
    }

    public float[] embed(String text, String apiKey, String model, String baseUrl) {
        String fullUrl = String.format(
                "%s/v1beta/models/%s:embedContent?key=%s",
                baseUrl, model, apiKey
        );

        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        RestClient client = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(timeout)
                                .withReadTimeout(timeout)))
                .build();

        String response = client.post()
                .uri(fullUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode values = root.path("embedding").path("values");
            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = (float) values.get(i).asDouble();
            }
            return embedding;
        } catch (Exception e) {
            throw new RuntimeException("Embedding response parsing failed", e);
        }
    }
}
