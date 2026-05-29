package com.therapyCommunity_Vol1.backend.post.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmbeddingEvent {

    private final Long postId;
    private final String text;
}
