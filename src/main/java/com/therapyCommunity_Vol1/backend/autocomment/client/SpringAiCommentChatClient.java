package com.therapyCommunity_Vol1.backend.autocomment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI ChatClient 기반 자동답글 생성 어댑터 (Vertex AI Gemini).
 * app.ai-comment.chat-provider=vertex + spring.ai.model.chat=vertex-ai-gemini일 때 활성화 (MEL-74).
 * 구조화 출력은 .entity()가 JSON 스키마 format 지시문을 프롬프트에 첨부해 처리.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai-comment", name = "chat-provider", havingValue = "vertex")
public class SpringAiCommentChatClient implements AiCommentChatClient {

    private final ChatClient chatClient;

    public SpringAiCommentChatClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public ChatResult generate(String systemPrompt, String userPrompt) {
        ChatResult result = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(ChatResult.class);

        if (result == null) {
            throw new NonTransientAiException("LLM returned no parseable response");
        }
        return result.grounds() == null
                ? new ChatResult(result.comment(), List.of())
                : result;
    }
}
