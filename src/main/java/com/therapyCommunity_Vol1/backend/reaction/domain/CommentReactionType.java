package com.therapyCommunity_Vol1.backend.reaction.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommentReactionType {

    LIKE("좋아요"),
    DISLIKE("싫어요");

    private final String label;
}
