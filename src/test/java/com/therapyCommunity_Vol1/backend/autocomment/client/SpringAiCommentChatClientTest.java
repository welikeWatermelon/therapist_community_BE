package com.therapyCommunity_Vol1.backend.autocomment.client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiCommentChatClientTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiCommentChatClient client =
            new SpringAiCommentChatClient(ChatClient.builder(chatModel));

    private void stubResponse(String json) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    @Test
    void JSON_응답을_ChatResult로_매핑() {
        stubResponse("""
                {"comment":"좋은 질문입니다.","grounds":[{"documentId":1,"title":"언어치료 가이드"}]}
                """);

        AiCommentChatClient.ChatResult result = client.generate("시스템", "유저");

        assertThat(result.comment()).isEqualTo("좋은 질문입니다.");
        assertThat(result.grounds()).hasSize(1);
        assertThat(result.grounds().get(0).documentId()).isEqualTo(1L);
        assertThat(result.grounds().get(0).title()).isEqualTo("언어치료 가이드");
    }

    @Test
    void grounds_없으면_빈_리스트로_정규화() {
        stubResponse("{\"comment\":\"응원합니다.\"}");

        AiCommentChatClient.ChatResult result = client.generate("시스템", "유저");

        assertThat(result.comment()).isEqualTo("응원합니다.");
        assertThat(result.grounds()).isEmpty();
    }

    @Test
    void 시스템_유저_프롬프트가_그대로_전달() {
        stubResponse("{\"comment\":\"ok\",\"grounds\":[]}");

        client.generate("시스템 프롬프트", "유저 프롬프트");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String rendered = captor.getValue().getContents();
        assertThat(rendered).contains("시스템 프롬프트").contains("유저 프롬프트");
    }
}
