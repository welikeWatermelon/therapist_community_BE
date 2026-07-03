package com.therapyCommunity_Vol1.backend.autocomment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiCommentChatClientWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AiCommentProperties.class)
            .withBean(ObjectMapper.class)
            .withBean(ChatClient.Builder.class, () -> ChatClient.builder(mock(ChatModel.class)))
            .withUserConfiguration(WiringConfig.class);

    @Configuration
    @Import({GeminiChatClient.class, SpringAiCommentChatClient.class})
    static class WiringConfig {}

    @Test
    void 기본값은_gemini_api_경로만_활성화() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(AiCommentChatClient.class);
            assertThat(ctx).hasSingleBean(GeminiChatClient.class);
            assertThat(ctx).doesNotHaveBean(SpringAiCommentChatClient.class);
        });
    }

    @Test
    void vertex_설정_시_Spring_AI_경로만_활성화() {
        runner.withPropertyValues("app.ai-comment.chat-provider=vertex")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AiCommentChatClient.class);
                    assertThat(ctx).hasSingleBean(SpringAiCommentChatClient.class);
                    assertThat(ctx).doesNotHaveBean(GeminiChatClient.class);
                });
    }
}
