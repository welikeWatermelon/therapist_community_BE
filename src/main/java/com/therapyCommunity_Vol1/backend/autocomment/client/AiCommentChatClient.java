package com.therapyCommunity_Vol1.backend.autocomment.client;

import java.util.List;

/**
 * 자동답글 생성 채팅 클라이언트 seam.
 * 구현체는 app.ai-comment.chat-provider로 택일: gemini-api(기존 REST) | vertex(Spring AI ChatClient).
 */
public interface AiCommentChatClient {

    ChatResult generate(String systemPrompt, String userPrompt);

    record ChatResult(String comment, List<Ground> grounds) {
        public record Ground(Long documentId, String title) {}
    }
}
