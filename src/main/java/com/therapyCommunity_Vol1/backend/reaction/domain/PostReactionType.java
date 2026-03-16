package com.therapyCommunity_Vol1.backend.reaction.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostReactionType {
    EMPATHY("공감"),
    APPRECIATE("잘 봤어요"),
    HELPFUL("유익");

    private final String label;
}
