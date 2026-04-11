package com.therapyCommunity_Vol1.backend.post.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostCreatedEvent {
    private final Long postId;
    private final Long authorId;
    private final boolean requestAutoComment;
}
